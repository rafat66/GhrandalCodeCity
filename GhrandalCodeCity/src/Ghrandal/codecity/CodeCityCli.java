package Ghrandal.codecity;

import java.io.*; import java.util.*;

/** Headless automation entry point for CI pipelines and architecture reports. */
public final class CodeCityCli {
    private CodeCityCli(){}
    public static void main(String[] args) throws Exception {
        Map<String,String> a=parse(args); String input=a.get("--input");
        if(input==null){System.err.println("Usage: CodeCityCli --input project.xml [--report report.html] [--json metrics.json] [--svg city.svg]"); System.exit(2);}
        Model m=XmlReader.load(new File(input)); if(a.containsKey("--report"))ArchitectureReportExporter.write(m,new File(a.get("--report"))); if(a.containsKey("--json"))JsonExporter.write(m,new File(a.get("--json"))); if(a.containsKey("--svg"))SvgExporter.write(m,new File(a.get("--svg")));
        MetricsReport r=new MetricsReport(m); System.out.println(m.name+": LOC="+r.loc+", packages="+r.packages+", classes="+r.types+", methods="+r.methods+", attributes="+r.attributes);
    }
    private static Map<String,String> parse(String[] args){Map<String,String>m=new LinkedHashMap<>();for(int i=0;i<args.length;i++){if(args[i].startsWith("--")&&i+1<args.length&&!args[i+1].startsWith("--"))m.put(args[i],args[++i]);else if(args[i].startsWith("--"))m.put(args[i],"true");}return m;}
}
