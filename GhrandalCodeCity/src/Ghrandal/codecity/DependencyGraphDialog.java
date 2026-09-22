package Ghrandal.codecity;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;

/** Full-screen dependency graph with independent relation filters and image exports. */
final class DependencyGraphDialog extends JDialog {
    private static final long serialVersionUID = 1L;
    private final GraphPanel graphPanel;

    DependencyGraphDialog(Window owner, Model model) {
        super(owner, "GhrandalCodeCity — Dependency Graph", ModalityType.MODELESS);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        graphPanel = new GraphPanel(model);
        setContentPane(graphPanel);
        setFullScreenBounds();
    }

    private void setFullScreenBounds() {
        GraphicsConfiguration gc = getGraphicsConfiguration();
        Rectangle b = gc == null ? GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds() : gc.getBounds();
        Insets in = gc == null ? new Insets(0,0,0,0) : Toolkit.getDefaultToolkit().getScreenInsets(gc);
        setBounds(b.x + in.left, b.y + in.top, b.width - in.left - in.right, b.height - in.top - in.bottom);
    }

    private static final class Edge {
        final ClassInfo a, b;
        final int kind; // 0 inheritance, 1 composition, 2 invocation, 3 attribute access
        Edge(ClassInfo a, ClassInfo b, int kind) { this.a=a; this.b=b; this.kind=kind; }
    }

    private static final class GraphPanel extends JPanel {
        private static final long serialVersionUID = 1L;
        final Model model;
        final List<Edge> edges = new ArrayList<>();
        final Map<ClassInfo, Point2D.Double> positions = new IdentityHashMap<>();
        final Map<ClassInfo, Integer> degree = new IdentityHashMap<>();
        final Set<ClassInfo> visible = Collections.newSetFromMap(new IdentityHashMap<>());
        double scale=1.0, panX=0, panY=0;
        Point lastMouse;
        String query="";
        int maxEdges=100;
        boolean showInheritance=true, showComposition=true, showInvocation=true, showAccess=true;
        final JTextField search=new JTextField(24);
        final JLabel status=new JLabel(" ");
        final JCheckBox inheritance=new JCheckBox("Inheritance",true);
        final JCheckBox composition=new JCheckBox("Composition",true);
        final JCheckBox invocation=new JCheckBox("Method Invocation",true);
        final JCheckBox access=new JCheckBox("Attribute Access",true);
        final JComboBox<String> edgeLimit=new JComboBox<>(new String[]{"10 edges","20 edges","100 edges","500 edges","1,000 edges","2,000 edges","3,000 edges","All edges"});

        GraphPanel(Model m) {
            model=m;
            setLayout(new BorderLayout());
            setBackground(CodeCityApp.UI_BG_DARKER);
            buildGraph();
            add(toolbar(),BorderLayout.NORTH);
            JPanel bottom=new JPanel(new BorderLayout());
            bottom.setBackground(CodeCityApp.UI_BG_DARKER);
            status.setBorder(new EmptyBorder(6,10,6,10));
            bottom.add(status,BorderLayout.CENTER);
            JButton ok=button("OK");
            ok.addActionListener(e->SwingUtilities.getWindowAncestor(this).dispose());
            JPanel okBox=new JPanel(new FlowLayout(FlowLayout.RIGHT,8,5));
            okBox.setOpaque(false);
            okBox.add(ok);
            bottom.add(okBox,BorderLayout.EAST);
            add(bottom,BorderLayout.SOUTH);
            installMouseNavigation();
            updateVisible();
        }

        private JComponent toolbar() {
            JPanel bar=new JPanel(new FlowLayout(FlowLayout.LEFT,7,7));
            bar.setBackground(CodeCityApp.UI_PANEL);
            bar.setBorder(new EmptyBorder(3,7,3,7));
            JLabel searchLabel=new JLabel("Class search:");
            searchLabel.setForeground(Color.WHITE);
            searchLabel.setFont(new Font("SansSerif",Font.BOLD,12));
            search.setToolTipText("Filter classes by name");
            search.setBackground(CodeCityApp.UI_BG_DARKER);
            search.setForeground(Color.WHITE);
            search.setCaretColor(Color.WHITE);
            search.setSelectionColor(CodeCityApp.UI_ACCENT);
            search.setFont(new Font("SansSerif",Font.PLAIN,13));

            JButton fit=button("Fit"), plus=button("+"), minus=button("−"), reset=button("Reset"), focus=button("Focus");
            JButton svg=iconButton(IconType.SVG,"Save as SVG");
            plus.addActionListener(e->zoom(1.25)); minus.addActionListener(e->zoom(.80)); fit.addActionListener(e->fitView());
            reset.addActionListener(e->{scale=1;panX=panY=0;search.setText("");updateVisible();repaint();});
            focus.addActionListener(e->focusSearch());
            search.addActionListener(e->{query=search.getText().trim().toLowerCase(Locale.ROOT);updateVisible();repaint();});
            search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener(){public void insertUpdate(javax.swing.event.DocumentEvent e){changed();}public void removeUpdate(javax.swing.event.DocumentEvent e){changed();}public void changedUpdate(javax.swing.event.DocumentEvent e){changed();}private void changed(){query=search.getText().trim().toLowerCase(Locale.ROOT);updateVisible();repaint();}});

