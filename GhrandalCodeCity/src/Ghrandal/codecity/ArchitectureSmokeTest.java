package Ghrandal.codecity;

import java.nio.file.*; import java.util.*;

/** Lightweight no-dependency regression test for the metrics engine. */
public final class ArchitectureSmokeTest {
    public static void main(String[] args) throws Exception {
        Path xml=Files.createTempFile("codecity-smoke-",".xml");
        Files.writeString(xml,"<Project ProjectName='Smoke' ProjectLOC='30'><Packages><Package PackageName='p'><Classes><Class ClassName='A' LOC='10' NOC='0' classAccessLevel='public'><Attributes><Attribute AttributeName='b' AttributeType='B'/></Attributes><Methods><Method MethodName='go' MethodReturnType='void'><MethodInvocations><MethodInvocation MethodInvocationName='run'/></MethodInvocations></Method></Methods></Class><Class ClassName='B' LOC='20' NOC='0'><Methods><Method MethodName='run' MethodReturnType='void'/></Methods></Class></Classes></Package></Packages></Project>");
        Model m=XmlReader.load(xml.toFile()); MetricsReport r=new MetricsReport(m); if(r.types!=2||r.methods!=2)throw new AssertionError("unexpected counts"); if(r.classes.size()!=2)throw new AssertionError("missing metrics"); Files.deleteIfExists(xml); System.out.println("ARCHITECTURE_SMOKE_OK");
    }
}
