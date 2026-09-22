package Ghrandal.codecity;

import java.awt.*;
import java.awt.geom.*;

/** Lightweight vector logo for the GhrandalCodeCity welcome screen. */
final class SoftwareCityLogo {
    private SoftwareCityLogo() {}

    static void paint(Graphics2D g, int centerX, int topY, double scale) {
        Graphics2D x = (Graphics2D) g.create();
        x.translate(centerX, topY);
        x.scale(scale, scale);
        x.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        x.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        // Ground / city base.
        x.setColor(new Color(0x3D8BFD));
        x.fillRoundRect(-94, 56, 188, 8, 4, 4);

        // Stylized city buildings.
        drawBuilding(x, -82, 8, 28, 48, new Color(0x5CA0FF));
        drawBuilding(x, -50, -8, 32, 64, new Color(0x3D8BFD));
        drawBuilding(x, -14, 14, 30, 42, new Color(0x6BB7FF));
        drawBuilding(x, 20, -20, 34, 76, new Color(0x3D8BFD));
        drawBuilding(x, 58, 4, 28, 52, new Color(0x5CA0FF));

        // Connected architecture nodes.
        x.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        x.setColor(new Color(0x9ED3FF));
        x.draw(new Line2D.Double(-35, -31, 37, -43));
        x.draw(new Line2D.Double(37, -43, 72, -12));
        x.draw(new Line2D.Double(-35, -31, -50, -8));
        for (Point2D p : new Point2D[]{new Point2D.Double(-35,-31), new Point2D.Double(37,-43), new Point2D.Double(72,-12)}) {
            x.fill(new Ellipse2D.Double(p.getX()-4, p.getY()-4, 8, 8));
        }

        // Window/grid details.
        x.setStroke(new BasicStroke(1.2f));
        x.setColor(new Color(0xDCEEFF));
        for (int bx : new int[]{-76,-44,-8,28,66}) {
            int h = (bx == 28 ? 76 : bx == -44 ? 64 : bx == -8 ? 42 : bx == -76 ? 48 : 52);
            for (int yy = 8; yy < h-4; yy += 12) x.drawLine(bx, yy, bx + 4, yy);
        }

        x.setColor(new Color(0xE8EBEF));
        x.setFont(new Font("SansSerif", Font.BOLD, 18));
        String name = "GhrandalCodeCity";
        FontMetrics fm = x.getFontMetrics();
        x.drawString(name, -fm.stringWidth(name)/2, 94);
        x.setFont(new Font("SansSerif", Font.PLAIN, 10));
        x.setColor(new Color(0x9AA3B0));
        String sub = "Software Architecture Visualization";
        fm = x.getFontMetrics();
        x.drawString(sub, -fm.stringWidth(sub)/2, 111);
        x.dispose();
    }

    private static void drawBuilding(Graphics2D g, int x, int y, int w, int h, Color c) {
        g.setColor(c);
        g.fillRoundRect(x, y, w, h, 4, 4);
        g.setColor(c.brighter());
        g.drawRoundRect(x, y, w, h, 4, 4);
    }
}