            configureCheck(inheritance); configureCheck(composition); configureCheck(invocation); configureCheck(access);
            ActionListener filter=e->{showInheritance=inheritance.isSelected();showComposition=composition.isSelected();showInvocation=invocation.isSelected();showAccess=access.isSelected();updateVisible();repaint();};
            inheritance.addActionListener(filter);composition.addActionListener(filter);invocation.addActionListener(filter);access.addActionListener(filter);
            edgeLimit.addActionListener(e->{maxEdges=switch(edgeLimit.getSelectedIndex()){case 0->10;case 1->20;case 2->100;case 3->500;case 4->1000;case 5->2000;case 6->3000;default->Integer.MAX_VALUE;};updateVisible();repaint();});
            svg.addActionListener(e->saveSvg());

            bar.add(searchLabel);bar.add(search);bar.add(focus);bar.add(plus);bar.add(minus);bar.add(fit);bar.add(reset);
            bar.add(inheritance);bar.add(composition);bar.add(invocation);bar.add(access);bar.add(edgeLimit);
            bar.add(svg);
            return bar;
        }

        private void configureCheck(JCheckBox b){b.setForeground(Color.WHITE);b.setBackground(CodeCityApp.UI_PANEL);b.setFont(new Font("SansSerif",Font.PLAIN,12));}
        private JButton button(String text){JButton b=new JButton(text);b.setFocusable(false);return b;}
        private JButton iconButton(IconType icon,String tooltip){
            JButton b=new JButton(new VectorIcon(icon,20,Color.WHITE));
            b.setToolTipText(tooltip);
            b.setFocusable(false);
            b.setFocusPainted(false);
            b.setBorder(BorderFactory.createEmptyBorder(5,8,5,8));
            b.setContentAreaFilled(false);
            b.setOpaque(true);
            b.setBackground(CodeCityApp.UI_BG_DARKER);
            b.setCursor(new Cursor(Cursor.HAND_CURSOR));
            b.addMouseListener(new MouseAdapter(){
                @Override public void mouseEntered(MouseEvent e){b.setBackground(CodeCityApp.UI_PANEL_HOVER);b.setIcon(new VectorIcon(icon,20,CodeCityApp.UI_ACCENT_HOVER));}
                @Override public void mouseExited(MouseEvent e){b.setBackground(CodeCityApp.UI_BG_DARKER);b.setIcon(new VectorIcon(icon,20,Color.WHITE));}
            });
            return b;
        }

