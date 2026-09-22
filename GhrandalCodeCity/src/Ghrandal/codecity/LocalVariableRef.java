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

record LocalVariableRef(ClassInfo c, MethodInfo method, int index) implements Ref {
    public String id() { return "LV:" + c.fullName() + ":" + method.name + ":" + index; }
    public String info() {
        String name = (index >= 0 && index < method.locals.size()) ? method.locals.get(index) : "(unknown)";
        String type = (index >= 0 && index < method.localsTypes.size()) ? method.localsTypes.get(index) : "(unknown)";
        return "LOCAL VARIABLE\n\n"
                + "name           : " + name + "\n"
                + "type           : " + type + "\n"
                + "containing     : " + method.name + "()\n"
                + "in class       : " + c.fullName();
    }
}
