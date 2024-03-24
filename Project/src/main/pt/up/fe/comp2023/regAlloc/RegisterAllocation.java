package pt.up.fe.comp2023.regAlloc;

import org.specs.comp.ollir.Method;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.jmm.report.Stage;

import java.util.Map;

public class RegisterAllocation {
    private Method method;
    private LVAnalysis lvAnalysis;
    private int offset;
    public RegisterAllocation(Method method) {
        this.method = method;
        lvAnalysis = new LVAnalysis(method);

        offset = method.getParams().size(); // Parameters
        if (!method.getMethodName().equals("main")) // this
            offset++;
    }

    public int determineRegisters() {
        int regNum = 0;
        while (true) {
            if (allocate(regNum))
                break;
            regNum++;
        }
        return regNum + offset;
    }

    private boolean allocate(int regNum) {
        GraphColoring coloring = new GraphColoring(regNum, lvAnalysis.getConflictInfo());
        if (!coloring.isStackWorking() || !coloring.isRegisterWorking())
            return false;
        else {
            Map<String, Integer> registerInfo = coloring.getRegisters();
            for (String var : registerInfo.keySet())
                method.getVarTable().get(var).setVirtualReg(registerInfo.get(var) + offset);
        }
        return true;
    }
}
