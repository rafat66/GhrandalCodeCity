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

record MethodRef(ClassInfo c, MethodInfo m) implements Ref {
    public String id() { return "M:" + c.fullName() + ":" + m.name; }
    public String info() {
        return "METHOD\n\n"
                + "name            : " + m.name + "\n"
                + "class           : " + c.fullName() + "\n"
                + "access          : " + m.accessLevel + "\n"
                + "return type     : " + m.returnType + "\n"
                + "parameters      : " + (m.parameters.isEmpty() ? "(none)" : m.parameters) + "\n"
                + "static          : " + m.isStatic + "\n"
                + "comments        : " + m.comments + "\n"
                + "local variables : " + (m.locals.isEmpty() ? "none" : String.join(", ", m.locals)) + "\n"
                + "attr. accesses  : " + (m.attributeAccesses.isEmpty() ? "none" : String.join(", ", m.attributeAccesses)) + "\n"
                + "invocations     : " + (m.invocations.isEmpty() ? "none" : String.join(", ", m.invocations));
    }
}
