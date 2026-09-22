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

class XmlReader {
    static Model load(File file) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        try { f.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true); } catch (Exception ignored) {}
        try { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); } catch (Exception ignored) {}
        try { f.setFeature("http://xml.org/sax/features/external-general-entities", false); } catch (Exception ignored) {}
        try { f.setFeature("http://xml.org/sax/features/external-parameter-entities", false); } catch (Exception ignored) {}
        try { f.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, ""); } catch (Exception ignored) {}
        try { f.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, ""); } catch (Exception ignored) {}
        f.setXIncludeAware(false); f.setExpandEntityReferences(false);
        Document document = f.newDocumentBuilder().parse(file);
        document.getDocumentElement().normalize();
        Model model = new Model();
        model.name = attr(document.getDocumentElement(), "ProjectName", file.getName());
        model.loc  = integer(document.getDocumentElement(), "ProjectLOC", 0);
        Map<String, District> districts = new LinkedHashMap<>();
        NodeList packages = document.getElementsByTagName("Package");
        for (int i = 0; i < packages.getLength(); i++) {
            Element p = (Element) packages.item(i);
            String packageName = attr(p, "PackageName", "default");
            District district = getDistrict(model, districts, packageName);
            NodeList classNodes = p.getElementsByTagName("Class");
            for (int j = 0; j < classNodes.getLength(); j++) {
                Element ce = (Element) classNodes.item(j);
                String declaredPackage = attr(ce, "DeclaredPackage", "");
                if (declaredPackage.isEmpty() || declaredPackage.equals(packageName)) {
                    ClassInfo c = readClass(ce, packageName);
                    c.district = district;
                    district.classes.add(c);
                    model.classes.add(c);
                    model.byFullName.put(c.fullName(), c);
                    model.bySimpleName.computeIfAbsent(c.name, k -> new ArrayList<>()).add(c);
                }
            }
        }
        District cityRoot = new District("", "", 0);
        for (District top : model.roots) { top.parent = cityRoot; cityRoot.children.add(top); }
        model.root = cityRoot;
        cityRoot.assignLevels(0);
        model.roots = new ArrayList<>();
        model.roots.add(cityRoot);
        for (ClassInfo c : model.classes) {
            if (c.superclass != null && !c.superclass.isBlank()) {
                ClassInfo parent = model.resolveClass(c.superclass, c);
                if (parent != null) model.superClassNames.add(parent.fullName());
            }
        }
        for (ClassInfo owner : model.classes) {
            for (String encoded : owner.compositionTargets) {
                String[] parts = encoded.split("\\|", 2);
                if (parts.length == 2) {
                    model.compositions.add(new Composition(parts[0], parts[1], owner));
                }
            }
        }
        int[] locs = model.classes.stream().mapToInt(c -> c.loc).sorted().toArray();
        model.lowLoc = locs.length == 0 ? 0 : locs[locs.length / 3];
        model.highLoc = locs.length == 0 ? 1 : locs[(locs.length * 2) / 3];
        return model;
    }
    private static District getDistrict(Model model, Map<String, District> map, String path) {
        if (map.containsKey(path)) return map.get(path);
        String[] parts = path.split("\\.");
        StringBuilder current = new StringBuilder();
        District parent = null;
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) current.append('.');
            current.append(parts[i]);
            String key = current.toString();
            District d = map.get(key);
            if (d == null) {
                d = new District(parts[i], key, i);
                map.put(key, d);
                if (parent == null) model.roots.add(d);
                else { d.parent = parent; if (!parent.children.contains(d)) parent.children.add(d); }
            }
            parent = d;
        }
        return map.get(path);
    }

    private static int classLevelCommentCount(Element classEl) {
        int count = 0;
        NodeList children = classEl.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE && "Comments".equals(n.getNodeName())) {
                count += ((Element) n).getElementsByTagName("Comment").getLength();
            }
        }
        return count;
    }

    private static ClassInfo readClass(Element e, String packageName) {
        List<AttributeInfo> attrs = new ArrayList<>();
        NodeList attrNodes = e.getElementsByTagName("Attribute");
        for (int i = 0; i < attrNodes.getLength(); i++) {
            Element a = (Element) attrNodes.item(i);
            attrs.add(new AttributeInfo(attr(a, "AttributeName", ""), attr(a, "AttributeType", ""),
                    bool(a, "isStaticAttribute"), attr(a, "AttributeAccessLevel", "package")));
        }
        List<MethodInfo> methods = new ArrayList<>();
        NodeList methodNodes = e.getElementsByTagName("Method");
        for (int i = 0; i < methodNodes.getLength(); i++) {
            Element me = (Element) methodNodes.item(i);
            List<String> params = new ArrayList<>();
            NodeList paramNodes = me.getElementsByTagName("Parameter");
            for (int j = 0; j < paramNodes.getLength(); j++) {
                Element p = (Element) paramNodes.item(j);
                params.add(attr(p, "ParameterType", "") + " " + attr(p, "ParameterName", ""));
            }
            List<String> locals = new ArrayList<>();
            List<String> localsTypes = new ArrayList<>();
            NodeList localNodes = me.getElementsByTagName("LocalVariable");
            for (int j = 0; j < localNodes.getLength(); j++) {
                Element l = (Element) localNodes.item(j);
                locals.add(attr(l, "LocalVariableName", ""));
                localsTypes.add(attr(l, "LocalVariableType", ""));
            }
            LinkedHashSet<String> invocationSet = new LinkedHashSet<>();
            NodeList invNodes = me.getElementsByTagName("MethodInvocation");
            for (int j = 0; j < invNodes.getLength(); j++) {
                invocationSet.add(attr((Element) invNodes.item(j), "MethodInvocationName", ""));
            }
            List<String> invocations = new ArrayList<>(invocationSet);

            LinkedHashSet<String> accessSet = new LinkedHashSet<>();
            NodeList accNodes = me.getElementsByTagName("AttributeAccess");
            for (int j = 0; j < accNodes.getLength(); j++) {
                accessSet.add(attr((Element) accNodes.item(j), "AttributeAccessName", ""));
            }
            List<String> accesses = new ArrayList<>(accessSet);

            List<String> exceptions = new ArrayList<>();
            NodeList excNodes = me.getElementsByTagName("MethodException");
            for (int j = 0; j < excNodes.getLength(); j++) exceptions.add(attr((Element) excNodes.item(j), "ExceptionName", ""));

            methods.add(new MethodInfo(attr(me, "MethodName", ""), attr(me, "MethodReturnType", "void"),
                    String.join(", ", params), bool(me, "isStaticMethod"), attr(me, "MethodAccessLevel", "package"),
                    commentCount(me), locals, localsTypes, invocations, accesses, exceptions));
        }

        String declaringClass = attr(e, "ClassName", "");
        List<String> compositions = new ArrayList<>();
        NodeList compNodes = e.getElementsByTagName("Composition");
        for (int i = 0; i < compNodes.getLength(); i++) {
            Element comp = (Element) compNodes.item(i);
            String fieldType = attr(comp, "ClassName", "");
            if (fieldType.isBlank()) fieldType = attr(comp, "ContainedClass", "");
            if (fieldType.isBlank()) fieldType = attr(comp, "ContainedClassName", "");
            if (fieldType.isBlank()) continue;
            if (declaringClass.isBlank()) continue;
            compositions.add(fieldType + "|" + declaringClass);
        }

        int noc = integer(e, "NOC", 0);
        ClassInfo c = new ClassInfo(attr(e, "ClassName", ""), packageName, integer(e, "LOC", 0), noc,
                bool(e, "isInterface"), attr(e, "Superclass", ""), attr(e, "classAccessLevel", "package"),
                classLevelCommentCount(e), attrs, methods);
        c.compositionTargets = compositions;
        return c;
    }
    private static int commentCount(Element e) { return e.getElementsByTagName("Comment").getLength(); }
    private static boolean bool(Element e, String name) { return Boolean.parseBoolean(attr(e, name, "false")); }
    private static int integer(Element e, String name, int def) {
        try { return Integer.parseInt(attr(e, name, String.valueOf(def))); } catch (NumberFormatException ex) { return def; }
    }
    private static String attr(Element e, String name, String def) {
        String s = e.getAttribute(name);
        return s == null || s.isBlank() ? def : s;
    }
}
