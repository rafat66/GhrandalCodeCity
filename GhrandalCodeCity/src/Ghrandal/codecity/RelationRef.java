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

record RelationRef(String kind, String source, String target, String direction, String summary)
        implements Ref {
    public String id() { return "REL:" + kind + ":" + source + "->" + target; }
    public String info() {
        return "RELATIONSHIP\n\n"
                + "type       : " + kind + "\n"
                + "source     : " + source + "\n"
                + "target     : " + target + "\n"
                + "direction  : " + direction + "\n\n"
                + summary;
    }
}
