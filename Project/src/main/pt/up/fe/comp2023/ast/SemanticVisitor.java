package pt.up.fe.comp2023.ast;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2023.ast.ASymbolTable;

import java.util.ArrayList;
import java.util.List;

public class SemanticVisitor extends AJmmVisitor<ASymbolTable, Type> {

    private String currentMethod;
    private List<Report> reports;

    public SemanticVisitor(){
        this.reports = new ArrayList<>();
    }

    public List<Report> getReports() {return this.reports;}
    @Override
    protected void buildVisitor() {
        setDefaultVisit(this::skip);
        addVisit("MainMethod", this::dealWithMain);                     // Method (declaration and body
        addVisit("Method", this::dealWithMethod);
        addVisit("Parenthesis", this::dealWithParenthesis);
        addVisit("IfStatement", this::dealWithConditionalStatement);    // Statements
        addVisit("WhileStatement", this::dealWithConditionalStatement);
        addVisit("Assignment", this::dealWithAssignment);
        addVisit("Parameter", this::dealWithDeclaration);
        addVisit("NewIntArray", this::dealWithArrayDeclaration);        // Arrays
        addVisit("IndexAccess", this::dealWithArrayAccess);
        addVisit("IndexAssignment", this::dealWithArrayAssignment);
        addVisit("True", this::dealWithBoolean);                        // Leaf expressions
        addVisit("False", this::dealWithBoolean);
        addVisit("Integer", this::dealWithInteger);
        addVisit("Identifier", this::dealWithIdentifier);
        addVisit("This", this::dealWithThis);
        addVisit("NewClass", this::dealWithNewClass);
        addVisit("Negation", this::dealWithNegation);                   // Operations
        addVisit("Comparation", this::dealWithComparation);
        addVisit("BinaryOp", this::dealWithBinaryOp);
        addVisit("MethodCall", this::functionVerification);
        addVisit("MethodLength", this::length);
    }

    private Type skip(JmmNode jmmNode, ASymbolTable st){
        for(JmmNode child : jmmNode.getChildren())
            visit(child, st);
        return null;
    }

    private Type dealWithDeclaration(JmmNode node, ASymbolTable st) {
        JmmNode child = node.getChildren().get(0);
        if (!child.getAttributes().contains("value"))
            return null;    // Array, assuming no multidimensional arrays
        if (child.get("value").equals("int") || child.get("value").equals("boolean"))
            return null;    // Default types
        if (st.getImports().contains(child.get("value")) || st.getClassName().equals(child.get("value")) || (st.getSuper() != null && st.getSuper().equals(child.get("value")))) {
            return null;    // Class types
        }
        addReport("Invalid Class", node.get("lineStart"));
        return null;
    }

    private Type dealWithMain(JmmNode jmmNode, ASymbolTable st){
        currentMethod = "main";
        for(JmmNode child : jmmNode.getChildren())
            visit(child, st);
        return null;
    }

    private Type dealWithMethod(JmmNode node, ASymbolTable st){
        currentMethod = node.get("methodName");
        Type type = null;
        for(JmmNode child : node.getChildren())
            type = visit(child, st);
        if(st.getReturnType(currentMethod).getName().equals("void") && type != null)
            addReport(currentMethod + " is void but is returning something", node.get("lineStart"), type);
        else {
            if (type == null)
                addReport(currentMethod + " is not returning anything", node.get("lineStart"));
            else if (!type.equals(st.getReturnType(currentMethod)))
                addReport(currentMethod + " return value doesn't match its declaration", node.get("lineStart"), type);
        }
        return null;
    }

    private Type dealWithConditionalStatement(JmmNode node, ASymbolTable st) {
        Boolean first = true;
        for (JmmNode child : node.getChildren()) {
            Type type = visit(child, st);
            if (first) {
                first = false;
                if (type == null || !type.equals(new Type("boolean", false)))
                    addReport("Conditional expression not a boolean", node.get("lineStart"), type);
            }
        }
        return null;
    }

    private Type dealWithAssignment(JmmNode node, ASymbolTable st) {
        Type type = isVariable(node.get("var"), st, node.get("lineStart"));
        Type type2 = visit(node.getChildren().get(0), st);
        if (type == null)
            addReport("Trying to assign value to undeclared identifier " + node.get("var"), node.get("lineStart"));
        else if(type2 == null)
            addReport("Trying to assign an invalid value", node.get("lineStart"), type);
        else if (!type.equals(type2)) {
            if(!checkIfExtension(type, type2, st) && !type2.getName().equals("ALWAYS VALID"))
                addReport("Can't assign value of type " + type2 + " to identifier of type " + type, node.get("lineStart"), type);
        }
        return null;
    }

