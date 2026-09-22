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

/**
 * GhrandalCodeCity — self-contained Java/Swing Code City viewer.
 */
public class CodeCityApp extends JFrame {
    private static final long serialVersionUID = 1L;
    private final CityView view = new CityView();
    private final JTextPane detail = new JTextPane();
    private final JLabel statusLabel = new JLabel("  Ready");
    private final PlaceholderTextField search = new PlaceholderTextField("Search classes, packages, methods, attributes…");
    private JPopupMenu suggestionPopup;
    private JList<String> suggestionList;
    private DefaultListModel<String> suggestionModel;
    private final List<Ref> suggestionRefs = new ArrayList<>();
    private SearchIndex searchIndex = new SearchIndex();

    // ---- Professional dark palette for UI ----
    static final Color UI_BG           = new Color(0x1B1F27);
    static final Color UI_BG_DARK      = new Color(0x14171E);
    static final Color UI_BG_DARKER    = new Color(0x0E1116);
    static final Color UI_PANEL        = new Color(0x20252E);
    static final Color UI_PANEL_HOVER  = new Color(0x2A313C);
    static final Color UI_BORDER       = new Color(0x2A313C);
    static final Color UI_TEXT         = new Color(0xE8EBEF);
    static final Color UI_TEXT_DIM     = new Color(0x9AA3B0);
    static final Color UI_TEXT_MUTED   = new Color(0x6B7580);
    static final Color UI_ACCENT       = new Color(0x3D8BFD);
    static final Color UI_ACCENT_HOVER = new Color(0x5CA0FF);

    // ============================================================
    // RELATION FILTER BITS
    // ============================================================
    static final int REL_NONE        = 0;
    static final int REL_INHERITANCE = 1 << 0;
    static final int REL_COMPOSITION = 1 << 1;
    static final int REL_INVOCATION  = 1 << 2;
    static final int REL_ACCESS      = 1 << 3;
    static final int REL_ALL         = REL_INHERITANCE | REL_COMPOSITION | REL_INVOCATION | REL_ACCESS;

