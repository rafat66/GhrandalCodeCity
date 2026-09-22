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

class SimplePdf {
    static void write(BufferedImage image, File file) throws IOException {
        ByteArrayOutputStream jpg = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", jpg);
        byte[] jpeg = jpg.toByteArray();
        int w = image.getWidth(), h = image.getHeight();
        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();
        put(pdf, "%PDF-1.4\n");
        object(pdf, offsets, 1, "<< /Type /Catalog /Pages 2 0 R >>");
        object(pdf, offsets, 2, "<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
        object(pdf, offsets, 3, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + w + " " + h + "] /Resources << /XObject << /I 5 0 R >> >> /Contents 4 0 R >>");
        byte[] content = ("q " + w + " 0 0 " + h + " 0 0 cm /I Do Q").getBytes(StandardCharsets.US_ASCII);
        stream(pdf, offsets, 4, "<< /Length " + content.length + " >>", content);
        stream(pdf, offsets, 5, "<< /Type /XObject /Subtype /Image /Width " + w + " /Height " + h
                + " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length " + jpeg.length + " >>", jpeg);
        int xref = pdf.size();
        put(pdf, "xref\n0 6\n0000000000 65535 f \n");
        for (int off : offsets) put(pdf, String.format(Locale.ROOT, "%010d 00000 n \n", off));
        put(pdf, "trailer << /Size 6 /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n");
        Files.write(file.toPath(), pdf.toByteArray());
    }
    static void object(ByteArrayOutputStream out, List<Integer> offsets, int number, String body) throws IOException {
        offsets.add(out.size()); put(out, number + " 0 obj\n" + body + "\nendobj\n");
    }
    static void stream(ByteArrayOutputStream out, List<Integer> offsets, int number, String header, byte[] data) throws IOException {
        offsets.add(out.size()); put(out, number + " 0 obj\n" + header + "\nstream\n");
        out.write(data); put(out, "\nendstream\nendobj\n");
    }
    static void put(ByteArrayOutputStream out, String text) throws IOException {
        out.write(text.getBytes(StandardCharsets.US_ASCII));
    }
}
