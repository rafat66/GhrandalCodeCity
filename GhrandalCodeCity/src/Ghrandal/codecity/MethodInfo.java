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

class MethodInfo {
    String name, returnType, parameters, accessLevel;
    boolean isStatic;
    int comments;
    List<String> locals, localsTypes, invocations, attributeAccesses, exceptions;
    MethodInfo(String name, String returnType, String parameters, boolean isStatic, String accessLevel, int comments,
            List<String> locals, List<String> localsTypes, List<String> invocations,
            List<String> accesses, List<String> exceptions) {
        this.name = name; this.returnType = returnType; this.parameters = parameters;
        this.isStatic = isStatic; this.accessLevel = accessLevel; this.comments = comments;
        this.locals = locals; this.localsTypes = localsTypes;
        this.invocations = invocations; this.attributeAccesses = accesses; this.exceptions = exceptions;
    }
}
