package Ghrandal.codecity;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import javax.imageio.ImageIO;

/** SVG export utilities.
 *
 * The user-facing "Save as SVG" command captures the CURRENT CityView: 
 * current zoom, pan, camera angle, visible elements, labels and relations.
 * The rendered view is embedded as a PNG inside a standards-compliant SVG
 * so the exported SVG is visually identical to what is on screen.
 */
final class SvgExporter {
    private SvgExporter() {}

    /** Save exactly the current viewport of the city as an SVG image. */
    static void writeCurrentView(CityView view, File file) throws IOException {
        if (view == null) throw new IOException("No city view available.");
        if (file == null) throw new IOException("No output file selected.");
        writeImageAsSvg(view.renderImage(), file, "GhrandalCodeCity — Visual Architecture");
    }

    /** Export a rendered Swing image as a publication-friendly SVG while preserving the exact visual appearance. */
    static void writeImageAsSvg(BufferedImage image, File file, String title) throws IOException {
        if (image == null) throw new IOException("No image available.");
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", png)) throw new IOException("PNG encoder is not available.");
        String encoded = Base64.getEncoder().encodeToString(png.toByteArray());
        int width=image.getWidth(), height=image.getHeight();
        String safeTitle=esc(title);
        StringBuilder s=new StringBuilder(encoded.length()+700);
        s.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        s.append("<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" width=\"").append(width).append("\" height=\"").append(height).append("\" viewBox=\"0 0 ").append(width).append(' ').append(height).append("\">\n");
        s.append("  <title>").append(safeTitle).append("</title>\n");
        s.append("  <desc>GhrandalCodeCity visual architecture export; the rendered architecture is embedded as PNG data so appearance is preserved exactly.</desc>\n");
        s.append("  <image x=\"0\" y=\"0\" width=\"").append(width).append("\" height=\"").append(height).append("\" preserveAspectRatio=\"none\" href=\"data:image/png;base64,").append(encoded).append("\"/>\n");
        s.append("</svg>\n");
        Files.writeString(file.toPath(),s.toString(),StandardCharsets.UTF_8);
    }

    /**
     * Export the architecture as a simple SVG summary. Kept for the
     * Architecture menu and CLI; this is intentionally different from the
     * user-facing current-view export above.
     */
    static void write(Model m, File file) throws IOException {
        if (m == null) throw new IOException("No model loaded.");
        int width = 1600, height = Math.max(900, 120 + m.classes.size() * 28);
        StringBuilder s = new StringBuilder();
        s.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"")
         .append(width).append("\" height=\"").append(height)
         .append("\" viewBox=\"0 0 ").append(width).append(' ').append(height).append("\">\n");
        s.append("<rect width=\"100%\" height=\"100%\" fill=\"#0e1116\"/>\n");
        s.append("<text x=\"30\" y=\"42\" fill=\"#e8ebef\" font-family=\"sans-serif\" font-size=\"24\" font-weight=\"bold\">")
         .append(esc(m.name)).append(" — Architecture Overview</text>\n");
        int y = 90;
        for (ClassInfo c : m.classes) {
            int w = Math.min(900, Math.max(220, 180 + c.loc * 2));
            String color = hex(m.colorFor(c));
            s.append("<rect x=\"30\" y=\"").append(y).append("\" width=\"")
             .append(w).append("\" height=\"22\" rx=\"4\" fill=\"")
             .append(color).append("\"/>\n");
            s.append("<text x=\"40\" y=\"").append(y + 16)
             .append("\" fill=\"#ffffff\" font-family=\"sans-serif\" font-size=\"12\">")
             .append(esc(c.fullName())).append(" — LOC ").append(c.loc)
             .append(" — M ").append(c.methods.size()).append(" — A ").append(c.attributes.size())
             .append("</text>\n");
            y += 28;
            if (y > height - 30) break;
        }
        s.append("</svg>\n");
        Files.writeString(file.toPath(), s.toString(), StandardCharsets.UTF_8);
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String hex(java.awt.Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }
}
