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

class District {
    String name, path; int level;
    double x, y, width, height;
    District parent;
    List<District> children = new ArrayList<>();
    List<ClassInfo> classes = new ArrayList<>();
    District(String name, String path, int level) { this.name = name; this.path = path; this.level = level; }
    int classCount() { int n = classes.size(); for (District d : children) n += d.classCount(); return n; }
    void assignLevels(int level) { this.level = level; for (District child : children) child.assignLevels(level + 1); }
}