    private Type dealWithParenthesis(JmmNode node, ASymbolTable st) {
        return visit(node.getChildren().get(0), st);
    }

    private Type dealWithBoolean(JmmNode node, ASymbolTable st){
        return new Type("boolean", false);
    }

    private Type dealWithInteger(JmmNode node, ASymbolTable st){
        return new Type("int", false);
    }

    private Type dealWithIdentifier(JmmNode node, ASymbolTable st){
        Type type = isVariable(node.get("value"), st, node.get("lineStart"));
        if (type == null)
            addReport("Undeclared Identifier, variable " + node.get("value") + " doesn't exist", node.get("lineStart"));
        return type;
    }

    private Type dealWithThis(JmmNode node, ASymbolTable st){
        if (currentMethod.equals("main"))   //  Main is the only static method
            addReport("The 'this' keyword can't be used inside a static method", node.get("lineStart"));
        return new Type(st.getClassName(), false);
    }

    private Type dealWithNewClass(JmmNode node, ASymbolTable st){
        if(st.getImports().contains(node.get("className")) || st.getClassName().equals(node.get("className")))
            return new Type(node.get("className"), false);  //Import or self
        if(st.getSuper() != null && st.getSuper().equals(node.get("className")))
            return new Type(node.get("className"), false);  //Implicit Superclass
        addReport("Class " + node.get("className") + " doesn't exist", node.get("lineStart"));
        return null;
    }

    private Type dealWithArrayDeclaration(JmmNode node, ASymbolTable st) {
        Type type = visit(node.getChildren().get(0), st);
        if (type == null || !type.equals(new Type("int", false)))
            addReport("Array length is not an integer", node.get("lineStart"), type);
        return new Type("int", true); //We are only dealing with integer arrays
    }

    private Type dealWithArrayAccess(JmmNode node, ASymbolTable st) {
        Type type = visit(node.getChildren().get(0), st);
        if (type == null || !type.isArray())
            addReport("Accessing an identifier that is not an array", node.get("lineStart"), type);
        type = visit(node.getChildren().get(1), st);
        if (type == null || !type.equals(new Type("int", false)))
            addReport("Array Index not an integer", node.get("lineStart"), type);
        return new Type("int", false); //All arrays are of integers
    }

    private Type dealWithArrayAssignment(JmmNode node, ASymbolTable st) {
        Type type = visit(node.getChildren().get(0), st);
        if (type == null || !type.equals(new Type("int", false)))
            addReport("Array Index not an integer", node.get("lineStart"), type);
        type = isVariable(node.get("var"), st, node.get("lineStart"));
        if (type == null || !type.isArray() || !type.getName().equals("int"))
            addReport("Invalid identifier " + node.get("var") + ", array access must be done on an array", node.get("lineStart"));
        type = visit(node.getChildren().get(1), st);
        if (type == null || !type.equals(new Type("int", false))) //Arrays are always int
            addReport("Values assigned to array elements must be integers", node.get("lineStart"), type);
        return null;
    }

    private Type dealWithNegation(JmmNode node, ASymbolTable st) {
        Type type = visit(node.getChildren().get(0), st);
        if (type == null || !type.equals(new Type("boolean", false)))
            addReport("Negating a value that is not a boolean", node.get("lineStart"), type);
        return new Type("boolean", false);
    }

    private Type dealWithComparation(JmmNode node, ASymbolTable st) {   // Assuming operands must be integers
        Type type = visit(node.getChildren().get(0), st);
        if(type == null || !type.equals(new Type("int", false)))
            addReport("Comparison: Operand 1 not an integer", node.get("lineStart"), type);
        type = visit(node.getChildren().get(1), st);
        if(type == null || !type.equals(new Type("int", false)))
            addReport("Comparison: Operand 2 not an integer", node.get("lineStart"), type);
        return new Type("boolean", false);
    }

