package pt.up.fe.comp2023;

import org.specs.comp.ollir.Method;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.ollir.JmmOptimization;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2023.ast.ASymbolTable;
import pt.up.fe.comp2023.ollir.OllirVisitor;
import pt.up.fe.comp2023.optimizations.FoldingVisitor;
import pt.up.fe.comp2023.regAlloc.GraphColoring;
import pt.up.fe.comp2023.regAlloc.LVAnalysis;
import pt.up.fe.comp2023.optimizations.PropagationVisitor;
import pt.up.fe.comp2023.regAlloc.RegisterAllocation;

import java.util.ArrayList;
import java.util.Map;

public class Optimization implements JmmOptimization {
    @Override
    public OllirResult optimize(OllirResult ollirResult) {
        if (ollirResult.getConfig().get("registerAllocation") == null || ollirResult.getConfig().get("registerAllocation").equals(-1))
            return ollirResult;

        for (Method method: ollirResult.getOllirClass().getMethods()) {
            RegisterAllocation allocation = new RegisterAllocation(method);
            int regNeeded = allocation.determineRegisters();
            int regNum = Integer.parseInt(ollirResult.getConfig().get("registerAllocation"));
            if (regNum != 0 && regNeeded > regNum) {
                String msg = "Method " + method.getMethodName() + " requires at least " + String.valueOf(regNeeded) + " registers";
                ollirResult.getReports().add(new Report(ReportType.ERROR, Stage.OPTIMIZATION, -1, msg));
            }
            else {
                System.out.println("Register Allocation for method " + method.getMethodName());
                for (String var : method.getVarTable().keySet())
                    System.out.println("Variable " + var + " set to use register " + method.getVarTable().get(var).getVirtualReg());
                System.out.println();
            }
        }

        return ollirResult;
    }
    @Override
    public JmmSemanticsResult optimize(JmmSemanticsResult jmmSemanticsResult) {
        if (jmmSemanticsResult.getConfig().get("optimize") == null || !jmmSemanticsResult.getConfig().get("optimize").equals("true")) {
            return jmmSemanticsResult;
        }
        while (true) {
            FoldingVisitor visitor = new FoldingVisitor();
            visitor.visit(jmmSemanticsResult.getRootNode(), (ASymbolTable) jmmSemanticsResult.getSymbolTable());
            PropagationVisitor propagationVisitor = new PropagationVisitor();
            propagationVisitor.visit(jmmSemanticsResult.getRootNode(), (ASymbolTable) jmmSemanticsResult.getSymbolTable());
            if (!visitor.wasUpdated() && !propagationVisitor.wasUpdated())
                break;
        }
        return jmmSemanticsResult;
    }
    @Override
    public OllirResult toOllir(JmmSemanticsResult jmmSemanticsResult) {
        OllirVisitor ov = new OllirVisitor(jmmSemanticsResult.getSymbolTable());
        ov.visit(jmmSemanticsResult.getRootNode());
        System.out.println(ov.getCode());
        return new OllirResult(jmmSemanticsResult, ov.getCode(), new ArrayList<>());
    }
}