        private void buildGraph(){
            if(model==null)return;
            for(ClassInfo c:model.classes){
                ClassInfo t=model.resolveClass(c.superclass,c); if(t!=null&&t!=c)addEdge(c,t,0);
                for(String x:c.compositionTargets){String[] z=x.split("\\|",2);if(z.length==2){t=model.resolveClass(z[0],c);if(t!=null&&t!=c)addEdge(c,t,1);}}
                for(MethodInfo mi:c.methods){
                    Set<ClassInfo> invocationTargets=Collections.newSetFromMap(new IdentityHashMap<>());
                    for(String x:mi.invocations){MethodTarget mt=model.resolveMethod(x,c);if(mt!=null&&mt.owner()!=c)invocationTargets.add(mt.owner());}
                    for(ClassInfo q:invocationTargets)addEdge(c,q,2);
                    Set<ClassInfo> accessTargets=Collections.newSetFromMap(new IdentityHashMap<>());
                    for(String x:mi.attributeAccesses){AttributeTarget at=model.resolveAttribute(x,c);if(at!=null&&at.owner()!=c)accessTargets.add(at.owner());}
                    for(ClassInfo q:accessTargets)addEdge(c,q,3);
                }
            }
            int n=model.classes.size(),cols=Math.max(1,(int)Math.ceil(Math.sqrt(n)));double spacing=95;
            int rows=(int)Math.ceil(n/(double)cols);
            for(int i=0;i<n;i++){int col=i%cols,row=i/cols;ClassInfo c=model.classes.get(i);positions.put(c,new Point2D.Double((col-(cols-1)/2.0)*spacing,(row-(rows-1)/2.0)*spacing));}
        }
        private void addEdge(ClassInfo a,ClassInfo b,int kind){edges.add(new Edge(a,b,kind));degree.put(a,degree.getOrDefault(a,0)+1);degree.put(b,degree.getOrDefault(b,0)+1);}
        private boolean edgeEnabled(int kind){return switch(kind){case 0->showInheritance;case 1->showComposition;case 2->showInvocation;default->showAccess;};}
        private Color edgeColor(int kind){return switch(kind){case 0->CityView.TRIANGLE_BLUE;case 1->CityView.RELATION_LINE_COLOR;case 2->CityView.TRIANGLE_GREEN;default->CityView.TRIANGLE_ORANGE;};}

        private void updateVisible(){
            visible.clear();if(model==null)return;
            if(query.isEmpty())visible.addAll(model.classes);else{for(ClassInfo c:model.classes)if(c.fullName().toLowerCase(Locale.ROOT).contains(query))visible.add(c);if(!visible.isEmpty()){Set<ClassInfo> seeds=Collections.newSetFromMap(new IdentityHashMap<>());seeds.addAll(visible);for(Edge e:edges)if(edgeEnabled(e.kind)&&(seeds.contains(e.a)||seeds.contains(e.b))){visible.add(e.a);visible.add(e.b);}}}
            status.setForeground(Color.WHITE);status.setText("  Classes: "+visible.size()+" / "+(model==null?0:model.classes.size())+"    Edges: "+countVisibleEdges()+"    Zoom: "+Math.round(scale*100)+"%    Drag to pan • Mouse wheel to zoom");
        }
        private int countVisibleEdges(){int n=0;for(Edge e:edges)if(edgeEnabled(e.kind)&&visible.contains(e.a)&&visible.contains(e.b))n++;return Math.min(n,maxEdges);}

