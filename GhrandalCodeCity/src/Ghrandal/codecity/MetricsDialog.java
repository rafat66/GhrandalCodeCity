package Ghrandal.codecity;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.util.*;

/** Full-screen metrics dashboard with class search and an explicit OK button. */
final class MetricsDialog extends JDialog {
    private static final long serialVersionUID = 1L;
    private final JTable table;
    private final TableRowSorter<DefaultTableModel> sorter;

    MetricsDialog(Window owner, Model model) {
        super(owner, "GhrandalCodeCity — Metrics Dashboard", ModalityType.MODELESS);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setFullScreenBounds();

        MetricsReport r = new MetricsReport(model);
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(BorderFactory.createEmptyBorder(14, 14, 12, 14));
        root.setBackground(CodeCityApp.UI_PANEL);

        JPanel top = new JPanel(new BorderLayout(8, 8));
        top.setOpaque(false);

        JButton exportHtml = new JButton("Export Report as HTML", new VectorIcon(IconType.HTML, 20, Color.WHITE));
        exportHtml.setToolTipText("Export the metrics dashboard report as an HTML file");
        exportHtml.setFocusable(false);
        exportHtml.setForeground(Color.WHITE);
        exportHtml.setBackground(CodeCityApp.UI_BG_DARKER);
        exportHtml.setBorder(BorderFactory.createEmptyBorder(7, 10, 7, 10));
        exportHtml.setCursor(new Cursor(Cursor.HAND_CURSOR));
        exportHtml.addActionListener(e -> exportHtmlReport(r));
        JPanel exportPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        exportPanel.setOpaque(false);
        exportPanel.add(exportHtml);
        top.add(exportPanel, BorderLayout.NORTH);

        JTextArea summary = new JTextArea(summary(r));
        summary.setEditable(false);
        summary.setRows(4);
        summary.setFont(new Font(Font.MONOSPACED, Font.BOLD, 13));
        summary.setBackground(CodeCityApp.UI_BG_DARKER);
        summary.setForeground(Color.WHITE);
        summary.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        top.add(summary, BorderLayout.CENTER);

        JTextField search = new JTextField();
        search.setToolTipText("Filter classes by name or package");
        search.putClientProperty("JTextField.placeholderText", "Search classes…");
        search.setBackground(CodeCityApp.UI_BG_DARKER);
        search.setForeground(Color.WHITE);
        search.setCaretColor(Color.WHITE);
        search.setSelectionColor(CodeCityApp.UI_ACCENT);
        search.setFont(new Font("SansSerif", Font.PLAIN, 13));

        JLabel searchLabel = new JLabel("Class search:");
        searchLabel.setForeground(Color.WHITE);
        searchLabel.setFont(new Font("SansSerif", Font.BOLD, 12));

        JPanel searchPanel = new JPanel(new BorderLayout(7, 5));
        searchPanel.setOpaque(false);
        searchPanel.add(searchLabel, BorderLayout.WEST);
        searchPanel.add(search, BorderLayout.CENTER);
        JComboBox<String> quality = new JComboBox<>(new String[]{"All quality levels", "Critical", "High", "Medium", "Low"});
        searchPanel.add(quality, BorderLayout.EAST);
        top.add(searchPanel, BorderLayout.SOUTH);
        root.add(top, BorderLayout.NORTH);

        String[] cols = {"Class Name", "LOC", "Methods", "Attrs", "Comments", "WMC*", "RFC*", "CBO*", "DIT", "NOC", "Fan-In", "Fan-Out", "Quality"};
        DefaultTableModel modelData = new DefaultTableModel(cols, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        for (MetricSnapshot m : r.hotspots()) {
            modelData.addRow(new Object[]{m.type.fullName(), m.loc, m.methods, m.attributes, m.comments,
                    m.wmcEstimated, m.rfcEstimated, m.cboEstimated, m.dit, m.noc, m.fanIn, m.fanOut,
                    MetricsEngine.quality(m)});
        }
        table = new JTable(modelData);
        sorter = new TableRowSorter<>(modelData);
        table.setRowSorter(sorter);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        table.setFillsViewportHeight(true);
        table.setBackground(CodeCityApp.UI_PANEL);
        table.setForeground(Color.WHITE);
        table.setGridColor(CodeCityApp.UI_BORDER);
        table.setSelectionBackground(CodeCityApp.UI_ACCENT);
        table.setSelectionForeground(Color.WHITE);
        table.setAutoCreateRowSorter(false);
        table.setRowHeight(24);
        table.getColumnModel().getColumn(0).setPreferredWidth(360);
        root.add(new JScrollPane(table), BorderLayout.CENTER);

        JLabel foot = new JLabel("* WMC/RFC/CBO are estimates from the available XML model. Use column headers to sort.");
        foot.setForeground(Color.WHITE);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.add(foot, BorderLayout.WEST);
        JButton ok = new JButton("OK");
        ok.setFocusable(false);
        ok.addActionListener(e -> dispose());
        JPanel okBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        okBox.setOpaque(false);
        okBox.add(ok);
        bottom.add(okBox, BorderLayout.EAST);
        root.add(bottom, BorderLayout.SOUTH);
        setContentPane(root);

        RowFilter<Object, Object> filter = new RowFilter<>() {
            public boolean include(Entry<?, ?> e) {
                String q = search.getText().trim().toLowerCase(Locale.ROOT);
                String level = String.valueOf(quality.getSelectedItem());
                String name = String.valueOf(e.getValue(0)).toLowerCase(Locale.ROOT);
                String ql = String.valueOf(e.getValue(12));
                return (q.isEmpty() || name.contains(q)) && (level.startsWith("All") || level.equals(ql));
            }
        };
        Runnable apply = () -> sorter.setRowFilter(filter);
        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { apply.run(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { apply.run(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { apply.run(); }
        });
        quality.addActionListener(e -> apply.run());
    }

    private void exportHtmlReport(MetricsReport report) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export Report as HTML");
        chooser.setSelectedFile(new java.io.File("Software metrics.html"));
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("HTML files (*.html)", "html"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        java.io.File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".html")) {
            file = new java.io.File(file.getAbsolutePath() + ".html");
        }
        try {
            ArchitectureReportExporter.write(report.model, file);
            JOptionPane.showMessageDialog(this, "Report exported successfully:\n" + file.getAbsolutePath(),
                    "HTML Report", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            AppLogger.get().log(java.util.logging.Level.WARNING, "HTML report export failed", ex);
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Export error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void setFullScreenBounds() {
        GraphicsConfiguration gc = getGraphicsConfiguration();
        Rectangle b = gc == null ? GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds() : gc.getBounds();
        Insets in = gc == null ? new Insets(0, 0, 0, 0) : Toolkit.getDefaultToolkit().getScreenInsets(gc);
        setBounds(b.x + in.left, b.y + in.top, b.width - in.left - in.right, b.height - in.top - in.bottom);
    }

    private static String summary(MetricsReport r) {
        return "PROJECT QUALITY OVERVIEW\n" +
                "Name        : " + r.model.name + "    LOC: " + r.loc + "    Packages: " + r.packages + "    Classes: " + r.types + "\n" +
                "Methods     : " + r.methods + "    Attributes: " + r.attributes + "    Comments: " + r.comments + "    Relations: " + r.relations;
    }
}
