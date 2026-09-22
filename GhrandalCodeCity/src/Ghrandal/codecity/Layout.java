package Ghrandal.codecity;

import java.util.*;

/**
 * Computes the 2D city coordinates used by the 3D-style renderer.
 * Layout modes change package placement while keeping class building sizes intact.
 */
class Layout {
    enum Mode {
        HIERARCHICAL("Hierarchical"),
        COMPACT("Compact"),
        WIDE("Wide Packages"),
        GRID("Package Grid"),
        LANE("Lane");

        final String label;
        Mode(String label) { this.label = label; }
    }

    private static Mode mode = Mode.HIERARCHICAL;

    private static final double OUTER_PADDING  = 1.60;
    private static final double HEADER_HEIGHT  = 0.75;
    private static final double CHILD_GAP      = 1.40;
    private static final double CLASS_GAP      = 2.00;

    static Mode mode() { return mode; }

    static void setMode(Mode newMode) {
        mode = newMode == null ? Mode.HIERARCHICAL : newMode;
    }

    static void layout(Model m) {
        if (m == null || m.root == null) return;
        sizeDistrict(m.root);
        m.root.x = 0;
        m.root.y = 0;
        m.root.width  = Math.max(4.0, m.root.width);
        m.root.height = Math.max(4.0, m.root.height);
        m.width  = m.root.width;
        m.height = m.root.height;
        placeDistrict(m.root);
    }

    private static double outerPadding() {
        return mode == Mode.COMPACT ? 1.05 : OUTER_PADDING;
    }

    private static double childGap() {
        return mode == Mode.COMPACT ? 0.65 : CHILD_GAP;
    }

    private static double classGap() {
        return mode == Mode.COMPACT ? 1.10 : CLASS_GAP;
    }

    private static void sizeDistrict(District d) {
        for (District child : d.children) sizeDistrict(child);

        double padding = outerPadding();
        double gap = classGap();
        boolean rootContainer = d.parent == null;
        double headerHeight = rootContainer ? 0.0 : HEADER_HEIGHT;
        int classCount = d.classes.size();
        double classAreaWidth = 0, classAreaHeight = 0;
        if (classCount > 0) {
            double maxClassSize = 1.0;
            for (ClassInfo c : d.classes) maxClassSize = Math.max(maxClassSize, c.baseSide());
            int columns = mode == Mode.LANE ? classCount : Math.max(1, (int) Math.ceil(Math.sqrt(classCount)));
            int rows = mode == Mode.LANE ? 1 : (int) Math.ceil(classCount / (double) columns);
            double cell = maxClassSize + gap;
            classAreaWidth = columns * cell - gap;
            classAreaHeight = rows * cell - gap;
        }

        double childrenWidth = 0, childrenHeight = 0;
        if (mode == Mode.WIDE || mode == Mode.LANE) {
            for (int i = 0; i < d.children.size(); i++) {
                District child = d.children.get(i);
                childrenWidth += child.width;
                childrenHeight = Math.max(childrenHeight, child.height);
                if (i < d.children.size() - 1) childrenWidth += childGap();
            }
        } else if (mode == Mode.GRID) {
            int count = d.children.size();
            if (count > 0) {
                int columns = Math.max(1, (int) Math.ceil(Math.sqrt(count)));
                int rows = (int) Math.ceil(count / (double) columns);
                double[] colWidths = new double[columns];
                double[] rowHeights = new double[rows];
                for (int i = 0; i < count; i++) {
                    District child = d.children.get(i);
                    int col = i % columns, row = i / columns;
                    colWidths[col] = Math.max(colWidths[col], child.width);
                    rowHeights[row] = Math.max(rowHeights[row], child.height);
                }
                for (double w : colWidths) childrenWidth += w;
                for (double h : rowHeights) childrenHeight += h;
                childrenWidth += childGap() * Math.max(0, columns - 1);
                childrenHeight += childGap() * Math.max(0, rows - 1);
            }
        } else {
            for (int i = 0; i < d.children.size(); i++) {
                District child = d.children.get(i);
                childrenWidth = Math.max(childrenWidth, child.width);
                childrenHeight += child.height;
                if (i < d.children.size() - 1) childrenHeight += childGap();
            }
        }

        double contentWidth = Math.max(classAreaWidth, childrenWidth);
        double contentHeight = 0;
        if (classAreaHeight > 0) contentHeight += classAreaHeight;
        if (classAreaHeight > 0 && childrenHeight > 0) contentHeight += childGap();
        if (childrenHeight > 0) contentHeight += childrenHeight;
        contentWidth = Math.max(contentWidth, 4.0);
        contentHeight = Math.max(contentHeight, 2.0);

        d.width = padding * 2 + contentWidth;
        d.height = headerHeight + padding + contentHeight + padding;
    }

    private static void placeDistrict(District d) {
        double padding = outerPadding();
        double gap = classGap();
        boolean rootContainer = d.parent == null;
        double contentLeft = d.x + padding;
        double contentTop = d.y + (rootContainer ? 0.0 : HEADER_HEIGHT) + padding;
        int classCount = d.classes.size();
        double classAreaHeight = 0;

        if (classCount > 0) {
            double maxClassSize = 1.0;
            for (ClassInfo c : d.classes) maxClassSize = Math.max(maxClassSize, c.baseSide());
            int columns = mode == Mode.LANE ? classCount : Math.max(1, (int) Math.ceil(Math.sqrt(classCount)));
            int rows = mode == Mode.LANE ? 1 : (int) Math.ceil(classCount / (double) columns);
            double cell = maxClassSize + gap;
            for (int i = 0; i < classCount; i++) {
                ClassInfo c = d.classes.get(i);
                int row = i / columns, col = i % columns;
                c.x = contentLeft + col * cell + (cell - gap - c.baseSide()) / 2.0;
                c.y = contentTop + row * cell + (cell - gap - c.baseSide()) / 2.0;
            }
            classAreaHeight = rows * cell - gap;
        }

        double childTop = contentTop + classAreaHeight;
        if (classCount > 0 && !d.children.isEmpty()) childTop += childGap();

        if (mode == Mode.WIDE || mode == Mode.LANE) {
            double childLeft = contentLeft;
            for (District child : d.children) {
                child.x = childLeft;
                child.y = childTop;
                placeDistrict(child);
                childLeft += child.width + childGap();
            }
        } else if (mode == Mode.GRID) {
            int count = d.children.size();
            if (count > 0) {
                int columns = Math.max(1, (int) Math.ceil(Math.sqrt(count)));
                double[] colWidths = new double[columns];
                double[] rowHeights = new double[(int) Math.ceil(count / (double) columns)];
                for (int i = 0; i < count; i++) {
                    District child = d.children.get(i);
                    int col = i % columns, row = i / columns;
                    colWidths[col] = Math.max(colWidths[col], child.width);
                    rowHeights[row] = Math.max(rowHeights[row], child.height);
                }
                double y = childTop;
                for (int row = 0; row < rowHeights.length; row++) {
                    double x = contentLeft;
                    for (int col = 0; col < columns; col++) {
                        int i = row * columns + col;
                        if (i >= count) break;
                        District child = d.children.get(i);
                        child.x = x;
                        child.y = y;
                        placeDistrict(child);
                        x += colWidths[col] + childGap();
                    }
                    y += rowHeights[row] + childGap();
                }
            }
        } else {
            for (District child : d.children) {
                double childLeft = d.x + (d.width - child.width) / 2.0;
                child.x = childLeft;
                child.y = childTop;
                placeDistrict(child);
                childTop += child.height + childGap();
            }
        }
    }
}
