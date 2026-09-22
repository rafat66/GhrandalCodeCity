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

class Transform {
    final double screenX, screenY, scale, yaw, pitch;
    Transform(double screenX, double screenY, double zoom, double yaw, double pitch) {
        this.screenX = screenX; this.screenY = screenY;
        this.scale = 42 * zoom; this.yaw = yaw; this.pitch = pitch;
    }
    Point2D project(double x, double y, double z) {
        double u = x * Math.cos(yaw) - y * Math.sin(yaw);
        double v = x * Math.sin(yaw) + y * Math.cos(yaw);
        return new Point2D.Double(screenX + u * scale, screenY + (v * Math.sin(pitch) - z * Math.cos(pitch)) * scale);
    }
}
