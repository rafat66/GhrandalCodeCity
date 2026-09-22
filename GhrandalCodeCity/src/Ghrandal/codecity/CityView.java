package Ghrandal.codecity;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.basic.BasicMenuBarUI;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

class CityView extends JPanel implements MouseListener, MouseMotionListener, MouseWheelListener, KeyListener {
    private static final long serialVersionUID = 1L;
    static final double MIN_ZOOM = 0.03;
    static final double MAX_ZOOM = 8.0;
    static final int CLICK_DRAG_THRESHOLD = 5;

    static final Color STATIC_METHOD_COLOR  = new Color(35, 160, 70);
    static final Color LOCAL_VARIABLE_COLOR = new Color(128, 0, 128);
    static final Color ATTRIBUTE_FLOOR_COLOR = new Color(135, 206, 250);
    static final Color ATTRIBUTE_MARKER_COLOR = Color.BLACK;
    static final Color STATIC_ATTRIBUTE_MARKER_COLOR = new Color(200, 0, 0);

    static final Color RELATION_LINE_COLOR = new Color(200, 25, 25);
    static final Color TRIANGLE_BLUE   = new Color(30, 90, 200);
    static final Color TRIANGLE_BLACK  = new Color(20, 20, 20);
    static final Color TRIANGLE_GREEN  = new Color(35, 160, 70);
    static final Color TRIANGLE_ORANGE = new Color(245, 135, 25);

    static final Color FEATURE_RED = new Color(200, 25, 25);
    static final Color FEATURE_ORANGE = new Color(245, 135, 25);

    static final Color FLOOR_FILL = Color.WHITE;
    static final Color FLOOR_TOP_EDGE = new Color(180, 180, 180);
    static final double FLOOR_BAND_H = 0.09;

    static final double ATTR_FLOOR_BAND_H = 0.55;
    static final double BASE_PLINTH_H = 0.15;
    static final double METHOD_FLOOR_SPACING = 0.62;

    static final double HEADER_BAND = 0.55;

    // Root district uses the exact user-selected city background color.
    // This removes the separate brown root-district surface so the root area
    // visually matches the selected background.
    static final Color ROOT_DISTRICT_COLOR = AppSettings.cityBackground();

    static final Color COMPLEXITY_SIMPLE  = new Color(20, 40, 120);
    static final Color COMPLEXITY_MEDIUM  = new Color(50, 110, 200);
    static final Color COMPLEXITY_COMPLEX = new Color(150, 190, 245);

    // ---- Attribute markers on the attribute floor (screen-space pixels) ----
    static final int ATTR_MARKER_W_PX = 3;
    static final int ATTR_MARKER_H_PX = 12;

    // ---- World-space sizes for ceiling symbols (in world units) ----
    // Fix C: larger documented-circle radius so it reads from far zoom
    static final double CEILING_RADIUS_WORLD   = 0.13;
    // Fix B: larger superclass tower so it is clearly visible
    static final double CEILING_TOWER_H_WORLD  = 0.30;
    static final double CEILING_TOWER_W_WORLD  = 0.24;

    // ---- Ceiling quadrant positions (normalized in [0,1] of the base side) ----
    static final double FAN_QUAD_X = 0.25, FAN_QUAD_Y = 0.25;   // top-left
    static final double DOC_QUAD_X = 0.75, DOC_QUAD_Y = 0.25;   // top-right
    static final double TWR_QUAD_X = 0.75, TWR_QUAD_Y = 0.75;   // bottom-right

    Model model;
    int relationMask = AppConstants.REL_NONE;
    double zoom = 1.0;
    double yaw = -0.70;
    double pitch = 0.55;
    double panX = 0;
    double panY = 0;
    Point last;
    Point pressPoint;
    boolean panning;
    Ref hovered;
    List<Hit> hits = Collections.emptyList();
    Consumer<Ref> onPick;

    boolean showPackages      = true;
    boolean showClasses       = true;
    boolean showAttributes    = true;
    boolean showMethods       = true;
    boolean showLocalVars     = true;
    boolean showStaticMethods = true;
    boolean showStreetGrid   = false;

    boolean labelPackage    = false;
    boolean labelClass      = false;
    boolean labelAttribute  = false;
    boolean labelMethod     = false;
    boolean labelLocal      = false;
    boolean labelRelations  = false;

    CityView() {
        setOpaque(true);
        setBackground(AppSettings.cityBackground());
        addMouseListener(this);
        addMouseMotionListener(this);
        addMouseWheelListener(this);
        addKeyListener(this);
        setFocusable(true);
        ToolTipManager.sharedInstance().registerComponent(this);
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        Ref r = hit(event.getPoint());
        if (r == null) return null;
        return tooltipFor(r);
    }

    private String tooltipFor(Ref r) {
        if (r instanceof DistrictRef dr) {
            District d = dr.d();
            String label = (d.level == 0 || d.name.isEmpty())
                    ? (model != null && model.name != null && !model.name.isBlank() ? model.name : "(root city)")
                    : d.name;
            return "<html><b>Package:</b> " + label
                    + "<br><b>Path:</b> " + (d.path.isEmpty() ? "(root)" : d.path)
                    + "<br><b>Classes:</b> " + d.classCount() + "</html>";
        }
        if (r instanceof ClassRef cr) {
            ClassInfo c = cr.c();
            return "<html><b>Class:</b> " + c.name
                    + "<br><b>Package:</b> " + (c.packageName == null || c.packageName.isBlank() ? "(default)" : c.packageName)
                    + "<br><b>LOC:</b> " + c.loc
                    + "<br><b>Methods:</b> " + c.methods.size()
                    + "<br><b>Attributes:</b> " + c.attributes.size()
                    + "<br><b>NOC:</b> " + c.noc + "</html>";
        }
        if (r instanceof MethodRef mr) {
            return "<html><b>Method:</b> " + mr.m().name + "()<br><b>Class:</b> " + mr.c().name
                    + "<br><b>Return:</b> " + mr.m().returnType + "</html>";
        }
        if (r instanceof AttributeRef ar) {
            return "<html><b>Attribute:</b> " + ar.a().name + "<br><b>Type:</b> " + ar.a().type
                    + "<br><b>Class:</b> " + ar.c().name + "</html>";
        }
        if (r instanceof LocalVariableRef lr) {
            String name = (lr.index() >= 0 && lr.index() < lr.method().locals.size())
                    ? lr.method().locals.get(lr.index()) : "?";
            String type = (lr.index() >= 0 && lr.index() < lr.method().localsTypes.size())
                    ? lr.method().localsTypes.get(lr.index()) : "?";
            return "<html><b>Local variable:</b> " + name + "<br><b>Type:</b> " + type
                    + "<br><b>Method:</b> " + lr.method().name + "()</html>";
        }
        if (r instanceof RelationRef rr) {
            return "<html><b>Relation:</b> " + rr.kind() + "<br>" + rr.source() + " → " + rr.target() + "</html>";
        }
        return null;
    }

    void setModel(Model m) { this.model = m; if (m != null) Layout.layout(m); repaint(); }
    void resetCamera() { zoom = 1.0; yaw = -0.70; pitch = 0.55; panX = 0; panY = 0; }

