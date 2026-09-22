package Ghrandal.codecity;

import java.util.*;

final class MetricsReport {
    final Model model;
    final Map<ClassInfo,MetricSnapshot> classes;
    final int loc, packages, types, methods, attributes, comments, relations;
    MetricsReport(Model model) {
        this.model=model; this.classes=MetricsEngine.calculate(model);
        loc=model==null?0:model.loc; packages=model==null?0:model.packageCount(); types=model==null?0:model.classes.size();
        int m=0,a=0,c=0,r=0; if(model!=null) for(ClassInfo x:model.classes){m+=x.methods.size();a+=x.attributes.size();c+=x.comments;}
        for(MetricSnapshot x:classes.values()) r += x.fanOut;
        methods=m; attributes=a; comments=c; relations=r;
    }
    List<MetricSnapshot> hotspots(){ List<MetricSnapshot> l=new ArrayList<>(classes.values()); l.sort(Comparator.comparingInt((MetricSnapshot x)->x.loc).reversed().thenComparingInt(x->x.cboEstimated).reversed()); return l; }
}
