package Ghrandal.codecity;

import java.util.*;

final class MetricSnapshot {
    final ClassInfo type;
    final int loc, methods, attributes, comments, parameters, locals, invocations, accesses;
    final int wmcEstimated, rfcEstimated, cboEstimated, dit, noc, fanIn, fanOut;
    final double commentRatio, methodDensity;
    MetricSnapshot(ClassInfo type, int loc, int methods, int attributes, int comments, int parameters,
                   int locals, int invocations, int accesses, int wmcEstimated, int rfcEstimated,
                   int cboEstimated, int dit, int noc, int fanIn, int fanOut,
                   double commentRatio, double methodDensity) {
        this.type=type; this.loc=loc; this.methods=methods; this.attributes=attributes; this.comments=comments;
        this.parameters=parameters; this.locals=locals; this.invocations=invocations; this.accesses=accesses;
        this.wmcEstimated=wmcEstimated; this.rfcEstimated=rfcEstimated; this.cboEstimated=cboEstimated;
        this.dit=dit; this.noc=noc; this.fanIn=fanIn; this.fanOut=fanOut;
        this.commentRatio=commentRatio; this.methodDensity=methodDensity;
    }
}
