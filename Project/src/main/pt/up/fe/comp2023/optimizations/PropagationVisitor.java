package pt.up.fe.comp2023.optimizations;

import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.JmmNodeImpl;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp2023.ast.ASymbolTable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

public class PropagationVisitor extends AJmmVisitor<ASymbolTable, Type> {

    private String currentMethod;
    private List<Report> reports;

    private boolean updated;
    private boolean inConditional;

    private List<String> deleteAfterIf;

    public PropagationVisitor(){
        this.reports = new ArrayList<>();
        this.updated = false;
        this.deleteAfterIf = new ArrayList<>();
    }

    public List<Report> getReports() {return this.reports;}
    @Override
    protected void buildVisitor() {
        setDefaultVisit(this::skip);
        addVisit("MainMethod", this::dealWithMain);                     // Method (declaration and body
        addVisit("Method", this::dealWithMethod);

        addVisit("Assignment", this::dealWithAssignment);
        addVisit("Identifier", this::dealWithIdentifier);

        addVisit("IfStatement", this::dealWithIf);
        addVisit("WhileStatement", this::dealWithWhile);
    }

    private Type dealWithMain(JmmNode node, ASymbolTable st){
        currentMethod = "main";
        skip(node, st);
        st.resetVarInfo(currentMethod);
        return null;
    }

    private Type dealWithMethod(JmmNode node, ASymbolTable st){
        currentMethod = node.get("methodName");
        skip(node, st);
        st.resetVarInfo(currentMethod);
        return null;
    }

    private Type dealWithWhile(JmmNode node, ASymbolTable st){
        WhileVisitor auxVisitor = new WhileVisitor();
        auxVisitor.visit(node, st);
        for (String var : auxVisitor.getVarsAssigned())
            st.setVarVal(currentMethod, var, "");
        skip(node, st);
        for (String var : auxVisitor.getVarsAssigned())
            st.setVarVal(currentMethod, var, "");
        return null;
    }

    private Type dealWithIf(JmmNode node, ASymbolTable st){
        inConditional = true;
        skip(node, st);
        inConditional = false;
        for (String var : deleteAfterIf)
            st.setVarVal(currentMethod, var, "");
        deleteAfterIf.clear();
        return null;
    }

    private Type skip(JmmNode jmmNode, ASymbolTable st){
        for(JmmNode child : jmmNode.getChildren())
            visit(child, st);
        return null;
    }

    private Type dealWithAssignment(JmmNode node, ASymbolTable st){
        visit(node.getChildren().get(0), st);
        String var = node.get("var");
        if (inConditional)
            deleteAfterIf.add(var);
        if (node.getChildren().get(0).getKind().equals("True"))
            st.setVarVal(currentMethod, var, "True");
        else if (node.getChildren().get(0).getKind().equals("False"))
            st.setVarVal(currentMethod, var, "False");
        else if (node.getChildren().get(0).getKind().equals("Integer"))
            st.setVarVal(currentMethod, var, node.getChildren().get(0).get("value"));
        else
            st.setVarVal(currentMethod, var, "");
        return null;
    }

    private Type dealWithIdentifier(JmmNode node, ASymbolTable st){
        String var = node.get("value");
        String result = st.getVarVal(currentMethod, var);
        if (!result.equals("")) {
            this.updated = true;
            if (result.equals("True"))
                replace(node, "True", Arrays.asList("op"));
            if (result.equals("False"))
                replace(node, "False", Arrays.asList("op"));
            else {
                LinkedHashMap<String,String> value = new LinkedHashMap<>();
                value.put("value", result);
                replace(node, "Integer", Arrays.asList("op"), value);
            }
        }
        return null;
    }
    public boolean wasUpdated() {
        return this.updated;
    }

    //Replacement Functions - use to replace nodes
    private void replace(JmmNode ogNode, JmmNode newNode) {
        this.updated = true;
        ogNode.replace(newNode);
    }
    //1- Simply alters kind
    private void replace(JmmNode node, String newKind) {
        this.updated = true;
        JmmNodeImpl newNode = new JmmNodeImpl(newKind);
        for (String attribute : node.getAttributes())
            newNode.put(attribute, node.get(attribute));
        node.replace(newNode);
    }
    //2- Also removes attributes
    private void replace(JmmNode node, String newKind, List<String> atToExclude) {
        this.updated = true;
        JmmNodeImpl newNode = new JmmNodeImpl(newKind);
        for (String attribute : node.getAttributes()) {
            if (!atToExclude.contains(attribute))
                newNode.put(attribute, node.get(attribute));
        }
        node.replace(newNode);
    }
    //3- Also adds attributes
    private void replace(JmmNode node, String newKind, List<String> atToExclude, LinkedHashMap<String, String> atToAdd) {
        this.updated = true;
        JmmNodeImpl newNode = new JmmNodeImpl(newKind);
        for (String attribute : node.getAttributes()) {
            if (!atToExclude.contains(attribute))
                newNode.put(attribute, node.get(attribute));
        }
        for (String attribute : atToAdd.keySet())
            newNode.put(attribute, atToAdd.get(attribute));
        node.replace(newNode);
    }
}
