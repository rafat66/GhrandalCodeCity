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

record DistrictRef(District d) implements Ref {
    public String id() { return "D:" + d.path; }
    public String info() {
        StringBuilder sb = new StringBuilder();
        boolean isRoot = (d.level == 0) || d.name == null || d.name.isEmpty();
        sb.append(isRoot ? "SOFTWARE\n\n" : "PACKAGE\n\n");
        sb.append("name          : ").append(d.name.isEmpty() ? "(root city)" : d.name).append("\n");
        sb.append("full path     : ").append(d.path.isEmpty() ? "(root)" : d.path).append("\n");
        sb.append("nesting level : ").append(d.level).append("\n");
        sb.append("parent        : ").append(d.parent == null ? "(none)" : d.parent.path).append("\n");
        sb.append("child packages: ").append(d.children.isEmpty() ? "none" : ModelUtils.joinNames(d.children, ch -> ch.name)).append("\n");
        sb.append("NOC           : ").append(d.classCount()).append("\n");
        sb.append("classes (").append(d.classes.size()).append(" direct): ")
          .append(d.classes.isEmpty() ? "none" : ModelUtils.joinNames(d.classes, cl -> cl.name));
        return sb.toString();
    }
}
