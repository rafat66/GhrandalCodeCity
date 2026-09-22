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

class ClassInfo {
    String name, packageName, superclass, accessLevel;
    int loc, comments, noc;
    boolean isInterface;
    List<AttributeInfo> attributes;
    List<MethodInfo> methods;
    List<String> compositionTargets = new ArrayList<>();
    District district;
    double x, y;
    ClassInfo(String name, String packageName, int loc, int noc, boolean isInterface, String superclass,
              String accessLevel, int comments, List<AttributeInfo> attributes, List<MethodInfo> methods) {
        this.name = name; this.packageName = packageName; this.loc = loc; this.noc = noc;
        this.isInterface = isInterface; this.superclass = superclass; this.accessLevel = accessLevel;
        this.comments = comments; this.attributes = attributes; this.methods = methods;
    }
    String fullName() { return packageName == null || packageName.isBlank() ? name : packageName + "." + name; }
    double baseSide() {
        int a = attributes.size();
        if (a <= 0) return 1.0;
        return Math.max(1.0, Math.ceil(Math.sqrt(a / 2.0)));
    }
    String baseSideLabel() {
        int a = attributes.size();
        int si = (int) Math.round(baseSide());
        if (a <= 0) return "1 × 1 (default)";
        return si + " × " + si + " (A=" + a + ", S = ⌈√(A/2)⌉ = " + si + ")";
    }
    double buildingHeight() {
        double base = CityView.BASE_PLINTH_H;
        if (!attributes.isEmpty()) base += CityView.ATTR_FLOOR_BAND_H;
        base += methods.size() * CityView.METHOD_FLOOR_SPACING;
        return base;
    }
    String buildingHeightLabel() { return methods.size() + " floor(s)"; }
}