    public static void main(String[] args) {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ignored) {}
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        SwingUtilities.invokeLater(() -> new CodeCityApp().setVisible(true));
    }

    public CodeCityApp() {
        super("GhrandalCodeCity");
        AppSettings.resetCityBackground();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(AppSettings.windowWidth(), AppSettings.windowHeight());
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setLocationRelativeTo(null);
        addWindowListener(new WindowAdapter() { @Override public void windowClosing(WindowEvent e) { AppSettings.saveWindow(getWidth(), getHeight()); } });
        setBackground(UI_BG);

        setJMenuBar(createMenuBar());
        getContentPane().setBackground(UI_BG);

        detail.setEditable(false);
        detail.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        detail.setBackground(UI_PANEL);
        detail.setForeground(UI_TEXT);
        detail.setCaretColor(UI_TEXT);
        detail.setBorder(new EmptyBorder(14, 16, 14, 16));
        detail.setText("Open a Code-Metamodel XML file from File → Open XML…\nThen hover or click any element in the city.");

        JScrollPane detailScroll = new JScrollPane(detail);
        detailScroll.setBorder(null);
        detailScroll.getViewport().setBackground(UI_PANEL);
        detailScroll.getVerticalScrollBar().setUnitIncrement(16);
        detailScroll.getVerticalScrollBar().setBackground(UI_PANEL);

        JPanel right = new JPanel(new BorderLayout());
        right.setPreferredSize(new Dimension(400, 1));
        right.setBackground(UI_PANEL);
        right.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, UI_BORDER));

        JPanel detailsHeader = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(UI_BG_DARKER);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(UI_BORDER);
                g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
            }
        };
        detailsHeader.setOpaque(false);
        detailsHeader.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        JLabel detailsTitle = new JLabel("DETAILS");
        detailsTitle.setForeground(UI_TEXT);
        detailsTitle.setFont(new Font("SansSerif", Font.BOLD, 11));
        JLabel detailsSub = new JLabel("Hover / select an element");
        detailsSub.setForeground(UI_TEXT_MUTED);
        detailsSub.setFont(new Font("SansSerif", Font.PLAIN, 10));
        JPanel detailsTitleBox = new JPanel();
        detailsTitleBox.setLayout(new BoxLayout(detailsTitleBox, BoxLayout.Y_AXIS));
        detailsTitleBox.setOpaque(false);
        detailsTitleBox.add(detailsTitle);
        detailsTitleBox.add(Box.createVerticalStrut(2));
        detailsTitleBox.add(detailsSub);
        detailsHeader.add(detailsTitleBox, BorderLayout.CENTER);

        right.add(detailsHeader, BorderLayout.NORTH);
        right.add(detailScroll, BorderLayout.CENTER);

        JPanel status = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(UI_BG_DARKER);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(UI_BORDER);
                g.drawLine(0, 0, getWidth(), 0);
            }
        };
        status.setOpaque(false);
        status.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        statusLabel.setForeground(UI_TEXT_DIM);
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        status.add(statusLabel, BorderLayout.WEST);

        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(UI_BG_DARK);
        top.add(createToolbar(), BorderLayout.NORTH);
        top.add(createActionBar(), BorderLayout.SOUTH);

        add(top, BorderLayout.NORTH);
        add(view, BorderLayout.CENTER);
        add(right, BorderLayout.EAST);
        add(status, BorderLayout.SOUTH);

        view.onPick = ref -> {
            if (ref == null) {
                detail.setText("Hover or click any element in the city to see its details here.");
                statusLabel.setText("  Ready");
            } else {
                detail.setText(ref.info());
                detail.setCaretPosition(0);
                statusLabel.setText("  Selected: " + ref.id());
            }
        };
    }

    // ============================================================
    // TOOLBAR
    // ============================================================
    private JPanel createToolbar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 8)) {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(UI_BG_DARKER);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(UI_BORDER);
                g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
            }
        };
        bar.setOpaque(false);
        bar.setPreferredSize(new Dimension(1, 52));

        bar.add(iconButton(IconType.OPEN,  "Open XML"));
        bar.add(iconButton(IconType.PNG,   "Save PNG"));
        bar.add(iconButton(IconType.PDF,   "Save PDF"));
        bar.add(separator());
        bar.add(iconButton(IconType.ZOOM_OUT, "Zoom Out"));
        bar.add(iconButton(IconType.ZOOM_IN,  "Zoom In"));
        bar.add(iconButton(IconType.FIT,      "Fit City"));
        bar.add(iconButton(IconType.HOME,     "Reset Camera"));
        bar.add(separator());

        JLabel searchIcon = new JLabel(new VectorIcon(IconType.SEARCH, 16, UI_TEXT_MUTED));
        searchIcon.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));

        search.setPreferredSize(new Dimension(280, 32));
        search.setBackground(UI_PANEL);
        search.setForeground(UI_TEXT);
        search.setCaretColor(UI_ACCENT);
        search.setFont(new Font("SansSerif", Font.PLAIN, 12));
        search.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UI_BORDER, 1),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        search.addActionListener(e -> searchAndFocus(search.getText()));

        setupSuggestions();

        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { updateSuggestions(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { updateSuggestions(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { updateSuggestions(); }
        });
        search.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (suggestionPopup == null || !suggestionPopup.isVisible()) return;
                int idx = suggestionList.getSelectedIndex();
                if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    idx = Math.min(suggestionModel.size() - 1, idx + 1);
                    suggestionList.setSelectedIndex(idx);
                    suggestionList.ensureIndexIsVisible(idx);
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_UP) {
                    idx = Math.max(0, idx - 1);
                    suggestionList.setSelectedIndex(idx);
                    suggestionList.ensureIndexIsVisible(idx);
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    if (idx >= 0 && idx < suggestionRefs.size()) {
                        Ref r = suggestionRefs.get(idx);
                        hideSuggestions();
                        detail.setText(r.info());
                        detail.setCaretPosition(0);
                        statusLabel.setText("  Found: " + r.id());
                        view.focusOn(r);
                    }
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    hideSuggestions();
                }
            }
        });
        search.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent e) {
                SwingUtilities.invokeLater(() -> {
                    if (suggestionPopup != null && !suggestionList.isFocusOwner()) hideSuggestions();
                });
            }
        });

        JPanel searchWrap = new JPanel(new BorderLayout());
        searchWrap.setOpaque(false);
        searchWrap.add(searchIcon, BorderLayout.WEST);
        searchWrap.add(search, BorderLayout.CENTER);
        bar.add(searchWrap);

        JButton go = pillButton("Go", e -> searchAndFocus(search.getText()));
        bar.add(go);

        return bar;
    }

    private void setupSuggestions() {
        suggestionModel = new DefaultListModel<>();
        suggestionList = new JList<>(suggestionModel);
        suggestionList.setFont(new Font("SansSerif", Font.PLAIN, 12));
        suggestionList.setBackground(UI_PANEL);
        suggestionList.setForeground(UI_TEXT);
        suggestionList.setSelectionBackground(UI_ACCENT);
        suggestionList.setSelectionForeground(Color.WHITE);
        suggestionList.setFixedCellHeight(24);
        suggestionList.setBorder(new EmptyBorder(4, 6, 4, 6));
        suggestionList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int idx = suggestionList.locationToIndex(e.getPoint());
                if (idx >= 0 && idx < suggestionRefs.size()) {
                    Ref r = suggestionRefs.get(idx);
                    hideSuggestions();
                    detail.setText(r.info());
                    detail.setCaretPosition(0);
                    statusLabel.setText("  Found: " + r.id());
                    view.focusOn(r);
                }
            }
        });

        JScrollPane sp = new JScrollPane(suggestionList);
        sp.setBorder(BorderFactory.createLineBorder(UI_BORDER));
        sp.getViewport().setBackground(UI_PANEL);

        suggestionPopup = new JPopupMenu();
        suggestionPopup.setLayout(new BorderLayout());
        suggestionPopup.setBorder(BorderFactory.createEmptyBorder());
        suggestionPopup.add(sp, BorderLayout.CENTER);
        suggestionPopup.setFocusable(false);
    }

    private void updateSuggestions() {
        if (view.model == null) { hideSuggestions(); return; }
        String query = search.getText().trim();
        if (query.isEmpty()) { hideSuggestions(); return; }

        List<Ref> matches = findMatches(view.model, query, 30);
        if (matches.isEmpty()) { hideSuggestions(); return; }

        suggestionRefs.clear();
        suggestionRefs.addAll(matches);
        suggestionModel.clear();
        for (Ref r : matches) suggestionModel.addElement(suggestionLabel(r));

        suggestionList.setSelectedIndex(0);
        suggestionPopup.setPopupSize(new Dimension(Math.max(280, search.getWidth()), Math.min(280, 26 * matches.size() + 12)));
        if (!suggestionPopup.isVisible()) {
            suggestionPopup.show(search, 0, search.getHeight() + 2);
        } else {
            suggestionPopup.pack();
            suggestionPopup.setPopupSize(new Dimension(Math.max(280, search.getWidth()), Math.min(280, 26 * matches.size() + 12)));
        }
        search.requestFocusInWindow();
    }

    private void hideSuggestions() {
        if (suggestionPopup != null && suggestionPopup.isVisible()) suggestionPopup.setVisible(false);
    }

    private static String suggestionLabel(Ref r) {
        if (r instanceof DistrictRef dr)  return "▣  " + dr.d().name + "   (package)";
        if (r instanceof ClassRef cr)     return "▤  " + cr.c().fullName() + "   (class)";
        if (r instanceof MethodRef mr)    return "ƒ  " + mr.c().name + "." + mr.m().name + "()   (method)";
        if (r instanceof AttributeRef ar) return "◆  " + ar.c().name + "." + ar.a().name + "   (attribute)";
        if (r instanceof LocalVariableRef lr) {
            String name = (lr.index() >= 0 && lr.index() < lr.method().locals.size())
                    ? lr.method().locals.get(lr.index()) : "?";
            return "●  " + name + "   (local in " + lr.method().name + ")";
        }
        if (r instanceof RelationRef rr) return "◄►  " + rr.source() + " → " + rr.target() + "   (relation)";
        return r.id();
    }

    private List<Ref> findMatches(Model model, String query, int limit) {
        if (model == null) return Collections.emptyList();
        if (searchIndex == null) searchIndex = SearchIndex.build(model);
        return searchIndex.prefix(query, limit);
    }

    private static void collectMatchingDistricts(District d, String needleLower, LinkedHashSet<Ref> out, int limit) {
        if (out.size() >= limit) return;
        boolean isRootContainer = (d.level == 0 && (d.name == null || d.name.isEmpty()));
        if (!isRootContainer && !d.name.isEmpty() && (d.name.toLowerCase(Locale.ROOT).startsWith(needleLower)
                || d.path.toLowerCase(Locale.ROOT).startsWith(needleLower))) {
            out.add(new DistrictRef(d));
        }
        for (District child : d.children) collectMatchingDistricts(child, needleLower, out, limit);
    }

    private JPanel createActionBar() {
        JPanel bar = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(UI_BG_DARK);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(UI_BORDER);
                g.drawLine(0, 0, getWidth(), 0);
            }
        };
        bar.setOpaque(false);
        bar.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));

        JLabel hint = new JLabel("Drag to rotate  ·  Shift-drag or right-drag to pan  ·  Scroll to zoom  ·  Double-click to focus");
        hint.setForeground(UI_TEXT_MUTED);
        hint.setFont(new Font("SansSerif", Font.PLAIN, 11));
        bar.add(hint, BorderLayout.WEST);

        return bar;
    }

    private JButton iconButton(IconType icon, String tooltip) {
        JButton b = new JButton(new VectorIcon(icon, 20, UI_TEXT));
        b.setToolTipText(tooltip);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        b.setContentAreaFilled(false);
        b.setOpaque(true);
        b.setBackground(UI_BG_DARKER);
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        b.setPreferredSize(new Dimension(40, 34));
        b.addActionListener(e -> {
            switch (icon) {
                case OPEN:     openXml(); break;
                case PNG:      save(false); break;
                case PDF:      save(true); break;
                case ZOOM_IN:  zoomIn(); break;
                case ZOOM_OUT: zoomOut(); break;
                case FIT:      view.fitCity(); view.repaint(); break;
                case HOME:     view.resetCamera(); view.repaint(); break;
                default: break;
            }
        });
        b.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                b.setBackground(UI_PANEL_HOVER);
                b.setIcon(new VectorIcon(icon, 20, UI_ACCENT_HOVER));
            }
            @Override public void mouseExited(MouseEvent e) {
                b.setBackground(UI_BG_DARKER);
                b.setIcon(new VectorIcon(icon, 20, UI_TEXT));
            }
        });
        return b;
    }

    private JButton pillButton(String text, ActionListener action) {
        JButton b = new JButton(text);
        b.setFont(new Font("SansSerif", Font.BOLD, 12));
        b.setForeground(Color.WHITE);
        b.setBackground(UI_ACCENT);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(8, 22, 8, 22));
        b.setContentAreaFilled(false);
        b.setOpaque(true);
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        b.addActionListener(action);
        b.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { b.setBackground(UI_ACCENT_HOVER); }
            @Override public void mouseExited(MouseEvent e)  { b.setBackground(UI_ACCENT); }
        });
        return b;
    }

    private JPanel separator() {
        JPanel p = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(UI_BORDER);
                g.fillRect(getWidth() / 2, 4, 1, getHeight() - 8);
            }
        };
        p.setOpaque(false);
        p.setPreferredSize(new Dimension(10, 34));
        return p;
    }

    // ============================================================
    // Icons
    // ============================================================





    // ============================================================
    // MENU BAR
    // ============================================================
    private JMenuBar createMenuBar() {
        JMenuBar bar = new JMenuBar() {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(UI_BG_DARKER);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(UI_BORDER);
                g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
            }
        };
        bar.setOpaque(true);
        bar.setBackground(UI_BG_DARKER);
        bar.setBorderPainted(false);
        bar.setPreferredSize(new Dimension(1, 32));
        bar.setUI(new BasicMenuBarUI() {
            @Override public void paint(Graphics g, JComponent c) {
                g.setColor(UI_BG_DARKER);
                g.fillRect(0, 0, c.getWidth(), c.getHeight());
            }
        });

        bar.add(customMenu("File", new String[][]{
                {"Open XML…", "ctrl O"},
                {"-", null},
                {"Save as PNG…", null},
                {"Save as PDF…", null},
                {"Save as SVG…", null},
                {"-", null},
                {"Exit", null}
        }, new ActionListener[]{
                e -> openXml(), null, e -> save(false), e -> save(true), e -> saveCurrentViewSvg(), null, e -> dispose()
        }));

        bar.add(customMenu("View", new String[][]{
                {"Zoom In", "ctrl PLUS"},
                {"Zoom Out", "ctrl MINUS"},
                {"-", null},
                {"Fit City", "F"},
                {"Reset Camera", "HOME"},
                {"-", null},
                {"System Summary", null}
        }, new ActionListener[]{
                e -> zoomIn(), e -> zoomOut(), null,
                e -> { view.fitCity(); view.repaint(); },
                e -> { view.resetCamera(); view.repaint(); },
                null, e -> { detail.setText(systemSummary()); detail.setCaretPosition(0); }
        }));

        bar.add(createLayoutsMenu());
        bar.add(createAppearanceMenu());
        bar.add(createVisibilityMenu());
        bar.add(createLabelsMenu());
        bar.add(createRelationsMenu());
        bar.add(createArchitectureMenu());

        bar.add(customMenu("Help", new String[][]{
                {"Legend…", null},
                {"-", null},
                {"About GhrandalCodeCity…", null}
        }, new ActionListener[]{
                e -> showLegend(), null, e -> showAbout()
        }));

        return bar;
    }


    private JMenu createArchitectureMenu() {
        JMenu m = styledMenu("Architecture");
        JMenuItem metrics = styledItem("Metrics Dashboard…");
        metrics.addActionListener(e -> { if (view.model == null) { statusLabel.setText("  Open an XML file first."); return; } new MetricsDialog(this, view.model).setVisible(true); });
        JMenuItem graph = styledItem("Dependency Graph…");
        graph.addActionListener(e -> { if (view.model == null) { statusLabel.setText("  Open an XML file first."); return; } new DependencyGraphDialog(this, view.model).setVisible(true); });
        m.add(metrics);
        m.add(graph);
        return m;
    }


    // ============================================================
    // APPEARANCE MENU
    // ============================================================
    private JMenu createAppearanceMenu() {
        JMenu m = styledMenu("Appearance");

        JMenuItem choose = styledItem("City Background Color…");
        choose.addActionListener(e -> chooseCityBackground());
        m.add(choose);

        JMenuItem reset = styledItem("Reset City Background");
        reset.addActionListener(e -> {
            Color defaultColor = AppSettings.defaultCityBackground();
            AppSettings.resetCityBackground();
            view.setBackground(defaultColor);
            view.repaint();
            statusLabel.setText("  City background reset.");
        });
        m.add(reset);

        return m;
    }

    private void chooseCityBackground() {
        Color current = AppSettings.cityBackground();
        Color selected = JColorChooser.showDialog(this, "Choose City Background Color", current);
        if (selected == null) return;

        AppSettings.setCityBackground(selected);
        view.setBackground(selected);
        view.repaint();
        statusLabel.setText(String.format(Locale.ROOT,
                "  City background: #%02X%02X%02X",
                selected.getRed(), selected.getGreen(), selected.getBlue()));
    }

    // ============================================================
    // LAYOUTS MENU
    // ============================================================
    private JMenu createLayoutsMenu() {
        JMenu m = styledMenu("Layouts");
        ButtonGroup group = new ButtonGroup();

        JRadioButtonMenuItem hierarchical = styledRadioItem("Hierarchical", Layout.mode() == Layout.Mode.HIERARCHICAL);
        JRadioButtonMenuItem compact = styledRadioItem("Compact", Layout.mode() == Layout.Mode.COMPACT);
        JRadioButtonMenuItem wide = styledRadioItem("Wide Packages", Layout.mode() == Layout.Mode.WIDE);
        JRadioButtonMenuItem grid = styledRadioItem("Package Grid", Layout.mode() == Layout.Mode.GRID);
        JRadioButtonMenuItem lane = styledRadioItem("Lane", Layout.mode() == Layout.Mode.LANE);

        group.add(hierarchical);
        group.add(compact);
        group.add(wide);
        group.add(grid);
        group.add(lane);

        hierarchical.addActionListener(e -> applyLayout(Layout.Mode.HIERARCHICAL));
        compact.addActionListener(e -> applyLayout(Layout.Mode.COMPACT));
        wide.addActionListener(e -> applyLayout(Layout.Mode.WIDE));
        grid.addActionListener(e -> applyLayout(Layout.Mode.GRID));
        lane.addActionListener(e -> applyLayout(Layout.Mode.LANE));

        m.add(hierarchical);
        m.add(compact);
        m.add(wide);
        m.add(grid);
        m.add(lane);
        m.getPopupMenu().addSeparator();

        JMenuItem fit = styledItem("Fit City After Layout");
        fit.addActionListener(e -> {
            view.fitCity();
            view.repaint();
        });
        m.add(fit);
        return m;
    }

    private void applyLayout(Layout.Mode mode) {
        Layout.setMode(mode);
        if (view.model != null) {
            Layout.layout(view.model);
            view.fitCity();
        }
        statusLabel.setText("  Layout: " + mode.label);
        view.repaint();
    }

    // ============================================================
    // ELEMENTS MENU
    // ============================================================
    private JMenu createVisibilityMenu() {
        JMenu m = styledMenu("Elements");

        JCheckBoxMenuItem showPackages = styledCheckItem("Show packages", view.showPackages);
        showPackages.addActionListener(e -> {
            view.showPackages = showPackages.isSelected();
            view.repaint();
        });

        JCheckBoxMenuItem showClasses = styledCheckItem("Show classes", view.showClasses);
        showClasses.addActionListener(e -> {
            view.showClasses = showClasses.isSelected();
            view.repaint();
        });

        JCheckBoxMenuItem showAttributes = styledCheckItem("Show attributes", view.showAttributes);
        showAttributes.addActionListener(e -> {
            view.showAttributes = showAttributes.isSelected();
            view.repaint();
        });

        JCheckBoxMenuItem showMethods = styledCheckItem("Show methods", view.showMethods);
        showMethods.addActionListener(e -> {
            view.showMethods = showMethods.isSelected();
            view.repaint();
        });

        JCheckBoxMenuItem showLocalVars = styledCheckItem("Show local variables", view.showLocalVars);
        showLocalVars.addActionListener(e -> {
            view.showLocalVars = showLocalVars.isSelected();
            view.repaint();
        });

        JCheckBoxMenuItem showStaticMethods = styledCheckItem("Show static methods", view.showStaticMethods);
        showStaticMethods.addActionListener(e -> {
            view.showStaticMethods = showStaticMethods.isSelected();
            view.repaint();
        });

        JCheckBoxMenuItem showStreetGrid = styledCheckItem("Street grid", view.showStreetGrid);
        showStreetGrid.addActionListener(e -> {
            view.showStreetGrid = showStreetGrid.isSelected();
            view.repaint();
        });

        JMenuItem allOn = styledItem("All on");
        allOn.addActionListener(e -> {
            view.showPackages      = true;
            view.showClasses       = true;
            view.showAttributes    = true;
            view.showMethods       = true;
            view.showLocalVars     = true;
            view.showStaticMethods = true;
            showStreetGrid.setSelected(true);
            view.showStreetGrid   = true;
            showPackages.setSelected(true);
            showClasses.setSelected(true);
            showAttributes.setSelected(true);
            showMethods.setSelected(true);
            showLocalVars.setSelected(true);
            showStaticMethods.setSelected(true);
            view.repaint();
        });

        JMenuItem allOff = styledItem("All off");
        allOff.addActionListener(e -> {
            view.showPackages      = false;
            view.showClasses       = false;
            view.showAttributes    = false;
            view.showMethods       = false;
            view.showLocalVars     = false;
            view.showStaticMethods = false;
            view.showStreetGrid    = false;
            showPackages.setSelected(false);
            showClasses.setSelected(false);
            showAttributes.setSelected(false);
            showMethods.setSelected(false);
            showLocalVars.setSelected(false);
            showStaticMethods.setSelected(false);
            view.repaint();
        });

        m.add(showPackages);
        m.add(showClasses);
        m.add(showAttributes);
        m.add(showMethods);
        m.add(showLocalVars);
        m.add(showStaticMethods);
        m.add(showStreetGrid);
        m.getPopupMenu().addSeparator();
        m.add(allOn);
        m.add(allOff);

        return m;
    }

    // ============================================================
    // LABELS MENU
    // ============================================================
    private JMenu createLabelsMenu() {
        JMenu m = styledMenu("Labels");

        JCheckBoxMenuItem lblPackage = styledCheckItem("Package labels", view.labelPackage);
        JCheckBoxMenuItem lblClass   = styledCheckItem("Class labels", view.labelClass);
        JCheckBoxMenuItem lblAttr    = styledCheckItem("Attribute labels", view.labelAttribute);
        JCheckBoxMenuItem lblMethod  = styledCheckItem("Method labels", view.labelMethod);
        JCheckBoxMenuItem lblLocal   = styledCheckItem("Local-variable labels", view.labelLocal);
        JCheckBoxMenuItem lblRelations = styledCheckItem("Relation labels", view.labelRelations);

        lblPackage.addActionListener(e -> {
            view.labelPackage = lblPackage.isSelected();
            view.repaint();
        });
        lblClass.addActionListener(e -> {
            view.labelClass = lblClass.isSelected();
            view.repaint();
        });
        lblAttr.addActionListener(e -> {
            view.labelAttribute = lblAttr.isSelected();
            view.repaint();
        });
        lblMethod.addActionListener(e -> {
            view.labelMethod = lblMethod.isSelected();
            view.repaint();
        });
        lblLocal.addActionListener(e -> {
            view.labelLocal = lblLocal.isSelected();
            view.repaint();
        });
        lblRelations.addActionListener(e -> {
            view.labelRelations = lblRelations.isSelected();
            view.repaint();
        });

        JMenuItem allOn = styledItem("All on");
        allOn.addActionListener(e -> {
            view.labelPackage    = true;
            view.labelClass      = true;
            view.labelAttribute  = true;
            view.labelMethod     = true;
            view.labelLocal      = true;
            view.labelRelations  = true;
            lblPackage.setSelected(true);
            lblClass.setSelected(true);
            lblAttr.setSelected(true);
            lblMethod.setSelected(true);
            lblLocal.setSelected(true);
            lblRelations.setSelected(true);
            view.repaint();
        });

        JMenuItem allOff = styledItem("All off");
        allOff.addActionListener(e -> {
            view.labelPackage    = false;
            view.labelClass      = false;
            view.labelAttribute  = false;
            view.labelMethod     = false;
            view.labelLocal      = false;
            view.labelRelations  = false;
            lblPackage.setSelected(false);
            lblClass.setSelected(false);
            lblAttr.setSelected(false);
            lblMethod.setSelected(false);
            lblLocal.setSelected(false);
            lblRelations.setSelected(false);
            view.repaint();
        });

        m.add(lblPackage);
        m.add(lblClass);
        m.add(lblAttr);
        m.add(lblMethod);
        m.add(lblLocal);
        m.getPopupMenu().addSeparator();
        m.add(lblRelations);
        m.getPopupMenu().addSeparator();
        m.add(allOn);
        m.add(allOff);

        return m;
    }

    // ============================================================
    // RELATIONS MENU
    // ============================================================
    private JMenu createRelationsMenu() {
        JMenu m = styledMenu("Relations");

        JCheckBoxMenuItem cbInheritance = styledCheckItem("Inheritance",
                (view.relationMask & REL_INHERITANCE) != 0);
        JCheckBoxMenuItem cbComposition = styledCheckItem("Composition",
                (view.relationMask & REL_COMPOSITION) != 0);
        JCheckBoxMenuItem cbInvocation  = styledCheckItem("Method Invocation",
                (view.relationMask & REL_INVOCATION) != 0);
        JCheckBoxMenuItem cbAccess      = styledCheckItem("Attribute Access",
                (view.relationMask & REL_ACCESS) != 0);

        cbInheritance.addActionListener(e -> {
            toggleRelation(REL_INHERITANCE, cbInheritance.isSelected());
            view.repaint();
        });
        cbComposition.addActionListener(e -> {
            toggleRelation(REL_COMPOSITION, cbComposition.isSelected());
            view.repaint();
        });
        cbInvocation.addActionListener(e -> {
            toggleRelation(REL_INVOCATION, cbInvocation.isSelected());
            view.repaint();
        });
        cbAccess.addActionListener(e -> {
            toggleRelation(REL_ACCESS, cbAccess.isSelected());
            view.repaint();
        });

        JMenuItem allOn = styledItem("All on");
        allOn.addActionListener(e -> {
            view.relationMask = REL_ALL;
            cbInheritance.setSelected(true);
            cbComposition.setSelected(true);
            cbInvocation.setSelected(true);
            cbAccess.setSelected(true);
            view.repaint();
        });

        JMenuItem allOff = styledItem("All off");
        allOff.addActionListener(e -> {
            view.relationMask = REL_NONE;
            cbInheritance.setSelected(false);
            cbComposition.setSelected(false);
            cbInvocation.setSelected(false);
            cbAccess.setSelected(false);
            view.repaint();
        });

        m.add(cbInheritance);
        m.add(cbComposition);
        m.add(cbInvocation);
        m.add(cbAccess);
        m.getPopupMenu().addSeparator();
        m.add(allOn);
        m.add(allOff);

        return m;
    }

    private void toggleRelation(int bit, boolean on) {
        if (on) view.relationMask |= bit;
        else    view.relationMask &= ~bit;
    }

    // ---- shared menu styling helpers ----
    private JMenu styledMenu(String title) {
        JMenu m = new JMenu(title) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isSelected() || isPopupMenuVisible() ? UI_PANEL_HOVER : UI_BG_DARKER);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(UI_TEXT);
                g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
                FontMetrics fm = g2.getFontMetrics();
                int tx = (getWidth() - fm.stringWidth(getText())) / 2;
                int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(getText(), tx, ty);
                g2.dispose();
            }
        };
        m.setForeground(UI_TEXT);
        m.setFont(new Font("SansSerif", Font.PLAIN, 12));
        m.setBorder(BorderFactory.createEmptyBorder(4, 16, 4, 16));
        m.setOpaque(false);
        m.setContentAreaFilled(false);

        JPopupMenu popup = m.getPopupMenu();
        popup.setBackground(UI_PANEL);
        popup.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UI_BORDER),
                BorderFactory.createEmptyBorder(6, 0, 6, 0)));
        return m;
    }

    private JRadioButtonMenuItem styledRadioItem(String text, boolean selected) {
        JRadioButtonMenuItem item = new JRadioButtonMenuItem(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setColor(isArmed() || isSelected() ? UI_PANEL_HOVER : UI_PANEL);
                g2.fillRect(0, 0, getWidth(), getHeight());

                int cx = 13, cy = getHeight() / 2;
                g2.setColor(UI_TEXT_DIM);
                g2.drawOval(cx - 6, cy - 6, 12, 12);
                if (isSelected()) {
                    g2.setColor(UI_ACCENT);
                    g2.fillOval(cx - 3, cy - 3, 6, 6);
                }

                g2.setColor(UI_TEXT);
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(getText(), 28, ty);
                g2.dispose();
            }
        };
        item.setSelected(selected);
        item.setForeground(UI_TEXT);
        item.setBackground(UI_PANEL);
        item.setFont(new Font("SansSerif", Font.PLAIN, 12));
        item.setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 30));
        item.setOpaque(true);
        item.setContentAreaFilled(false);
        return item;
    }

    private JCheckBoxMenuItem styledCheckItem(String text, boolean selected) {
        JCheckBoxMenuItem item = new JCheckBoxMenuItem(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                Color bg = isArmed() || isSelected() ? UI_PANEL_HOVER : UI_PANEL;
                g2.setColor(bg);
                g2.fillRect(0, 0, getWidth(), getHeight());

                if (isSelected()) {
                    g2.setColor(Color.WHITE);
                    g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    int bx = 8;
                    int by = getHeight() / 2;
                    g2.drawLine(bx, by, bx + 3, by + 4);
                    g2.drawLine(bx + 3, by + 4, bx + 9, by - 5);
                }

                g2.setColor(UI_TEXT);
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                Insets ins = getInsets();
                int tx = Math.max(24, ins.left);
                int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(getText(), tx, ty);
                g2.dispose();
            }
        };
        item.setSelected(selected);
        item.setForeground(UI_TEXT);
        item.setBackground(UI_PANEL);
        item.setFont(new Font("SansSerif", Font.PLAIN, 12));
        item.setBorder(BorderFactory.createEmptyBorder(6, 24, 6, 30));
        item.setOpaque(true);
        item.setContentAreaFilled(false);
        return item;
    }

    private JMenuItem styledItem(String text) {
        JMenuItem item = new JMenuItem(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                Color bg = isArmed() ? UI_PANEL_HOVER : UI_PANEL;
                g2.setColor(bg);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(UI_TEXT);
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                Insets ins = getInsets();
                int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(getText(), ins.left, ty);
                g2.dispose();
            }
        };
        item.setForeground(UI_TEXT);
        item.setBackground(UI_PANEL);
        item.setFont(new Font("SansSerif", Font.PLAIN, 12));
        item.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 30));
        item.setOpaque(true);
        item.setContentAreaFilled(false);
        return item;
    }

    private JMenu customMenu(String title, String[][] items, ActionListener[] actions) {
        JMenu m = styledMenu(title);
        for (int i = 0; i < items.length; i++) {
            if (items[i][0].equals("-")) {
                m.getPopupMenu().addSeparator();
                continue;
            }
            JMenuItem item = styledItem(items[i][0]);
            if (items[i][1] != null) {
                try { item.setAccelerator(KeyStroke.getKeyStroke(items[i][1])); } catch (Exception ignored) {}
            }
            if (actions[i] != null) item.addActionListener(actions[i]);
            m.add(item);
        }
        return m;
    }

    private void showAbout() {
        JOptionPane.showMessageDialog(this,
                "GhrandalCodeCity\nProfessional Software Architecture & Code Visualization Tool\n\n" +
                "GhrandalCodeCity is an industrial-oriented software visualization environment\n" +
                "for exploring the structure, metrics, architecture, and dependencies of Java systems.\n\n" +
                "The platform provides interactive software-city visualization, architecture views,\n" +
                "dependency analysis, metrics dashboards, search, filtering, and export capabilities.\n\n" +
                "It is designed to support software engineering analysis, architecture communication,\n" +
                "technical documentation, and professional presentation of complex codebases.",
                "About GhrandalCodeCity", JOptionPane.INFORMATION_MESSAGE);
    }

    // ============================================================
    // LEGEND
    // ============================================================
    private void showLegend() {
        Object[][] rows = {
            {"DISTRICTS & BUILDINGS", null, null, false},
            {"◼", new Color(0xC8A97E), "Root package (light brown)", false},
            {"▣", new Color(0xC8C8C8), "Package (gray shade = nesting)", false},
            {"▤", new Color(0x3D8BFD), "Class building (blue by LOC)", false},
            {"▮", new Color(20, 40, 120),   "Simple class (LOC ≤ 50)", false},
            {"▮", new Color(50, 110, 200),  "Medium class (50 < LOC < 150)", false},
            {"▮", new Color(150, 190, 245), "Complex class (LOC ≥ 150)", false},

            {"CLASS FEATURES (on ceiling)", null, null, false},
            {"✇", new Color(200, 25, 25),  "Interface (red fan, top-left)", false},
            {"🗼", new Color(200, 25, 25),  "Superclass (red tower, bottom-right)", false},
            {"●", new Color(245, 135, 25), "Documented class (orange, top-right)", false},

            {"ATTRIBUTES & METHODS", null, null, false},
            {"▬", new Color(135, 206, 250), "Attribute floor (sky blue)", false},
            {"▮", Color.BLACK,              "Instance attribute (black rectangle)", false},
            {"▮", new Color(200, 0, 0),     "Static attribute (red rectangle)", false},
            {"●", new Color(128, 0, 128),   "Local variable (purple circle, center)", false},
            {"●", new Color(35, 160, 70),   "Static method (green circle, left of center)", false},

            {"RELATIONSHIPS (red line + mid triangle)", null, null, false},
            {"", new Color(30, 90, 200),  "Inheritance → superclass (blue triangle)", true},
            {"", new Color(20, 20, 20),   "Composition → containing class (black triangle)", true},
            {"", new Color(35, 160, 70),  "Invocation → invoked method (green triangle)", true},
            {"", new Color(245, 135, 25), "Access → accessed attribute (orange triangle)", true}
        };

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(UI_PANEL);
        panel.setBorder(new EmptyBorder(6, 10, 6, 10));

        Font headerFont = new Font("SansSerif", Font.BOLD, 11);
        Font rowFont    = new Font("SansSerif", Font.PLAIN, 11);
        Font glyphFont  = new Font("SansSerif", Font.PLAIN, 13);

        for (Object[] row : rows) {
            String glyph = (String) row[0];
            Color color  = (Color)  row[1];
            String text  = (String) row[2];
            boolean isRelation = (Boolean) row[3];

            if (color == null) {
                JLabel h = new JLabel(glyph);
                h.setForeground(UI_TEXT);
                h.setFont(headerFont);
                h.setBorder(BorderFactory.createEmptyBorder(6, 0, 2, 0));
                h.setAlignmentX(Component.LEFT_ALIGNMENT);
                panel.add(h);
                continue;
            }

            final Color triColor = color;
            final boolean drawRel = isRelation;

            JLabel symbol = new JLabel(glyph) {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    int w = getWidth(), h = getHeight(), cy = h / 2;
                    if (drawRel) {
                        g2.setColor(new Color(200, 25, 25));
                        g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g2.drawLine(2, cy, w - 2, cy);
                        int cx = w / 2, s = 5;
                        int[] xs = { cx + s, cx - s, cx - s };
                        int[] ys = { cy, cy - s, cy + s };
                        g2.setColor(triColor);
                        g2.fillPolygon(xs, ys, 3);
                        g2.setColor(Color.DARK_GRAY);
                        g2.drawPolygon(xs, ys, 3);
                    } else {
                        g2.setColor(triColor);
                        g2.setFont(glyphFont);
                        FontMetrics fm = g2.getFontMetrics();
                        int x = (w - fm.stringWidth(getText())) / 2;
                        int y = (h + fm.getAscent() - fm.getDescent()) / 2;
                        g2.drawString(getText(), x, y);
                    }
                    g2.dispose();
                }
            };
            symbol.setPreferredSize(new Dimension(isRelation ? 34 : 18, 16));
            symbol.setOpaque(false);

            JLabel meaning = new JLabel(text);
            meaning.setForeground(UI_TEXT);
            meaning.setFont(rowFont);

            JPanel line = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            line.setOpaque(false);
            line.setAlignmentX(Component.LEFT_ALIGNMENT);
            line.add(symbol);
            line.add(meaning);
            panel.add(line);
        }

        JScrollPane sp = new JScrollPane(panel);
        sp.setPreferredSize(new Dimension(380, 500));
        sp.setBorder(BorderFactory.createLineBorder(UI_BORDER));
        sp.getViewport().setBackground(UI_PANEL);

        JOptionPane.showMessageDialog(this, sp, "Legend", JOptionPane.PLAIN_MESSAGE);
    }

    private void zoomIn()  { view.zoom = Math.min(CityView.MAX_ZOOM, view.zoom * CityView.KEY_ZOOM_STEP); view.repaint(); }
    private void zoomOut() { view.zoom = Math.max(CityView.MIN_ZOOM, view.zoom / CityView.KEY_ZOOM_STEP); view.repaint(); }

    private void searchAndFocus(String rawQuery) {
        if (view.model == null) { statusLabel.setText("  Open an XML file first."); return; }
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (query.isEmpty()) return;
        hideSuggestions();
        Ref ref = findByName(view.model, query);
        if (ref == null) { statusLabel.setText("  Not found: " + query); return; }
        detail.setText(ref.info());
        detail.setCaretPosition(0);
        statusLabel.setText("  Found: " + query);
        view.focusOn(ref);
    }

    private Ref findByName(Model model, String query) {
        if (model == null || query == null || query.trim().isEmpty()) return null;
        if (searchIndex == null) searchIndex = SearchIndex.build(model);
        Ref exact = searchIndex.exact(query);
        if (exact != null) return exact;
        List<Ref> matches = searchIndex.prefix(query, 1);
        return matches.isEmpty() ? null : matches.get(0);
    }

    private static District findDistrict(District d, String needleLower) {
        if (d.path.equalsIgnoreCase(needleLower) || d.name.equalsIgnoreCase(needleLower)) return d;
        for (District child : d.children) { District hit = findDistrict(child, needleLower); if (hit != null) return hit; }
        return null;
    }

    // ============================================================
    // SYSTEM SUMMARY
    // ============================================================
    private String systemSummary() {
        Model model = view.model;
        if (model == null) return "Open an XML file first (File → Open XML…).";
        int m = 0, a = 0;
        for (ClassInfo c : model.classes) { m += c.methods.size(); a += c.attributes.size(); }
        return "System summary\n\n" +
                "Software name:           " + model.name + "\n\n" +
                "LOC (Lines of code):     " + model.loc + "\n" +
                "NOP (Packages):          " + model.packageCount() + "\n" +
                "NOC (Classes):           " + model.classes.size() + "\n" +
                "NOM (Methods):           " + m + "\n" +
                "NOA (Attributes):        " + a;
    }

    private void openXml() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("XML files", "xml"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            Model loadedModel = XmlReader.load(chooser.getSelectedFile());
            searchIndex = SearchIndex.build(loadedModel);
            view.setModel(loadedModel);
            view.labelPackage   = false;
            view.labelClass     = false;
            view.labelAttribute = false;
            view.labelMethod    = false;
            view.labelLocal     = false;
            view.labelRelations = false;
            view.relationMask   = REL_NONE;
            setJMenuBar(createMenuBar());
            view.resetCamera();
            view.fitCity();
            view.repaint();
            detail.setText(systemSummary());
            detail.setCaretPosition(0);
            statusLabel.setText("  Loaded: " + chooser.getSelectedFile().getName());
        } catch (Exception ex) {
            AppLogger.get().log(java.util.logging.Level.WARNING, "Unable to load XML", ex);
            JOptionPane.showMessageDialog(this, ex.getMessage(), "XML error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveCurrentViewSvg() {
        if (view.model == null) {
            JOptionPane.showMessageDialog(this, "Open an XML file first.", "Nothing to save", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("ghrandal-code-city-current-view.svg"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            File file = chooser.getSelectedFile();
            if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".svg"))
                file = new File(file.getAbsolutePath() + ".svg");
            SvgExporter.writeCurrentView(view, file);
            statusLabel.setText("  Saved current city view: " + file.getName());
        } catch (Exception ex) {
            AppLogger.get().log(java.util.logging.Level.WARNING, "Unable to save current city view as SVG", ex);
            JOptionPane.showMessageDialog(this, ex.getMessage(), "SVG save error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void save(boolean pdf) {
        if (view.model == null) {
            JOptionPane.showMessageDialog(this, "Open an XML file first.", "Nothing to save", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        String ext = pdf ? "pdf" : "png";
        chooser.setSelectedFile(new File("ghrandal-code-city." + ext));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            File file = chooser.getSelectedFile();
            if (!file.getName().toLowerCase(Locale.ROOT).endsWith("." + ext))
                file = new File(file.getAbsolutePath() + "." + ext);
            if (pdf) SimplePdf.write(view.renderImage(), file);
            else ImageIO.write(view.renderImage(), "png", file);
            statusLabel.setText("  Saved: " + file.getName());
        } catch (Exception ex) {
            AppLogger.get().log(java.util.logging.Level.WARNING, "Unable to save image", ex);
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Save error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ============================================================
    // CITY VIEW
    // ============================================================




















    private static <T> String joinNames(List<T> items, java.util.function.Function<T, String> nameOf) {
        StringBuilder sb = new StringBuilder();
        int shown = Math.min(items.size(), 10);
        for (int i = 0; i < shown; i++) {
            if (i > 0) sb.append(", ");
            sb.append(nameOf.apply(items.get(i)));
        }
        if (items.size() > shown) sb.append(", … (").append(items.size() - shown).append(" more)");
        return sb.toString();
    }

















}