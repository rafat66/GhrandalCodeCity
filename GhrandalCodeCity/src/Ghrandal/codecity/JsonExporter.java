package Ghrandal.codecity;

import java.io.*; import java.nio.charset.StandardCharsets; import java.nio.file.Files; import java.util.*;

final class JsonExporter {
    private JsonExporter(){}
    static void write(Model m, File file) throws IOException { MetricsReport r=new MetricsReport(m); StringBuilder b=new StringBuilder("{\n"); b.append("  \"project\":\"").append(q(m.name)).append("\",\n  \"loc\":").append(r.loc).append(",\n  \"packages\":").append(r.packages).append(",\n  \"classes\":[\n"); int i=0; for(MetricSnapshot x:r.classes.values()){if(i++>0)b.append(",\n"); b.append("    {\"name\":\"").append(q(x.type.fullName())).append("\",\"loc\":").append(x.loc).append(",\"methods\":").append(x.methods).append(",\"attributes\":").append(x.attributes).append(",\"wmcEstimated\":").append(x.wmcEstimated).append(",\"rfcEstimated\":").append(x.rfcEstimated).append(",\"cboEstimated\":").append(x.cboEstimated).append(",\"dit\":").append(x.dit).append(",\"noc\":").append(x.noc).append(",\"fanIn\":").append(x.fanIn).append(",\"fanOut\":").append(x.fanOut).append(",\"quality\":\"").append(q(MetricsEngine.quality(x))).append("\"}"); } b.append("\n  ]\n}\n"); Files.writeString(file.toPath(),b.toString(),StandardCharsets.UTF_8); }
    private static String q(String s){return s==null?"":s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n");}
}