        @Override protected void paintComponent(Graphics gg){
            super.paintComponent(gg);Graphics2D g=(Graphics2D)gg.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setFont(new Font("SansSerif",Font.PLAIN,12));g.translate(getWidth()/2.0+panX,getHeight()/2.0+panY);g.scale(scale,scale);
            int drawn=0;
            for(Edge e:edges){if(drawn>=maxEdges)break;if(!edgeEnabled(e.kind)||!visible.contains(e.a)||!visible.contains(e.b))continue;Point2D a=positions.get(e.a),b=positions.get(e.b);if(a==null||b==null)continue;drawDirectedEdge(g,a,b,edgeColor(e.kind),e.kind);drawn++;}
            for(ClassInfo c:model.classes){if(!visible.contains(c))continue;Point2D q=positions.get(c);if(q==null)continue;int d=Math.min(12,degree.getOrDefault(c,0));int s=14+d/3;g.setColor(model.colorFor(c));g.fillRoundRect((int)q.getX()-s,(int)q.getY()-s,2*s,2*s,6,6);g.setColor(Color.WHITE);g.drawRoundRect((int)q.getX()-s,(int)q.getY()-s,2*s,2*s,6,6);String label=shortName(c);FontMetrics fm=g.getFontMetrics();int labelX=(int)Math.round(q.getX()-fm.stringWidth(label)/2.0);int labelY=(int)Math.round(q.getY()+s+fm.getAscent()+7);g.setColor(Color.WHITE);g.drawString(label,labelX,labelY);}
            g.dispose();
        }
        private void drawDirectedEdge(Graphics2D g,Point2D from,Point2D to,Color color,int kind){
            g.setColor(color);g.setStroke(new BasicStroke(kind==0?2.2f:1.8f));g.draw(new Line2D.Double(from,to));
            double angle=Math.atan2(to.getY()-from.getY(),to.getX()-from.getX());double size=9;double tx=to.getX()-size*.35*Math.cos(angle),ty=to.getY()-size*.35*Math.sin(angle);double bx=tx-size*Math.cos(angle),by=ty-size*Math.sin(angle);double px=-Math.sin(angle)*size*.55,py=Math.cos(angle)*size*.55;int[] xs={(int)tx,(int)(bx+px),(int)(bx-px)},ys={(int)ty,(int)(by+py),(int)(by-py)};g.setColor(color);g.fillPolygon(xs,ys,3);g.setColor(Color.DARK_GRAY);g.drawPolygon(xs,ys,3);
        }
        private String shortName(ClassInfo c){return c.name.length()>32?c.name.substring(0,29)+"...":c.name;}
        private void installMouseNavigation(){addMouseListener(new MouseAdapter(){public void mousePressed(MouseEvent e){lastMouse=e.getPoint();}});addMouseMotionListener(new MouseMotionAdapter(){public void mouseDragged(MouseEvent e){if(lastMouse!=null){panX+=e.getX()-lastMouse.x;panY+=e.getY()-lastMouse.y;lastMouse=e.getPoint();repaint();}}});addMouseWheelListener(e->{double factor=e.getPreciseWheelRotation()<0?1.12:.89;zoomAt(factor,e.getPoint());});}
        private void zoom(double factor){zoomAt(factor,new Point(getWidth()/2,getHeight()/2));}
        private void zoomAt(double factor,Point mouse){double old=scale,next=Math.max(.08,Math.min(8.0,old*factor));double mx=mouse.x-getWidth()/2.0-panX,my=mouse.y-getHeight()/2.0-panY;panX-=mx*(next/old-1);panY-=my*(next/old-1);scale=next;updateVisible();repaint();}
        private void fitView(){if(visible.isEmpty())return;double minX=Double.POSITIVE_INFINITY,minY=Double.POSITIVE_INFINITY,maxX=Double.NEGATIVE_INFINITY,maxY=Double.NEGATIVE_INFINITY;for(ClassInfo c:visible){Point2D p=positions.get(c);if(p==null)continue;minX=Math.min(minX,p.getX());minY=Math.min(minY,p.getY());maxX=Math.max(maxX,p.getX());maxY=Math.max(maxY,p.getY());}double w=Math.max(100,maxX-minX+220),h=Math.max(100,maxY-minY+190);scale=Math.max(.08,Math.min(2.0,Math.min((getWidth()-40)/w,(getHeight()-100)/h)));panX=panY=0;updateVisible();repaint();}
        private void focusSearch(){if(query.isEmpty()){fitView();return;}ClassInfo first=null;for(ClassInfo c:visible)if(c.fullName().toLowerCase(Locale.ROOT).contains(query)){first=c;break;}if(first==null)return;Point2D p=positions.get(first);if(p!=null){panX=-p.getX()*scale;panY=-p.getY()*scale;scale=Math.max(scale,1.25);}repaint();}

        private BufferedImage renderImage(){BufferedImage image=new BufferedImage(Math.max(1,getWidth()),Math.max(1,getHeight()),BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();paint(g);g.dispose();return image;}
        private void saveImage(String ext){JFileChooser chooser=new JFileChooser();chooser.setSelectedFile(new File("ghrandal-dependency-graph."+ext));if(chooser.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return;try{File f=chooser.getSelectedFile();if(!f.getName().toLowerCase(Locale.ROOT).endsWith("."+ext))f=new File(f.getAbsolutePath()+"."+ext);BufferedImage image=renderImage();if("png".equals(ext))ImageIO.write(image,"png",f);else SimplePdf.write(image,f);status.setText("  Saved: "+f.getName());}catch(Exception ex){JOptionPane.showMessageDialog(this,ex.getMessage(),"Dependency graph export error",JOptionPane.ERROR_MESSAGE);}}
        private void saveSvg(){JFileChooser chooser=new JFileChooser();chooser.setSelectedFile(new File("ghrandal-dependency-graph.svg"));if(chooser.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return;try{File f=chooser.getSelectedFile();if(!f.getName().toLowerCase(Locale.ROOT).endsWith(".svg"))f=new File(f.getAbsolutePath()+".svg");SvgExporter.writeImageAsSvg(renderImage(),f,"GhrandalCodeCity — Dependency Graph");status.setText("  Saved: "+f.getName());}catch(Exception ex){JOptionPane.showMessageDialog(this,ex.getMessage(),"Dependency graph SVG error",JOptionPane.ERROR_MESSAGE);}}
    }
}
