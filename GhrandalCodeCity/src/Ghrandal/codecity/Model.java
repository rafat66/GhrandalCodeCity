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

class Model {
    String name;
    int loc;
    District root;
    List<District> roots = new ArrayList<>();
    List<ClassInfo> classes = new ArrayList<>();
    List<Composition> compositions = new ArrayList<>();
    Map<String, ClassInfo> byFullName = new HashMap<>();
    Map<String, List<ClassInfo>> bySimpleName = new HashMap<>();
    Set<String> superClassNames = new HashSet<>();
    double width, height;
    int lowLoc, highLoc;
    private final Map<ClassInfo, Color> classTintCache = new IdentityHashMap<>();

    int packageCount() {
        int n = 0;
        for (District d : roots) n += countRealPackages(d);
        return n;
    }
    private int countRealPackages(District d) {
        int n = (d.name != null && !d.name.isEmpty()) ? 1 : 0;
        for (District c : d.children) n += countRealPackages(c);
        return n;
    }

    Color colorForLoc(int loc) {
        if (loc <= 50) return CityView.COMPLEXITY_SIMPLE;
        if (loc < 150) return CityView.COMPLEXITY_MEDIUM;
        return CityView.COMPLEXITY_COMPLEX;
    }
    Color colorFor(ClassInfo c) {
        Color cached = classTintCache.get(c);
        if (cached != null) return cached;
        Color base = colorForLoc(c.loc);
        int h = Math.abs(c.fullName().hashCode());
        int dx = (h % 25) - 12;
        int dy = ((h / 25) % 25) - 12;
        int dz = ((h / 625) % 25) - 12;
        Color tinted = new Color(clamp255(base.getRed() + dx), clamp255(base.getGreen() + dy), clamp255(base.getBlue() + dz));
        classTintCache.put(c, tinted);
        return tinted;
    }
    private static int clamp255(int v) { return Math.max(0, Math.min(255, v)); }
    static String complexityLabel(int loc) {
        if (loc <= 50) return "simple (≤50)";
        if (loc < 150) return "medium (50–150)";
        return "complex (≥150)";
    }
    ClassInfo resolveClass(String raw, ClassInfo scope) {
        if (raw == null || raw.isBlank()) return null;
        String s = clean(raw);
        ClassInfo c = byFullName.get(s);
        if (c != null) return c;
        if (scope != null) { c = byFullName.get(scope.packageName + "." + s); if (c != null) return c; }
        String simple = s.substring(s.lastIndexOf('.') + 1);
        List<ClassInfo> candidates = bySimpleName.get(simple);
        if (candidates == null || candidates.isEmpty()) return null;
        if (scope != null) for (ClassInfo cand : candidates) if (cand.packageName.equals(scope.packageName)) return cand;
        return candidates.size() == 1 ? candidates.get(0) : null;
    }
    MethodTarget resolveMethod(String raw, ClassInfo scope) {
        String s = clean(raw); if (s.isBlank()) return null;
        int dot = s.lastIndexOf('.');
        String name = dot >= 0 ? s.substring(dot + 1) : s;
        ClassInfo owner = dot >= 0 ? resolveClass(s.substring(0, dot), scope) : scope;
        if (owner != null) for (MethodInfo m : owner.methods) if (m.name.equals(name)) return new MethodTarget(owner, m);
        MethodTarget found = null;
        for (ClassInfo c : classes) for (MethodInfo m : c.methods) if (m.name.equals(name)) {
            if (found != null) return null;
            found = new MethodTarget(c, m);
        }
        return found;
    }
    AttributeTarget resolveAttribute(String raw, ClassInfo scope) {
        String s = clean(raw); if (s.isBlank()) return null;
        int dot = s.lastIndexOf('.');
        String name = dot >= 0 ? s.substring(dot + 1) : s;
        ClassInfo owner = dot >= 0 ? resolveClass(s.substring(0, dot), scope) : scope;
        if (owner != null) for (AttributeInfo a : owner.attributes) if (a.name.equals(name)) return new AttributeTarget(owner, a);
        AttributeTarget found = null;
        for (ClassInfo c : classes) for (AttributeInfo a : c.attributes) if (a.name.equals(name)) {
            if (found != null) return null;
            found = new AttributeTarget(c, a);
        }
        return found;
    }
    static String clean(String s) { int i = s.indexOf('<'); if (i >= 0) s = s.substring(0, i); return s.replace("[]", "").trim(); }
}
