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

record ClassRef(ClassInfo c) implements Ref {
    public String id() { return "C:" + c.fullName(); }
    public String info() {
        StringBuilder sb = new StringBuilder();
        sb.append("CLASS\n\n");
        sb.append("name         : ").append(c.name).append("\n");
        sb.append("package      : ").append(c.packageName == null || c.packageName.isBlank() ? "(default)" : c.packageName).append("\n");
        sb.append("access       : ").append(c.accessLevel).append("\n");
        sb.append("superclass   : ").append(c.superclass == null || c.superclass.isBlank() ? "(none)" : c.superclass).append("\n");
        sb.append("interface    : ").append(c.isInterface).append("\n");
        sb.append("LOC          : ").append(c.loc).append("  (").append(Model.complexityLabel(c.loc)).append(")\n");
        sb.append("comments     : ").append(c.comments).append("\n");
        sb.append("NOC          : ").append(c.noc).append("\n");
        sb.append("base         : ").append(c.baseSideLabel()).append("\n");
        sb.append("height       : ").append(c.buildingHeightLabel()).append("\n");
        sb.append("attributes (").append(c.attributes.size()).append("): ")
          .append(c.attributes.isEmpty() ? "none" : ModelUtils.joinNames(c.attributes, a -> a.name)).append("\n");
        sb.append("methods (").append(c.methods.size()).append("): ")
          .append(c.methods.isEmpty() ? "none" : ModelUtils.joinNames(c.methods, m -> m.name));
        if (!c.compositionTargets.isEmpty()) {
            sb.append("\ncompositions : ").append(String.join(", ", c.compositionTargets));
        }
        return sb.toString();
    }
}
