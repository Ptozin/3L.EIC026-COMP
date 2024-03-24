package pt.up.fe.comp2023.optimizations;

import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2023.ast.ASymbolTable;

import java.util.ArrayList;
import java.util.List;

public class WhileVisitor extends AJmmVisitor<ASymbolTable, Type> {
    List<String> varsAssigned;
    public WhileVisitor(){
        this.varsAssigned = new ArrayList<>();
    }
    @Override
    protected void buildVisitor() {
        setDefaultVisit(this::skip);
        addVisit("Assignment", this::dealWithAssignment);
    }

    private Type skip(JmmNode jmmNode, ASymbolTable st){
        for(JmmNode child : jmmNode.getChildren())
            visit(child, st);
        return null;
    }

    private Type dealWithAssignment(JmmNode node, ASymbolTable st){
        varsAssigned.add(node.get("var"));
        visit(node.getChildren().get(0), st);
        return null;
    }

    List<String> getVarsAssigned() {
        return varsAssigned;
    }
}
