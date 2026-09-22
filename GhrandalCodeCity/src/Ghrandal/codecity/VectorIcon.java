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

class VectorIcon implements javax.swing.Icon {
    final IconType type; final int size; final Color color;
    VectorIcon(IconType type, int size, Color color) { this.type = type; this.size = size; this.color = color; }

    @Override public int getIconWidth()  { return size; }
    @Override public int getIconHeight() { return size; }

    @Override
    public void paintIcon(Component c, Graphics g0, int x, int y) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.translate(x, y);
        g.setColor(color);
        g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int s = size;
        switch (type) {
            case OPEN: {
                Path2D p = new Path2D.Double();
                p.moveTo(3, 2);
                p.lineTo(s - 6, 2);
                p.lineTo(s - 2, 6);
                p.lineTo(s - 2, s - 2);
                p.lineTo(3, s - 2);
                p.closePath();
                g.draw(p);
                g.draw(new Line2D.Double(s - 6, 2, s - 6, 6));
                g.draw(new Line2D.Double(s - 6, 6, s - 2, 6));
                break;
            }
            case PNG:
            case PDF:
            case SVG:
            case HTML: {
                Path2D p = new Path2D.Double();
                p.moveTo(3, 2);
                p.lineTo(s - 6, 2);
                p.lineTo(s - 2, 6);
                p.lineTo(s - 2, s - 2);
                p.lineTo(3, s - 2);
                p.closePath();
                g.draw(p);
                g.draw(new Line2D.Double(s - 6, 2, s - 6, 6));
                g.draw(new Line2D.Double(s - 6, 6, s - 2, 6));
                g.setFont(new Font("SansSerif", Font.BOLD, 6));
                String t = (type == IconType.PNG) ? "IMG" : (type == IconType.PDF ? "PDF" : (type == IconType.SVG ? "SVG" : "HTML"));
                FontMetrics fm = g.getFontMetrics();
                g.drawString(t, (s - fm.stringWidth(t)) / 2, s - 5);
                break;
            }
            case ZOOM_IN:
            case ZOOM_OUT: {
                g.draw(new Ellipse2D.Double(2, 2, s - 9, s - 9));
                g.draw(new Line2D.Double(s - 7, s - 7, s - 1, s - 1));
                int cx = (int) (2 + (s - 9) / 2.0);
                int cy = (int) (2 + (s - 9) / 2.0);
                g.draw(new Line2D.Double(cx - 3, cy, cx + 3, cy));
                if (type == IconType.ZOOM_IN) g.draw(new Line2D.Double(cx, cy - 3, cx, cy + 3));
                break;
            }
            case FIT: {
                g.draw(new Rectangle2D.Double(2, 2, s - 4, s - 4));
                g.draw(new Rectangle2D.Double(6, 6, s - 12, s - 12));
                break;
            }
            case HOME: {
                Path2D p = new Path2D.Double();
                p.moveTo(s / 2.0, 2);
                p.lineTo(s - 3, s / 2.0);
                p.lineTo(s - 6, s / 2.0);
                p.lineTo(s - 6, s - 3);
                p.lineTo(6, s - 3);
                p.lineTo(6, s / 2.0);
                p.lineTo(3, s / 2.0);
                p.closePath();
                g.draw(p);
                break;
            }
            case SEARCH: {
                g.draw(new Ellipse2D.Double(3, 3, s - 8, s - 8));
                g.draw(new Line2D.Double(s - 5, s - 5, s - 2, s - 2));
                break;
            }
        }
        g.dispose();
    }
}
