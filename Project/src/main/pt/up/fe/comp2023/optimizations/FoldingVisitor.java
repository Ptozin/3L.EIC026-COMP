package pt.up.fe.comp2023.optimizations;

import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp2023.ast.ASymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNodeImpl;

import java.util.*;

public class FoldingVisitor extends AJmmVisitor<ASymbolTable, Type> {

    private String currentMethod;
    private List<Report> reports;

    private boolean updated;

    public FoldingVisitor(){
        this.reports = new ArrayList<>();
        this.updated = false;
    }

    public List<Report> getReports() {return this.reports;}
    @Override
    protected void buildVisitor() {
        setDefaultVisit(this::skip);

        addVisit("IfStatement", this::dealWithIf);
        addVisit("WhileStatement", this::dealWithWhile);
        addVisit("StatementBlock", this::dealWithStatementBlock);

        addVisit("Parenthesis", this::dealWithParenthesis);
        addVisit("Negation", this::dealWithNegation);
        addVisit("BinaryOp", this::dealWithBinaryOp);
        addVisit("Comparation", this::dealWithComp);
    }

    private Type skip(JmmNode jmmNode, ASymbolTable st){
        for(JmmNode child : jmmNode.getChildren())
            visit(child, st);
        return null;
    }
    private Type dealWithStatementBlock(JmmNode node, ASymbolTable st) { //Needs work
        skip(node, st);
        if (node.getChildren().size() == 1 && node.getChildren().get(0).getKind().equals("StatementBlock")) {
            replace(node, node.getChildren().get(0));
        }
        return null;
    }

    private Type dealWithWhile(JmmNode node, ASymbolTable st) {
        visit(node.getChildren().get(0));
        if (node.getChildren().get(0).getKind().equals("False")) {
            node.delete();
            updated = true;
        }
        return null;
    }

    private Type dealWithIf(JmmNode node, ASymbolTable st) {
        visit(node.getChildren().get(0));
        if (node.getChildren().get(0).getKind().equals("True")) { //Removes if
            visit(node.getChildren().get(1));
            replace(node, node.getChildren().get(1));
        }
        else if (node.getChildren().get(0).getKind().equals("False") && node.getChildren().size() == 3) {
            visit(node.getChildren().get(2));
            replace(node, node.getChildren().get(2));
        }
        else if (node.getChildren().get(0).getKind().equals("False")) {
            node.delete();
            updated = true;
        }
        return null;
    }

    private Type dealWithParenthesis(JmmNode node, ASymbolTable st) {
        Type type = visit(node.getChildren().get(0));
        if (isLeaf(node.getChildren().get(0)))
            replace(node, node.getChildren().get(0));// Removes itself
        return type;
    }

    private Type dealWithNegation(JmmNode node, ASymbolTable st) {
        visit(node.getChildren().get(0));
        if (node.getChildren().get(0).getKind().equals("True"))
            replace(node, "False");
        else if (node.getChildren().get(0).getKind().equals("False"))
            replace(node, "True");
        return new Type("boolean", false);
    }