    void fitCity() {
        if (model == null) return;
        Layout.layout(model);
        if (model.width <= 0 || model.height <= 0) return;
        double usable = Math.min(getWidth() - 80.0, getHeight() - 100.0);
        double city = Math.max(model.width, model.height) * 42.0;
        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, usable / Math.max(1, city)));
        panX = 0; panY = 0;
    }

    BufferedImage renderImage() {
        BufferedImage image = new BufferedImage(Math.max(1, getWidth()), Math.max(1, getHeight()), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        paint(g);
        g.dispose();
        return image;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        // Paint the user-selected background as a solid, exact RGB color.
        // Do not apply gradients/brightness transforms: those made a selected
        // color (especially white) appear gray in parts of the city view.
        Color bgBase = AppSettings.cityBackground();
        g.setPaint(bgBase);
        g.fillRect(0, 0, getWidth(), getHeight());

        if (model == null) {
            int cx = getWidth() / 2;
            int cy = getHeight() / 2 - 95;

            g.setColor(new Color(0xE8EBEF));
            g.setFont(new Font("SansSerif", Font.BOLD, 20));
            String title = "Open a Code-Metamodel XML file";
            FontMetrics fm = g.getFontMetrics();
            g.drawString(title, cx - fm.stringWidth(title) / 2, cy);

            g.setColor(new Color(0x9AA3B0));
            g.setFont(new Font("SansSerif", Font.PLAIN, 13));
            String subtitle = "Use File → Open XML… to start visualizing your software city";
            fm = g.getFontMetrics();
            g.drawString(subtitle, cx - fm.stringWidth(subtitle) / 2, cy + 25);

            // XML document icon directly under the opening instruction.
            int ix = cx - 34, iy = cy + 55, iw = 68, ih = 78;
            g.setColor(new Color(0x20252E));
            g.fillRoundRect(ix, iy, iw, ih, 10, 10);
            g.setColor(new Color(0x3D8BFD));
            g.setStroke(new BasicStroke(2.2f));
            g.drawRoundRect(ix, iy, iw, ih, 10, 10);
            g.setColor(new Color(0x5CA0FF));
            Path2D fold = new Path2D.Double();
            fold.moveTo(ix + iw - 20, iy + 2);
            fold.lineTo(ix + iw - 2, iy + 20);
            fold.lineTo(ix + iw - 20, iy + 20);
            fold.closePath();
            g.fill(fold);
            g.setColor(new Color(0xE8EBEF));
            g.setFont(new Font("SansSerif", Font.BOLD, 15));
            String xml = "XML";
            fm = g.getFontMetrics();
            g.drawString(xml, cx - fm.stringWidth(xml) / 2, iy + 48);
            g.setColor(new Color(0x9AA3B0));
            g.setFont(new Font("SansSerif", Font.PLAIN, 10));
            String hint = "Code-Metamodel";
            fm = g.getFontMetrics();
            g.drawString(hint, cx - fm.stringWidth(hint) / 2, iy + 65);

            // Software city logo below the XML file icon.
            SoftwareCityLogo.paint(g, cx, iy + ih + 20, 0.72);

            g.dispose();
            return;
        }

        Transform t = new Transform(getWidth() / 2.0 + panX, getHeight() * 0.60 + panY, zoom, yaw, pitch);
        List<Hit> newHits = new ArrayList<>();

        drawGround(g, t);
        if (showStreetGrid) drawStreetGrid(g, t);
        if (showPackages) for (District d : model.roots) drawDistrict(g, t, d, newHits);
        if (showClasses)  drawAllBuildings(g, t, newHits);
        if (relationMask != AppConstants.REL_NONE) drawRelations(g, t, newHits);
        for (Hit h : newHits) if (h.label != null) h.label.accept(g);

        hits = newHits;
        g.dispose();
    }

    private void drawGround(Graphics2D g, Transform t) {
        Point2D[] p = { t.project(-1, -1, -0.02), t.project(model.width + 1, -1, -0.02),
                t.project(model.width + 1, model.height + 1, -0.02), t.project(-1, model.height + 1, -0.02) };

        // Keep the area immediately around/under the root city visually neutral.
        // It is not a software package or an additional district, so it must not
        // introduce the gray floor/shadow that can look like an extra district.
        // The actual root district remains the light-brown software-city surface.
        Color background = AppSettings.cityBackground();
        polygon(g, p, background, null);
    }

    private void drawStreetGrid(Graphics2D g, Transform t) {
        if (model == null) return;
        double maxX = model.width + 1.0;
        double maxY = model.height + 1.0;
        double spacing = 2.0;
        Stroke old = g.getStroke();
        g.setStroke(new BasicStroke(0.8f));
        g.setColor(new Color(0x4A525D));
        for (double x = -1.0; x <= maxX; x += spacing) {
            Point2D a = t.project(x, -1.0, -0.005);
            Point2D b = t.project(x, maxY, -0.005);
            g.draw(new Line2D.Double(a, b));
        }
        for (double y = -1.0; y <= maxY; y += spacing) {
            Point2D a = t.project(-1.0, y, -0.005);
            Point2D b = t.project(maxX, y, -0.005);
            g.draw(new Line2D.Double(a, b));
        }
        g.setStroke(old);
    }

    // ============================================================
    // DISTRICT
    // ============================================================
    private void drawDistrict(Graphics2D g, Transform t, District d, List<Hit> out) {
        boolean isRootContainer = (d.parent == null && d.level == 0 && (d.name == null || d.name.isEmpty()));
        Color base = isRootContainer ? ROOT_CITY_COLOR : districtBaseColor(d.level);

        Point2D[] floor = {
            t.project(d.x,           d.y,           0),
            t.project(d.x + d.width, d.y,           0),
            t.project(d.x + d.width, d.y + d.height, 0),
            t.project(d.x,           d.y + d.height, 0)
        };
        // Structural representation: the synthetic root is the entire
        // software city and is drawn first as a light-brown district. Every
        // real package is then drawn as its own gray district on top of the
        // root (or on top of its parent package). This is important: a direct
        // package must have a visible floor, otherwise it visually disappears
        // into the root and package nesting cannot be recognized.
        //
        // All package levels therefore receive their own filled district
        // surface. Nested packages naturally appear inside their parent's
        // rectangle because Layout places children inside the parent bounds.
        Object oldAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        polygon(g, floor, base, null);
        if (oldAA != null) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAA);
        } else {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        }

        // A subtle boundary makes each package district unambiguous without
        // adding another visual element that could be mistaken for a package.
        if (!isRootContainer) {
            Stroke oldStroke = g.getStroke();
            g.setColor(darker(base, 0.72));
            g.setStroke(new BasicStroke(1.15f));
            polygon(g, floor, null, g.getColor());
            g.setStroke(oldStroke);
        }

        if (labelPackage && !isRootContainer) {
            Color headerFill = darker(base, 0.92);
            Point2D[] header = {
                t.project(d.x,           d.y,               0.01),
                t.project(d.x + d.width, d.y,               0.01),
                t.project(d.x + d.width, d.y + HEADER_BAND, 0.01),
                t.project(d.x,           d.y + HEADER_BAND, 0.01)
            };
            polygon(g, header, headerFill, null);

            Point2D lp = t.project(d.x + 0.20, d.y + HEADER_BAND * 0.68, 0.02);
            Color textColor = (base.getRed() + base.getGreen() + base.getBlue()) < 400
                    ? Color.WHITE : new Color(0x3A3A3A);
            g.setColor(textColor);
            g.setFont(new Font("SansSerif", Font.BOLD, 12));
            g.drawString(d.name, (int) lp.getX(), (int) lp.getY());
        }

        final String displayName = isRootContainer
                ? (model != null && model.name != null && !model.name.isBlank() ? model.name : "(root city)")
                : d.name;
        String districtId = "D:" + d.path;
        final Point2D floorPt0 = floor[0];
        final boolean showHoverLabel = !labelPackage;
        out.add(new Hit(shape(floor), new DistrictRef(d), gg -> {
            if (showHoverLabel && isHovered(districtId))
                drawLabel(gg, displayName, (int) floorPt0.getX() + 40, (int) floorPt0.getY() - 8);
        }));

        // The root District represents the entire software city.
        // Real package Districts must remain visible inside the root, and
        // nested packages are rendered recursively with their level-based
        // gray shades. The root itself is never treated as a package.
        for (District child : d.children) {
            drawDistrict(g, t, child, out);
        }
    }

    private static final Color ROOT_CITY_COLOR = new Color(0xD8C09A);

    /**
     * Gray palette for real package districts.
     *
     * Level 1 is deliberately darker and each deeper nesting level becomes
     * visibly lighter. This makes Package -> Nested Package -> Deeper Package
     * immediately distinguishable while keeping the representation neutral
     * and consistent with the software-city notation.
     */
    private static Color districtBaseColor(int level) {
        final int[] grayLevels = {
            180,  // package level 1 - lighter gray
            205,  // package level 2 - very light gray
            220,  // package level 3 - very light gray
            230,  // package level 4 - very light gray
            238,  // package level 5 - very light gray
            245,  // package level 6+ - very light gray
            245,  // package level 7+ - very light gray
            245   // package level 8+ - very light gray
        };

        int index = Math.max(0, Math.min(level - 1, grayLevels.length - 1));
        int gray = grayLevels[index];
        return new Color(gray, gray, gray);
    }

    private void drawAllBuildings(Graphics2D g, Transform t, List<Hit> out) {
        double vx = -Math.sin(yaw);
        double vy = -Math.cos(yaw);
        List<ClassInfo> all = new ArrayList<>(model.classes);
        all.sort((a, b) -> Double.compare(depthOf(b, vx, vy), depthOf(a, vx, vy)));
        for (ClassInfo c : all) drawBuilding(g, t, c, out);
    }

    private double depthOf(ClassInfo c, double vx, double vy) {
        double cx = c.x + c.baseSide() / 2.0;
        double cy = c.y + c.baseSide() / 2.0;
        return cx * vx + cy * vy;
    }

    // ============================================================
    // BUILDING
    // ============================================================
    private void drawBuilding(Graphics2D g, Transform t, ClassInfo c, List<Hit> out) {
        double side = c.baseSide();
        double height = c.buildingHeight();
        Color building = model.colorFor(c);

        Point2D[] rim = { t.project(c.x - 0.10, c.y - 0.10, 0.001),
                t.project(c.x + side + 0.10, c.y - 0.10, 0.001),
                t.project(c.x + side + 0.10, c.y + side + 0.10, 0.001),
                t.project(c.x - 0.10, c.y + side + 0.10, 0.001) };
        polygon(g, rim, new Color(230, 235, 240), new Color(210, 218, 228));

        Color frontColor = building;
        Color sideColor  = building.darker();
        Color backColor  = darker(building, 0.55);
        int lum = building.getRed() + building.getGreen() + building.getBlue();
        Color wallOutline = (lum < 330) ? new Color(255, 255, 255, 200) : new Color(120, 120, 120, 180);

        Point2D[] base = {
            t.project(c.x,        c.y,        0),
            t.project(c.x + side, c.y,        0),
            t.project(c.x + side, c.y + side, 0),
            t.project(c.x,        c.y + side, 0)
        };
        Point2D[] top = {
            t.project(c.x,        c.y,        height),
            t.project(c.x + side, c.y,        height),
            t.project(c.x + side, c.y + side, height),
            t.project(c.x,        c.y + side, height)
        };

        Point2D[] southWall = { base[0], base[1], top[1], top[0] };
        Point2D[] eastWall  = { base[1], base[2], top[2], top[1] };
        Point2D[] northWall = { base[2], base[3], top[3], top[2] };
        Point2D[] westWall  = { base[3], base[0], top[0], top[3] };

        double camDirX = -Math.sin(t.yaw);
        double camDirY = -Math.cos(t.yaw);
        boolean southVisible = camDirY > 0;
        boolean northVisible = camDirY < 0;
        boolean westVisible  = camDirX > 0;
        boolean eastVisible  = camDirX < 0;

        int methodCount = showMethods ? c.methods.size() : 0;
        boolean hasAttr = !c.attributes.isEmpty();

        if (!southVisible) fillOnly(g, southWall, backColor);
        if (!northVisible) fillOnly(g, northWall, backColor);
        if (!eastVisible)  fillOnly(g, eastWall,  backColor);
        if (!westVisible)  fillOnly(g, westWall,  backColor);

        if (showAttributes && hasAttr) {
            double attrTop = attrFloorTopZ(true);
            double attrBot = BASE_PLINTH_H;
            if (!southVisible) drawAttributeBand(g, t, c, attrBot, attrTop, 0, 1);
            if (!northVisible) drawAttributeBand(g, t, c, attrBot, attrTop, 2, 3);
            if (!eastVisible)  drawAttributeBand(g, t, c, attrBot, attrTop, 1, 2);
            if (!westVisible)  drawAttributeBand(g, t, c, attrBot, attrTop, 3, 0);
        }
        for (int i = 0; i < methodCount; i++) {
            double floorZ = methodFloorTopZ(hasAttr, i);
            if (!southVisible) drawWallBand(g, t, c, floorZ, side, 0, 1);
            if (!northVisible) drawWallBand(g, t, c, floorZ, side, 2, 3);
            if (!eastVisible)  drawWallBand(g, t, c, floorZ, side, 1, 2);
            if (!westVisible)  drawWallBand(g, t, c, floorZ, side, 3, 0);
        }

        if (westVisible)  polygonSoft(g, westWall,  sideColor,  wallOutline);
        if (eastVisible)  polygonSoft(g, eastWall,  sideColor,  wallOutline);
        if (northVisible) polygonSoft(g, northWall, frontColor, wallOutline);
        if (southVisible) polygonSoft(g, southWall, frontColor, wallOutline);

        if (showAttributes && hasAttr) {
            double attrTop = attrFloorTopZ(true);
            double attrBot = BASE_PLINTH_H;
            if (southVisible) drawAttributeBand(g, t, c, attrBot, attrTop, 0, 1);
            if (northVisible) drawAttributeBand(g, t, c, attrBot, attrTop, 2, 3);
            if (eastVisible)  drawAttributeBand(g, t, c, attrBot, attrTop, 1, 2);
            if (westVisible)  drawAttributeBand(g, t, c, attrBot, attrTop, 3, 0);
        }
        for (int i = 0; i < methodCount; i++) {
            double floorZ = methodFloorTopZ(hasAttr, i);
            if (southVisible) drawWallBand(g, t, c, floorZ, side, 0, 1);
            if (northVisible) drawWallBand(g, t, c, floorZ, side, 2, 3);
            if (eastVisible)  drawWallBand(g, t, c, floorZ, side, 1, 2);
            if (westVisible)  drawWallBand(g, t, c, floorZ, side, 3, 0);
        }

        double dotSouth =  camDirY, dotNorth = -camDirY, dotWest =  camDirX, dotEast = -camDirX;
        double bestDot = Math.max(Math.max(dotSouth, dotNorth), Math.max(dotWest, dotEast));
        int wallA, wallB;
        if (bestDot == dotSouth)      { wallA = 0; wallB = 1; }
        else if (bestDot == dotNorth) { wallA = 2; wallB = 3; }
        else if (bestDot == dotWest)  { wallA = 3; wallB = 0; }
        else                          { wallA = 1; wallB = 2; }

        if (showMethods) {
            for (int i = 0; i < c.methods.size(); i++) {
                MethodInfo method = c.methods.get(i);
                double floorBottom = methodFloorBottomZ(hasAttr, i);
                double floorTop    = methodFloorTopZ(hasAttr, i);
                Point2D p = wallPositionPoint(t, c, wallA, wallB, floorBottom, floorTop, 0.5);

                // --------------------------------------------------------
                // SYMBOLS ON THIS FLOOR
                // --------------------------------------------------------
                double wallLenWorld = wallLenSafe(c, wallA, wallB);
                int n = method.locals.size();
                int staticSlot = (showStaticMethods && method.isStatic) ? 1 : 0;
                int totalSymbols = n + staticSlot;

                // Initial world radius: base size clamps to building side and wall length.
                double worldRadius = Math.max(0.10, Math.min(side * 0.28, wallLenWorld * 0.14));

                int totalSlots = Math.max(1, totalSymbols);
                double[] slotT = new double[totalSlots];
                if (totalSlots == 1) {
                    slotT[0] = 0.5;
                } else {
                    // FIX A: reserve 25% margins and require a 40% inter-symbol gap
                    // so circles never touch even on crowded floors.
                    double margin = worldRadius * 1.6;
                    double usable = Math.max(worldRadius, wallLenWorld - 2 * margin);

                    double neededPerSymbol = 2.0 * worldRadius * 1.40;   // 40% gap
                    double totalNeeded = totalSlots * neededPerSymbol;

                    if (totalNeeded > usable) {
                        worldRadius = usable / (totalSlots * 2.0 * 1.40);
                        // FIX A: minimum radius lowered to 0.03
                        worldRadius = Math.max(0.03, worldRadius);
                    }

                    double stepT = usable / (totalSlots - 1) / wallLenWorld;
                    double startT = margin / wallLenWorld;
                    for (int k = 0; k < totalSlots; k++) slotT[k] = startT + k * stepT;
                }

                int nextSlot = 0;

                // ---- Static method (first slot) ----
                if (staticSlot == 1) {
                    Point2D sp = wallPositionPoint(t, c, wallA, wallB, floorBottom, floorTop, slotT[nextSlot]);
                    double radiusPx = estimateScreenRadius(t, c, wallA, wallB, floorBottom, floorTop, slotT[nextSlot], worldRadius);
                    drawCircle(g, sp, radiusPx, STATIC_METHOD_COLOR);
                    out.add(new Hit(new Ellipse2D.Double(sp.getX() - radiusPx, sp.getY() - radiusPx, radiusPx*2, radiusPx*2),
                            new MethodRef(c, method), null));
                    nextSlot++;
                }

                // ---- Local variables (remaining slots) ----
                if (showLocalVars && n > 0) {
                    for (int k = 0; k < n; k++) {
                        int slot = nextSlot + k;
                        if (slot >= slotT.length) break;
                        Point2D lp = wallPositionPoint(t, c, wallA, wallB, floorBottom, floorTop, slotT[slot]);
                        double radiusPx = estimateScreenRadius(t, c, wallA, wallB, floorBottom, floorTop, slotT[slot], worldRadius);
                        drawCircle(g, lp, radiusPx, LOCAL_VARIABLE_COLOR);
                        String lid = "LV:" + c.fullName() + ":" + method.name + ":" + k;
                        final int index = k;
                        final MethodInfo mm = method;
                        out.add(new Hit(new Ellipse2D.Double(lp.getX() - radiusPx, lp.getY() - radiusPx, radiusPx*2, radiusPx*2),
                                new LocalVariableRef(c, method, index),
                                gg -> {
                                    if (labelLocal || isHovered(lid)) {
                                        String lv = (index >= 0 && index < mm.locals.size())
                                                ? mm.locals.get(index) : "?";
                                        drawLabel(gg, lv, (int) lp.getX(), (int) lp.getY() - 12);
                                    }
                                }));
                    }
                }

                Rectangle2D floorHit = new Rectangle2D.Double(p.getX() - 12, p.getY() - 10, 24, 20);
                String methodId = "M:" + c.fullName() + ":" + method.name;
                out.add(new Hit(floorHit, new MethodRef(c, method), gg -> {
                    if (labelMethod || isHovered(methodId))
                        drawLabel(gg, method.name, (int) p.getX(), (int) p.getY() - 14);
                }));
            }
        }

        if (showAttributes && hasAttr) {
            drawAttributeMarkers(g, t, c, southVisible, northVisible, eastVisible, westVisible, out);
        }

        polygonSoft(g, top, building.brighter(), new Color(255, 255, 255, 180));

        String classId = "C:" + c.fullName();
        final ClassInfo classRef = c;
        out.add(new Hit(shape(top), new ClassRef(c), gg -> {
            if (labelClass || isHovered(classId))
                drawLabel(gg, classRef.name, (int) top[0].getX(), (int) top[0].getY() - 16);
        }));

        // --------------------------------------------------------
        // CEILING FEATURES
        // --------------------------------------------------------
        // Interface fan     -> top-left quadrant    (0.25, 0.25)
        // Documented circle -> top-right quadrant   (0.75, 0.25)
        // Superclass tower  -> bottom-right quadrant (0.75, 0.75)

        if (c.isInterface) {
            Point2D fp = t.project(c.x + side * FAN_QUAD_X,
                                   c.y + side * FAN_QUAD_Y,
                                   height + 0.05);
            double radiusPx = estimateScreenRadiusAtPoint(t, side, height, fp, CEILING_RADIUS_WORLD * 0.7);
            drawInterfaceFan(g, fp, radiusPx);
            out.add(new Hit(new Ellipse2D.Double(fp.getX() - radiusPx, fp.getY() - radiusPx, radiusPx*2, radiusPx*2),
                    new ClassRef(c), null));
        }

        if (c.comments > 0) {
            Point2D center = t.project(c.x + side * DOC_QUAD_X,
                                       c.y + side * DOC_QUAD_Y,
                                       height + 0.05);
            double radiusPx = estimateScreenRadiusAtPoint(t, side, height, center, CEILING_RADIUS_WORLD);
            drawCircle(g, center, radiusPx, FEATURE_ORANGE);
            out.add(new Hit(new Ellipse2D.Double(center.getX() - radiusPx, center.getY() - radiusPx, radiusPx*2, radiusPx*2),
                    new ClassRef(c), null));
        }

        if (model.superClassNames.contains(c.fullName())) {
            Point2D sp = t.project(c.x + side * TWR_QUAD_X,
                                   c.y + side * TWR_QUAD_Y,
                                   height + 0.05);
            double baseW = estimateScreenRadiusAtPoint(t, side, height, sp, CEILING_TOWER_W_WORLD) * 2.0;
            double towerH = estimateScreenRadiusAtPoint(t, side, height, sp, CEILING_TOWER_H_WORLD) * 2.0;
            drawSuperclassTower(g, sp, baseW, towerH);
            out.add(new Hit(new Rectangle2D.Double(sp.getX() - baseW / 2.0, sp.getY() - towerH, baseW, towerH),
                    new ClassRef(c), null));
        }
    }

    private static int clamp255(int v) { return Math.max(0, Math.min(255, v)); }
    private static Color brighten(Color c, double factor) {
        return new Color(clamp255((int) (c.getRed() * factor)),
                clamp255((int) (c.getGreen() * factor)),
                clamp255((int) (c.getBlue() * factor)));
    }

    private static Color darken(Color c, double factor) {
        return new Color(clamp255((int) (c.getRed() * factor)),
                clamp255((int) (c.getGreen() * factor)),
                clamp255((int) (c.getBlue() * factor)));
    }

    private static Color darker(Color c, double factor) {
        return new Color(clamp255((int) (c.getRed() * factor)),
                clamp255((int) (c.getGreen() * factor)),
                clamp255((int) (c.getBlue() * factor)));
    }

    private static double attrFloorTopZ(boolean hasAttr) { return hasAttr ? (BASE_PLINTH_H + ATTR_FLOOR_BAND_H) : 0.0; }
    private static double methodFloorTopZ(boolean hasAttr, int i) {
        double base = hasAttr ? attrFloorTopZ(true) : BASE_PLINTH_H;
        return base + (i + 1) * METHOD_FLOOR_SPACING;
    }
    private static double methodFloorMidZ(boolean hasAttr, int i) {
        return methodFloorTopZ(hasAttr, i) - METHOD_FLOOR_SPACING / 2.0;
    }
    private static double methodFloorBottomZ(boolean hasAttr, int i) {
        double base = hasAttr ? attrFloorTopZ(true) : BASE_PLINTH_H;
        return base + i * METHOD_FLOOR_SPACING;
    }

    private Point2D wallPositionPoint(Transform t, ClassInfo c, int wallA, int wallB,
                                      double floorBottomZ, double floorTopZ, double tParam) {
        double[] a = wallCorner(c, c.baseSide(), wallA);
        double[] b = wallCorner(c, c.baseSide(), wallB);
        if (a == null || b == null) return t.project(c.x, c.y, 0);
        double px = a[0] + (b[0] - a[0]) * tParam;
        double py = a[1] + (b[1] - a[1]) * tParam;
        double midZ = (floorBottomZ + floorTopZ) / 2.0;
        return t.project(px, py, midZ);
    }

    private double wallLenSafe(ClassInfo c, int wallA, int wallB) {
        double[] a = wallCorner(c, c.baseSide(), wallA);
        double[] b = wallCorner(c, c.baseSide(), wallB);
        if (a == null || b == null) return 0;
        return Math.hypot(b[0] - a[0], b[1] - a[1]);
    }

    private double estimateScreenRadius(Transform t, ClassInfo c,
                                        int wallA, int wallB,
                                        double floorBottomZ, double floorTopZ,
                                        double tParam, double worldRadius) {
        double wallLen = wallLenSafe(c, wallA, wallB);
        double dt = (wallLen > 0) ? (worldRadius / wallLen) : 0.01;
        double tLeft  = Math.max(0.0, tParam - dt);
        double tRight = Math.min(1.0, tParam + dt);
        Point2D pL = wallPositionPoint(t, c, wallA, wallB, floorBottomZ, floorTopZ, tLeft);
        Point2D pR = wallPositionPoint(t, c, wallA, wallB, floorBottomZ, floorTopZ, tRight);
        double dist = Math.hypot(pR.getX() - pL.getX(), pR.getY() - pL.getY());
        return Math.max(1.5, dist / 2.0);
    }

    private double estimateScreenRadiusAtPoint(Transform t, double side,
                                               double z, Point2D screenCenter,
                                               double worldRadius) {
        Point2D p1 = t.project(0, 0, z);
        Point2D p2 = t.project(worldRadius, 0, z);
        double dx = p2.getX() - p1.getX();
        double dy = p2.getY() - p1.getY();
        double dist = Math.hypot(dx, dy);
        return Math.max(2.0, dist);
    }

    private void drawCircle(Graphics2D g, Point2D center, double radiusPx, Color color) {
        int r = Math.max(2, (int) Math.round(radiusPx));
        g.setColor(color);
        g.fillOval((int)center.getX() - r, (int)center.getY() - r, r * 2, r * 2);
        g.setColor(darker(color, 0.7));
        g.drawOval((int)center.getX() - r, (int)center.getY() - r, r * 2, r * 2);
    }

    private void drawInterfaceFan(Graphics2D g, Point2D p, double radiusPx) {
        int x = (int) p.getX();
        int y = (int) p.getY();
        int r = Math.max(3, (int) Math.round(radiusPx));
        int dotR = Math.max(2, r / 3);
        int armLen = (int) (r * 0.9);
        int ringR = r;
        g.setColor(FEATURE_RED);
        g.fillOval(x - dotR, y - dotR, dotR * 2, dotR * 2);
        g.setStroke(new BasicStroke(Math.max(1.0f, r * 0.18f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2.0;
            g.drawLine(x, y, (int) (x + Math.cos(a) * armLen), (int) (y + Math.sin(a) * armLen));
        }
        g.drawOval(x - ringR, y - ringR, ringR * 2, ringR * 2);
        g.setStroke(new BasicStroke(1f));
    }

    private void drawSuperclassTower(Graphics2D g, Point2D p, double baseW, double towerH) {
        int x = (int) p.getX();
        int y = (int) p.getY();
        int stemW = Math.max(2, (int) Math.round(baseW * 0.35));
        int stemH = Math.max(4, (int) Math.round(towerH));
        int baseWpx = Math.max(4, (int) Math.round(baseW));
        int capW  = Math.max(3, (int) Math.round(baseW * 0.55));
        int capH  = Math.max(2, (int) Math.round(towerH * 0.18));

        g.setColor(FEATURE_RED);
        g.fillRect(x - stemW / 2, y - stemH, stemW, stemH);
        g.fillRect(x - baseWpx / 2, y - 2, baseWpx, capH);
        g.fillRect(x - capW / 2, y - stemH - 2, capW, capH);
    }

    private void drawWallBand(Graphics2D g, Transform t, ClassInfo c, double floorZ, double side, int cornerA, int cornerB) {
        double[] a = wallCorner(c, side, cornerA);
        double[] b = wallCorner(c, side, cornerB);
        if (a == null || b == null) return;
        double zTop = floorZ;
        double zBot = Math.max(0.0, floorZ - FLOOR_BAND_H);
        Point2D pBL = t.project(a[0], a[1], zBot);
        Point2D pBR = t.project(b[0], b[1], zBot);
        Point2D pTR = t.project(b[0], b[1], zTop);
        Point2D pTL = t.project(a[0], a[1], zTop);
        Path2D path = new Path2D.Double();
        path.moveTo(pBL.getX(), pBL.getY());
        path.lineTo(pBR.getX(), pBR.getY());
        path.lineTo(pTR.getX(), pTR.getY());
        path.lineTo(pTL.getX(), pTL.getY());
        path.closePath();
        g.setColor(FLOOR_FILL);
        g.fill(path);
        g.setColor(FLOOR_TOP_EDGE);
        g.setStroke(new BasicStroke(1.0f));
        g.draw(new Line2D.Double(pTL, pTR));
        g.setStroke(new BasicStroke(1f));
    }

    private void drawAttributeBand(Graphics2D g, Transform t, ClassInfo c, double attrBot, double attrTop, int cornerA, int cornerB) {
        double[] a = wallCorner(c, c.baseSide(), cornerA);
        double[] b = wallCorner(c, c.baseSide(), cornerB);
        if (a == null || b == null) return;
        Point2D pBL = t.project(a[0], a[1], attrBot);
        Point2D pBR = t.project(b[0], b[1], attrBot);
        Point2D pTR = t.project(b[0], b[1], attrTop);
        Point2D pTL = t.project(a[0], a[1], attrTop);
        Path2D path = new Path2D.Double();
        path.moveTo(pBL.getX(), pBL.getY());
        path.lineTo(pBR.getX(), pBR.getY());
        path.lineTo(pTR.getX(), pTR.getY());
        path.lineTo(pTL.getX(), pTL.getY());
        path.closePath();
        g.setColor(ATTRIBUTE_FLOOR_COLOR);
        g.fill(path);
        g.setColor(ATTRIBUTE_FLOOR_COLOR.darker());
        g.setStroke(new BasicStroke(0.8f));
        g.draw(new Line2D.Double(pTL, pTR));
        g.draw(new Line2D.Double(pBL, pBR));
        g.setStroke(new BasicStroke(1f));
    }

    private void drawAttributeMarkers(Graphics2D g, Transform t, ClassInfo c,
                                      boolean southVisible, boolean northVisible,
                                      boolean eastVisible, boolean westVisible, List<Hit> out) {
        int n = c.attributes.size();
        if (n == 0) return;
        int wallA, wallB;
        if (southVisible)      { wallA = 0; wallB = 1; }
        else if (northVisible) { wallA = 2; wallB = 3; }
        else if (eastVisible)  { wallA = 1; wallB = 2; }
        else                   { wallA = 3; wallB = 0; }

        double[] a = wallCorner(c, c.baseSide(), wallA);
        double[] b = wallCorner(c, c.baseSide(), wallB);
        if (a == null || b == null) return;

        double bandTop = attrFloorTopZ(true);
        double bandBot = BASE_PLINTH_H;
        double markerTop = bandTop - 0.08;
        double markerBot = bandBot + 0.08;

        double wallLen = Math.hypot(b[0] - a[0], b[1] - a[1]);
        double sideMargin = 0.10;
        double usable = Math.max(0.05, wallLen - 2 * sideMargin);
        double step = n == 1 ? 0 : usable / (n - 1);

        for (int i = 0; i < n; i++) {
            AttributeInfo attr = c.attributes.get(i);
            double t01 = n == 1 ? 0.5 : (sideMargin + i * step) / wallLen;
            double px = a[0] + (b[0] - a[0]) * t01;
            double py = a[1] + (b[1] - a[1]) * t01;
            Point2D pTop = t.project(px, py, markerTop);
            Point2D pBot = t.project(px, py, markerBot);
            final double mx = (pTop.getX() + pBot.getX()) / 2.0;
            final double my = (pTop.getY() + pBot.getY()) / 2.0;
            double mh = ATTR_MARKER_H_PX;
            double mw = ATTR_MARKER_W_PX;
            Rectangle2D rect = new Rectangle2D.Double(mx - mw / 2.0, my - mh / 2.0, mw, mh);
            g.setColor(attr.isStatic ? STATIC_ATTRIBUTE_MARKER_COLOR : ATTRIBUTE_MARKER_COLOR);
            g.fill(rect);
            Rectangle2D clickRect = new Rectangle2D.Double(mx - mw / 2.0 - 4, my - mh / 2.0 - 4, mw + 8, mh + 8);
            final String attrId = "A:" + c.fullName() + ":" + attr.name;
            final double fmh = mh;
            out.add(new Hit(clickRect, new AttributeRef(c, attr), gg -> {
                if (labelAttribute || isHovered(attrId))
                    drawLabel(gg, attr.name, (int) mx, (int) (my - fmh / 2.0) - 4);
            }));
        }
    }

    private static double[] wallCorner(ClassInfo c, double side, int corner) {
        switch (corner) {
            case 0: return new double[] { c.x,        c.y        };
            case 1: return new double[] { c.x + side, c.y        };
            case 2: return new double[] { c.x + side, c.y + side };
            case 3: return new double[] { c.x,        c.y + side };
            default: return null;
        }
    }

    // ============================================================
    // RELATIONS
    // ============================================================
    private void drawRelations(Graphics2D g, Transform t, List<Hit> out) {
        g.setStroke(new BasicStroke(1.8f));

        if ((relationMask & AppConstants.REL_INHERITANCE) != 0) {
            for (ClassInfo subclass : model.classes) {
                if (subclass.superclass == null || subclass.superclass.isBlank()) continue;
                ClassInfo superclass = model.resolveClass(subclass.superclass, subclass);
                if (superclass != null) {
                    RelationRef rr = new RelationRef("Inheritance",
                            subclass.name, superclass.name,
                            "toward superclass",
                            subclass.name + "  →  " + superclass.name);
                    drawRelationLine(g, topPoint(t, subclass), topPoint(t, superclass),
                            TRIANGLE_BLUE, rr, out, true);
                }
            }
        }

        if ((relationMask & AppConstants.REL_COMPOSITION) != 0) {
            for (Composition ci : model.compositions) {
                ClassInfo owner     = model.resolveClass(ci.from, ci.scope);
                ClassInfo container = model.resolveClass(ci.to,   ci.scope);
                if (owner != null && container != null) {
                    RelationRef rr = new RelationRef("Composition/Aggregation",
                            owner.name, container.name,
                            "toward containing class",
                            owner.name + "  →  " + container.name);
                    drawRelationLine(g, topPoint(t, owner), topPoint(t, container),
                            TRIANGLE_BLACK, rr, out, true);
                }
            }
        }

        if ((relationMask & AppConstants.REL_INVOCATION) != 0) {
            for (ClassInfo owner : model.classes) {
                for (int mi = 0; mi < owner.methods.size(); mi++) {
                    MethodInfo invoking = owner.methods.get(mi);
                    for (String target : invoking.invocations) {
                        MethodTarget mt = model.resolveMethod(target, owner);
                        if (mt == null) continue;
                        int ti = mt.owner().methods.indexOf(mt.method());
                        if (ti < 0) continue;
                        Point2D from = methodMarkerPoint(t, owner, mi);
                        Point2D to   = methodMarkerPoint(t, mt.owner(), ti);
                        if (from != null && to != null) {
                            RelationRef rr = new RelationRef("Method Invocation",
                                    invoking.name + "()", mt.method().name + "()",
                                    "toward invoked method",
                                    invoking.name + "()  →  " + mt.method().name + "()");
                            drawRelationLine(g, from, to, TRIANGLE_GREEN, rr, out, true);
                        }
                    }
                }
            }
        }

        if ((relationMask & AppConstants.REL_ACCESS) != 0) {
            for (ClassInfo owner : model.classes) {
                for (int mi = 0; mi < owner.methods.size(); mi++) {
                    MethodInfo accessing = owner.methods.get(mi);
                    for (String target : accessing.attributeAccesses) {
                        AttributeTarget at = model.resolveAttribute(target, owner);
                        if (at == null) continue;
                        int ai = at.owner().attributes.indexOf(at.attribute());
                        if (ai < 0) continue;
                        Point2D from = methodMarkerPoint(t, owner, mi);
                        Point2D to   = attributeMarkerPoint(t, at.owner(), ai);
                        if (from != null && to != null) {
                            RelationRef rr = new RelationRef("Attribute Access",
                                    accessing.name + "()", at.attribute().name,
                                    "toward accessed attribute",
                                    accessing.name + "()  →  " + at.attribute().name);
                            drawRelationLine(g, from, to, TRIANGLE_ORANGE, rr, out, true);
                        }
                    }
                }
            }
        }
    }

    private void drawRelationLine(Graphics2D g, Point2D from, Point2D to, Color triangleColor,
                                  RelationRef rr, List<Hit> out, boolean orientToTarget) {
        if (from == null || to == null) return;

        g.setColor(RELATION_LINE_COLOR);
        g.setStroke(new BasicStroke(1.8f));
        g.draw(new Line2D.Double(from, to));

        double mx = (from.getX() + to.getX()) / 2.0;
        double my = (from.getY() + to.getY()) / 2.0;

        double angle = Math.atan2(to.getY() - from.getY(), to.getX() - from.getX());
        if (!orientToTarget) angle += Math.PI;

        double size = 10.0;
        double tipX = mx + size * 0.55 * Math.cos(angle);
        double tipY = my + size * 0.55 * Math.sin(angle);
        double backX = mx - size * 0.55 * Math.cos(angle);
        double backY = my - size * 0.55 * Math.sin(angle);
        double perpX = -Math.sin(angle) * size * 0.55;
        double perpY =  Math.cos(angle) * size * 0.55;

        int[] xs = { (int) tipX, (int) (backX + perpX), (int) (backX - perpX) };
        int[] ys = { (int) tipY, (int) (backY + perpY), (int) (backY - perpY) };

        g.setColor(triangleColor);
        g.fillPolygon(xs, ys, 3);
        g.setColor(Color.DARK_GRAY);
        g.setStroke(new BasicStroke(1.0f));
        g.drawPolygon(xs, ys, 3);

        if (out != null) {
            Ellipse2D relHit = new Ellipse2D.Double(mx - 10, my - 10, 20, 20);
            final String rid = rr.id();
            final RelationRef finalRr = rr;
            final double fmx = mx, fmy = my;
            out.add(new Hit(relHit, rr, gg -> {
                if (labelRelations || isHovered(rid))
                    drawLabel(gg, finalRr.source() + "  →  " + finalRr.target(), (int) fmx, (int) fmy - 10);
            }));
        }
    }

    private Point2D methodMarkerPoint(Transform t, ClassInfo c, int mi) {
        if (mi < 0 || mi >= c.methods.size()) return null;
        boolean hasAttr = !c.attributes.isEmpty();
        double floorBottom = methodFloorBottomZ(hasAttr, mi);
        double floorTop    = methodFloorTopZ(hasAttr, mi);
        int wallA = 0, wallB = 1;
        double dotSouth =  Math.cos(t.yaw), dotNorth = -Math.cos(t.yaw);
        double dotWest  =  Math.sin(t.yaw), dotEast  = -Math.sin(t.yaw);
        double best = Math.max(Math.max(dotSouth, dotNorth), Math.max(dotWest, dotEast));
        if (best == dotSouth)      { wallA = 0; wallB = 1; }
        else if (best == dotNorth) { wallA = 2; wallB = 3; }
        else if (best == dotWest)  { wallA = 3; wallB = 0; }
        else                       { wallA = 1; wallB = 2; }
        return wallPositionPoint(t, c, wallA, wallB, floorBottom, floorTop, 0.5);
    }

    private Point2D attributeMarkerPoint(Transform t, ClassInfo c, int ai) {
        if (ai < 0 || ai >= c.attributes.size()) return null;
        int n = c.attributes.size();
        double camDirX = -Math.sin(t.yaw);
        double camDirY = -Math.cos(t.yaw);
        boolean southVisible = camDirY > 0;
        boolean northVisible = camDirY < 0;
        boolean eastVisible  = camDirX < 0;
        boolean westVisible  = camDirX > 0;
        int wallA, wallB;
        if (southVisible)      { wallA = 0; wallB = 1; }
        else if (northVisible) { wallA = 2; wallB = 3; }
        else if (eastVisible)  { wallA = 1; wallB = 2; }
        else                   { wallA = 3; wallB = 0; }

        double[] a = wallCorner(c, c.baseSide(), wallA);
        double[] b = wallCorner(c, c.baseSide(), wallB);
        if (a == null || b == null) return null;

        double wallLen = Math.hypot(b[0] - a[0], b[1] - a[1]);
        double sideMargin = 0.10;
        double usable = Math.max(0.05, wallLen - 2 * sideMargin);
        double step = n == 1 ? 0 : usable / (n - 1);
        double t01 = n == 1 ? 0.5 : (sideMargin + ai * step) / wallLen;

        double px = a[0] + (b[0] - a[0]) * t01;
        double py = a[1] + (b[1] - a[1]) * t01;
        double midZ = (attrFloorTopZ(true) + BASE_PLINTH_H) / 2.0;
        return t.project(px, py, midZ);
    }

    private Point2D topPoint(Transform t, ClassInfo c) {
        return t.project(c.x + c.baseSide() / 2.0, c.y + c.baseSide() / 2.0, c.buildingHeight() + 0.10);
    }

    private boolean isHovered(String id) { return hovered != null && hovered.id().equals(id); }

    private static void polygon(Graphics2D g, Point2D[] points, Color fill, Color edge) {
        Path2D p = new Path2D.Double();
        p.moveTo(points[0].getX(), points[0].getY());
        for (int i = 1; i < points.length; i++) p.lineTo(points[i].getX(), points[i].getY());
        p.closePath();
        g.setColor(fill);
        g.fill(p);
        if (edge != null) { g.setColor(edge); g.draw(p); }
    }

    private static void fillOnly(Graphics2D g, Point2D[] points, Color fill) {
        Path2D p = new Path2D.Double();
        p.moveTo(points[0].getX(), points[0].getY());
        for (int i = 1; i < points.length; i++) p.lineTo(points[i].getX(), points[i].getY());
        p.closePath();
        g.setColor(fill);
        g.fill(p);
    }

    private static void polygonSoft(Graphics2D g, Point2D[] points, Color fill, Color softEdge) {
        Path2D p = new Path2D.Double();
        p.moveTo(points[0].getX(), points[0].getY());
        for (int i = 1; i < points.length; i++) p.lineTo(points[i].getX(), points[i].getY());
        p.closePath();
        g.setColor(fill);
        g.fill(p);
        if (softEdge != null) {
            g.setColor(softEdge);
            g.setStroke(new BasicStroke(0.8f));
            g.draw(p);
            g.setStroke(new BasicStroke(1f));
        }
    }

    private static Shape shape(Point2D[] points) {
        Path2D p = new Path2D.Double();
        p.moveTo(points[0].getX(), points[0].getY());
        for (int i = 1; i < points.length; i++) p.lineTo(points[i].getX(), points[i].getY());
        p.closePath();
        return p;
    }

    private static void drawLabel(Graphics2D g, String text, int x, int y) {
        Font old = g.getFont();
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        int width = g.getFontMetrics().stringWidth(text);
        g.setColor(new Color(20, 25, 32, 235));
        g.fillRoundRect(x - width / 2 - 8, y - 16, width + 16, 22, 6, 6);
        g.setColor(new Color(0xE8EBEF));
        g.drawString(text, x - width / 2, y);
        g.setFont(old);
    }

    private Ref hit(Point p) {
        for (int i = hits.size() - 1; i >= 0; i--)
            if (hits.get(i).shape.contains(p)) return hits.get(i).ref;
        return null;
    }

    @Override
    public void mousePressed(MouseEvent e) {
        requestFocusInWindow();
        last = e.getPoint();
        pressPoint = e.getPoint();
        panning = e.isShiftDown() || SwingUtilities.isMiddleMouseButton(e) || SwingUtilities.isRightMouseButton(e);
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (last == null) return;
        double dx = e.getX() - last.x, dy = e.getY() - last.y;
        if (panning) { panX += dx; panY += dy; }
        else { yaw += dx * 0.012; pitch = Math.max(0.15, Math.min(1.15, pitch + dy * 0.01)); }
        last = e.getPoint();
        repaint();
    }

    @Override public void mouseReleased(MouseEvent e) { last = null; panning = false; }

    @Override
    public void mouseMoved(MouseEvent e) {
        Ref r = hit(e.getPoint());
        if (!Objects.equals(r, hovered)) {
            hovered = r;
            setCursor(r == null ? Cursor.getDefaultCursor() : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            if (onPick != null) onPick.accept(r);
            repaint();
        }
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        boolean wasDrag = pressPoint != null && pressPoint.distance(e.getPoint()) > CLICK_DRAG_THRESHOLD;
        if (!panning && !wasDrag && onPick != null) onPick.accept(hit(e.getPoint()));
        if (!panning && !wasDrag && e.getClickCount() == 2) focusOn(hit(e.getPoint()));
    }

    void focusOn(Ref ref) {
        if (ref == null || model == null) return;
        double cx, cy, footprint, targetZ = 0.0;
        if (ref instanceof ClassRef cr) {
            ClassInfo c = cr.c();
            cx = c.x + c.baseSide() / 2.0;
            cy = c.y + c.baseSide() / 2.0;
            footprint = Math.max(1.5, c.baseSide() * 2.5);
            targetZ = c.buildingHeight() / 2.0;
        } else if (ref instanceof DistrictRef dr) {
            District d = dr.d();
            cx = d.x + d.width / 2.0;
            cy = d.y + d.height / 2.0;
            footprint = Math.max(d.width, d.height);
        } else if (ref instanceof MethodRef mr) {
            ClassInfo c = mr.c();
            MethodInfo m = mr.m();
            int idx = Math.max(0, c.methods.indexOf(m));
            double z = methodFloorMidZ(!c.attributes.isEmpty(), idx);
            boolean eastVisible = Math.sin(yaw) < 0;
            double wallX = eastVisible ? c.x + c.baseSide() + 0.04 : c.x - 0.04;
            cx = wallX;
            cy = c.y + c.baseSide() / 2.0;
            footprint = 1.2;
            targetZ = z;
        } else if (ref instanceof AttributeRef ar) {
            ClassInfo c = ar.c();
            cx = c.x + c.baseSide() / 2.0;
            cy = c.y + c.baseSide() / 2.0;
            footprint = Math.max(1.2, c.baseSide());
            targetZ = (attrFloorTopZ(!c.attributes.isEmpty()) + BASE_PLINTH_H) / 2.0;
        } else if (ref instanceof LocalVariableRef lr) {
            ClassInfo c = lr.c();
            int idx = Math.max(0, c.methods.indexOf(lr.method()));
            double z = methodFloorMidZ(!c.attributes.isEmpty(), idx);
            boolean eastVisible = Math.sin(yaw) < 0;
            double wallX = eastVisible ? c.x + c.baseSide() + 0.04 : c.x - 0.04;
            cx = wallX;
            cy = c.y + c.baseSide() / 2.0;
            footprint = 1.2;
            targetZ = z;
        } else if (ref instanceof RelationRef) {
            footprint = 3.0;
            cx = model.width / 2.0;
            cy = model.height / 2.0;
            targetZ = 0.5;
        } else {
            return;
        }

        double usable = Math.min(getWidth() - 80.0, getHeight() - 100.0);
        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, usable / Math.max(1.0, footprint * 42.0)));
        double scale = 42.0 * zoom;
        double u = cx * Math.cos(yaw) - cy * Math.sin(yaw);
        double v = cx * Math.sin(yaw) + cy * Math.cos(yaw);
        panX = -u * scale;
        panY = -(v * Math.sin(pitch) - targetZ * Math.cos(pitch)) * scale;
        repaint();
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        double oldZoom = zoom;
        double newZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * Math.pow(1.14, -e.getPreciseWheelRotation())));
        if (newZoom == oldZoom) return;
        double anchorX = getWidth() / 2.0 + panX;
        double anchorY = getHeight() * 0.60 + panY;
        double ratio = newZoom / oldZoom;
        double mx = e.getX(), my = e.getY();
        double newAnchorX = mx + ratio * (anchorX - mx);
        double newAnchorY = my + ratio * (anchorY - my);
        panX = newAnchorX - getWidth() / 2.0;
        panY = newAnchorY - getHeight() * 0.60;
        zoom = newZoom;
        repaint();
    }

    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e) { hovered = null; repaint(); }

    static final double KEY_PAN_STEP = 28;
    static final double KEY_ZOOM_STEP = 1.12;

    @Override
    public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_LEFT:   panX += KEY_PAN_STEP; break;
            case KeyEvent.VK_RIGHT:  panX -= KEY_PAN_STEP; break;
            case KeyEvent.VK_UP:     panY += KEY_PAN_STEP; break;
            case KeyEvent.VK_DOWN:   panY -= KEY_PAN_STEP; break;
            case KeyEvent.VK_PLUS:
            case KeyEvent.VK_EQUALS:
            case KeyEvent.VK_ADD:    zoom = Math.min(MAX_ZOOM, zoom * KEY_ZOOM_STEP); break;
            case KeyEvent.VK_MINUS:
            case KeyEvent.VK_SUBTRACT: zoom = Math.max(MIN_ZOOM, zoom / KEY_ZOOM_STEP); break;
            case KeyEvent.VK_HOME:   resetCamera(); break;
            case KeyEvent.VK_F:      fitCity(); break;
            default: return;
        }
        repaint();
    }
    @Override public void keyReleased(KeyEvent e) {}
    @Override public void keyTyped(KeyEvent e) {}
}
