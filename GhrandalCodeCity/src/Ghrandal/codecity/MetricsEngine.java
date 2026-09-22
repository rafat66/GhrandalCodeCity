package Ghrandal.codecity;

import java.util.*;

/** Computes architecture/maintainability indicators available from the Code-Metamodel XML. */
final class MetricsEngine {
    private MetricsEngine() {}

    static Map<ClassInfo, MetricSnapshot> calculate(Model model) {
        Map<ClassInfo, MetricSnapshot> out = new LinkedHashMap<>();
        if (model == null) return out;
        Map<ClassInfo, Integer> fanIn = new IdentityHashMap<>();
        Map<ClassInfo, Integer> fanOut = new IdentityHashMap<>();
        Map<String, List<MethodTarget>> methodsByName = new HashMap<>();
        Map<String, List<AttributeTarget>> attributesByName = new HashMap<>();
        for (ClassInfo c : model.classes) {
            for (MethodInfo m : c.methods) methodsByName.computeIfAbsent(m.name, k -> new ArrayList<>()).add(new MethodTarget(c, m));
            for (AttributeInfo a : c.attributes) attributesByName.computeIfAbsent(a.name, k -> new ArrayList<>()).add(new AttributeTarget(c, a));
        }
        for (ClassInfo c : model.classes) { fanIn.put(c,0); fanOut.put(c,0); }
        for (ClassInfo c : model.classes) {
            ClassInfo parent = model.resolveClass(c.superclass, c);
            if (parent != null && parent != c) edge(c,parent,fanOut,fanIn);
            for (String encoded : c.compositionTargets) {
                String[] p = encoded.split("\\|",2);
                if (p.length == 2) { ClassInfo target=model.resolveClass(p[0],c); if(target!=null&&target!=c) edge(c,target,fanOut,fanIn); }
            }
            for (MethodInfo m : c.methods) {
                Set<ClassInfo> targets = new LinkedHashSet<>();
                for (String inv : m.invocations) { MethodTarget t=model.resolveMethod(inv,c); if(t!=null && t.owner()!=c) targets.add(t.owner()); }
                for (String acc : m.attributeAccesses) { AttributeTarget t=model.resolveAttribute(acc,c); if(t!=null && t.owner()!=c) targets.add(t.owner()); }
                for (ClassInfo target: targets) edge(c,target,fanOut,fanIn);
            }
        }
        for (ClassInfo c : model.classes) {
            int params=0, locals=0, inv=0, acc=0;
            Set<String> response = new LinkedHashSet<>();
            Set<ClassInfo> coupled = Collections.newSetFromMap(new IdentityHashMap<>());
            for(AttributeInfo a:c.attributes) { ClassInfo t=model.resolveClass(a.type,c); if(t!=null&&t!=c) coupled.add(t); }
            ClassInfo parent=model.resolveClass(c.superclass,c); if(parent!=null&&parent!=c) coupled.add(parent);
            for(MethodInfo m:c.methods) {
                params += parameterCount(m.parameters); locals += m.locals.size(); inv += m.invocations.size(); acc += m.attributeAccesses.size();
                response.add(m.name);
                for(String x:m.invocations) { response.add(x); MethodTarget t=resolveMethodFast(model, x, c, methodsByName); if(t!=null&&t.owner()!=c) coupled.add(t.owner()); }
                for(String x:m.attributeAccesses) { AttributeTarget t=resolveAttributeFast(model, x, c, attributesByName); if(t!=null&&t.owner()!=c) coupled.add(t.owner()); }
            }
            int noc=0; for(ClassInfo child:model.classes) { ClassInfo p=model.resolveClass(child.superclass,child); if(p==c) noc++; }
            int dit=0; Set<ClassInfo> seen=Collections.newSetFromMap(new IdentityHashMap<>()); ClassInfo p=parent;
            while(p!=null && seen.add(p)) { dit++; p=model.resolveClass(p.superclass,p); }
            double ratio=c.loc<=0?0.0:((double)c.comments/Math.max(1,c.loc));
            double density=c.loc<=0?0.0:((double)c.methods.size()/c.loc*100.0);
            out.put(c,new MetricSnapshot(c,c.loc,c.methods.size(),c.attributes.size(),c.comments,params,locals,inv,acc,
                    c.methods.size(),response.size(),coupled.size(),dit,noc,fanIn.get(c),fanOut.get(c),ratio,density));
        }
        return out;
    }

    private static MethodTarget resolveMethodFast(Model model, String raw, ClassInfo scope, Map<String, List<MethodTarget>> index) {
        MethodTarget direct = model.resolveMethod(raw, scope);
        if (direct != null) return direct;
        String s = Model.clean(raw); int dot = s.lastIndexOf('.');
        String name = dot >= 0 ? s.substring(dot + 1) : s;
        List<MethodTarget> candidates = index.get(name);
        if (candidates == null || candidates.isEmpty()) return null;
        if (dot < 0 && scope != null) for (MethodTarget t : candidates) if (t.owner() == scope) return t;
        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    private static AttributeTarget resolveAttributeFast(Model model, String raw, ClassInfo scope, Map<String, List<AttributeTarget>> index) {
        AttributeTarget direct = model.resolveAttribute(raw, scope);
        if (direct != null) return direct;
        String s = Model.clean(raw); int dot = s.lastIndexOf('.');
        String name = dot >= 0 ? s.substring(dot + 1) : s;
        List<AttributeTarget> candidates = index.get(name);
        if (candidates == null || candidates.isEmpty()) return null;
        if (dot < 0 && scope != null) for (AttributeTarget t : candidates) if (t.owner() == scope) return t;
        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    private static void edge(ClassInfo from,ClassInfo to,Map<ClassInfo,Integer> out,Map<ClassInfo,Integer> in){out.put(from,out.get(from)+1);in.put(to,in.get(to)+1);}
    private static int parameterCount(String s){ if(s==null||s.isBlank()) return 0; int n=1; int depth=0; for(char c:s.toCharArray()){ if(c=='<' )depth++; else if(c=='>')depth--; else if(c==','&&depth==0)n++; } return n; }
    static String quality(MetricSnapshot m){
        if(m.loc>=300 || m.wmcEstimated>=30 || m.cboEstimated>=12 || m.fanOut>=12) return "Critical";
        if(m.loc>=150 || m.wmcEstimated>=20 || m.cboEstimated>=8 || m.fanOut>=8) return "High";
        if(m.loc>=80 || m.wmcEstimated>=12 || m.cboEstimated>=5 || m.fanOut>=5) return "Medium";
        return "Low";
    }
}