    private Type dealWithBinaryOp(JmmNode node, ASymbolTable st) {
        Type type = visit(node.getChildren().get(0), st);
        if (node.get("op").equals("&&") || node.get("op").equals("&&")) {
            if (type == null || !type.equals(new Type("boolean", false)))
                addReport("Logical Operation: Operand 1 not a boolean", node.get("lineStart"), type);
            type = visit(node.getChildren().get(1), st);
            if (type == null || !type.equals(new Type("boolean", false)))
                addReport("Logical Operation: Operand 2 not a boolean", node.get("lineStart"), type);
            return new Type("boolean", false);
        }
        else {
            if (type == null || !type.equals(new Type("int", false)))
                addReport("Arithmetic Operation: Operand 1 not an integer", node.get("lineStart"), type);
            type = visit(node.getChildren().get(1), st);
            if (type == null || !type.equals(new Type("int", false)))
                addReport("Arithmetic Operation: Operand 2 not an integer", node.get("lineStart"), type);
            return new Type("int", false);
        }
    }

    private Type length(JmmNode node, ASymbolTable st) {
        Type type = visit(node.getChildren().get(0), st);
        if (type == null || !type.isArray())
            addReport("Length being called on not an array", node.get("lineStart"), type);
        return new Type("int", false);
    }

    private Type functionVerification(JmmNode node, ASymbolTable st) {
        Type type = visit(node.getChildren().get(0), st);
        if (type == null)
            addReport("Class not recognized", node.get("lineStart"));
        else if (type.getName().equals(st.getClassName())) { // Same class
            String methodName = node.getChildren().get(1).get("value");
            if (st.getMethods().contains(methodName)) {
                List<Symbol> parameters = st.getParameters(methodName);
                if (!(parameters.size() == node.getChildren().size() - 2))
                    addReport("Invalid number of arguments in method", node.get("lineStart"));
                else {
                    int i = 2;
                    for (Symbol parameter : parameters) {   // Check parameter types
                        Type paramType = visit(node.getChildren().get(i), st);
                        if (paramType == null || !paramType.equals(parameter.getType()))
                            addReport("Method parameter types don't match", node.get("lineStart"), paramType);
                        i++;
                    }
                }
                return st.getReturnType(methodName);
            }
            else {  // Method doesn't exist, check superclass
                if (st.getSuper() == null)  //If there's a superclass, we assume it works
                    addReport("Method doesn't exist", node.get("lineStart"));
                return new Type("ALWAYS VALID", false);
            }
        }
        else    // Superclass or imported
            return new Type("ALWAYS VALID", false);
        return null;
    }

    private void addReport(String message, String line) {
        reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(line), message));
    }
    private void addReport(String message, String line, Type type) {
        if (type == null || !type.getName().equals("ALWAYS VALID"))
            reports.add(new Report(ReportType.ERROR, Stage.SEMANTIC, Integer.parseInt(line), message));
    }

    private Type isVariable(String variable, ASymbolTable st, String line) {  // Organized in descending priority
        for (Symbol localVariable : st.getLocalVariables(currentMethod)) {
            if (localVariable.getName().equals(variable))                   // Local Variable
                return localVariable.getType();
        }   for (Symbol parameter : st.getParameters(currentMethod)) {
            if (parameter.getName().equals(variable))                       // Method Parameter
                return parameter.getType();
        }   for (Symbol classField : st.getFields()) {
            if (classField.getName().equals(variable)) {                    // Class Field
                if(currentMethod.equals("main")){
                    if (st.getImports().contains(variable) || (st.getSuper() != null && st.getSuper().equals(variable)))
                        return new Type(variable, false); //Variable could still be one of the lower priority options
                    else {
                        addReport("Can't try to use class fields inside a static method", line);
                        return new Type(variable, false);
                    }
                }
                return classField.getType();
            }
        }   if (st.getImports().contains(variable)) {                       // Imported Class
            return new Type(variable, false);
        }   if(st.getSuper() != null && st.getSuper().equals(variable))     // Implicit Superclass
            return new Type(variable, false);
        return null;                                                        // No variable
    }

    private Boolean checkIfExtension(Type class1, Type class2, ASymbolTable st) {
        if (st.getImports().contains(class1.getName()) && st.getImports().contains(class2.getName()))
            return true;
        if (st.getSuper() == null)
            return false;
        if (class1.getName().equals(st.getClassName()) && class2.getName().equals(st.getSuper()))
            return true;
        if (class2.getName().equals(st.getClassName()) && class1.getName().equals(st.getSuper()))
            return true;
        return false;
    }
}