    private Type dealWithComp(JmmNode node, ASymbolTable st) {
        JmmNode child1 = node.getChildren().get(0);
        visit(child1, st);
        JmmNode child2 = node.getChildren().get(1);
        visit(child2,st);
        if (child1.getKind().equals("Integer") && child2.getKind().equals("Integer")){
            if (node.get("op").equals("==")) {
                if (Integer.valueOf(child1.get("value")) == Integer.valueOf(child2.get("value")))
                    replace(node, "True", Arrays.asList("op"));
                else
                    replace(node, "False", Arrays.asList("op"));
            }
            else if (node.get("op").equals("==")) {
                if (Integer.valueOf(child1.get("value")) != Integer.valueOf(child2.get("value")))
                    replace(node, "True", Arrays.asList("op"));
                else
                    replace(node, "False", Arrays.asList("op"));
            }
            else if (node.get("op").equals(">=")) {
                if (Integer.valueOf(child1.get("value")) >= Integer.valueOf(child2.get("value")))
                    replace(node, "True", Arrays.asList("op"));
                else
                    replace(node, "False", Arrays.asList("op"));
            }
            else if (node.get("op").equals("<=")) {
                if (Integer.valueOf(child1.get("value")) <= Integer.valueOf(child2.get("value")))
                    replace(node, "True", Arrays.asList("op"));
                else
                    replace(node, "False", Arrays.asList("op"));
            }
            else if (node.get("op").equals(">")) {
                if (Integer.valueOf(child1.get("value")) > Integer.valueOf(child2.get("value")))
                    replace(node, "True", Arrays.asList("op"));
                else
                    replace(node, "False", Arrays.asList("op"));
            }
            else if (node.get("op").equals("<")) {
                if (Integer.valueOf(child1.get("value")) < Integer.valueOf(child2.get("value")))
                    replace(node, "True", Arrays.asList("op"));
                else
                    replace(node, "False", Arrays.asList("op"));
            }
        }


        return new Type("boolean", false);
    }

    private Type dealWithBinaryOp(JmmNode node, ASymbolTable st) {
        JmmNode child1 = node.getChildren().get(0);
        visit(child1, st);
        JmmNode child2 = node.getChildren().get(1);
        visit(child2,st);
        if (checkIfBool(child1) && checkIfBool(child2)){
            if (node.get("op").equals("&&")) {
                if (child1.getKind().equals(child2.getKind()))
                    replace(node, "True");
                else
                    replace(node, "False");
            }
            else if (node.get("op").equals("||")) {
                if (child1.getKind().equals("True") || child2.getKind().equals("True"))
                    replace(node, "True");
                else
                    replace(node, "False");
            }
        }
        else if (child1.getKind().equals("Integer") && child2.getKind().equals("Integer")) {
            if (node.get("op").equals("+")) {
                String result = Integer.toString(Integer.valueOf(child1.get("value")) + Integer.valueOf(child2.get("value")));
                LinkedHashMap<String,String> value = new LinkedHashMap<>();
                value.put("value", result);
                replace(node, "Integer", Arrays.asList("op"), value);
            }
            else if (node.get("op").equals("-")) {
                String result = Integer.toString(Integer.valueOf(child1.get("value")) - Integer.valueOf(child2.get("value")));
                LinkedHashMap<String, String> value = new LinkedHashMap<>();
                value.put("value", result);
                replace(node, "Integer", Arrays.asList("op"), value);
            }
            else if (node.get("op").equals("*")) {
                String result = Integer.toString(Integer.valueOf(child1.get("value")) * Integer.valueOf(child2.get("value")));
                LinkedHashMap<String, String> value = new LinkedHashMap<>();
                value.put("value", result);
                replace(node, "Integer", Arrays.asList("op"), value);
            }
            else if (node.get("op").equals("/")) {
                String result = Integer.toString(Integer.valueOf(child1.get("value")) / Integer.valueOf(child2.get("value")));
                LinkedHashMap<String, String> value = new LinkedHashMap<>();
                value.put("value", result);
                replace(node, "Integer", Arrays.asList("op"), value);
            }
            else if (node.get("op").equals("%")) {
                String result = Integer.toString(Integer.valueOf(child1.get("value")) % Integer.valueOf(child2.get("value")));
                LinkedHashMap<String, String> value = new LinkedHashMap<>();
                value.put("value", result);
                replace(node, "Integer", Arrays.asList("op"), value);
            }
        }
        return null;
    }

    private boolean checkIfBool (JmmNode node) {
        return (node.getKind().equals("True") || node.getKind().equals("False"));
    }

    private boolean isLeaf (JmmNode node) {
        List<String> leafNodes = Arrays.asList("Integer", "True", "False", "Identifier", "This");
        return leafNodes.contains(node.getKind());
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

    public boolean wasUpdated() {
        return this.updated;
    }
}
