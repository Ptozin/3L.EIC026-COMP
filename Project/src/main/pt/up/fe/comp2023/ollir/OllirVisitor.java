package pt.up.fe.comp2023.ollir;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ollir.OllirUtils;

import java.util.*;
import java.util.stream.Collectors;

import static pt.up.fe.comp2023.ollir.OllirUtilities.*;

public class OllirVisitor extends AJmmVisitor<Void, Void> {
    private final SymbolTable st;

    private String currentMethod;
    private final Stack<String> stack;

    private String ident = "";
    private int ifCount = 0;
    private boolean midAssignment = false;
    private boolean breakSkip = false;
    private boolean negation = false;
    private boolean insideIf = false;

    private final StringBuilder code;
    private final OllirExpressionVisitor expressionVisitor;

    // Prevent repeated getfield instruction on already loaded field
    private final Map<String, String> fieldsLoaded = new HashMap<>();

    public OllirVisitor(SymbolTable st){
        this.st = st;
        this.code = new StringBuilder();
        this.stack = new Stack<>();
        this.expressionVisitor = new OllirExpressionVisitor(code, st, fieldsLoaded, ident);
    }

    public String getCode(){
        return this.code.toString();
    }

    @Override
    protected void buildVisitor() {
        setDefaultVisit(this::skip);
        addVisit("ClassDeclaration", this::dealWithClass);
        addVisit("Method", this::dealWithMethod);
        addVisit("MainMethod", this::dealWithMainMethod);
        addVisit("BinaryOp", this::dealWithBinaryOp);
        addVisit("Identifier", this::dealWithIdentifier);
        addVisit("Assignment", this::dealWithAssignment);
        addVisit("Integer", this::dealWithInteger);
        addVisit("IfStatement", this::dealWithIf);
        addVisit("NewClass", this::dealWithNewClass);
        addVisit("MethodCall", this::dealWithMethodCall);
        addVisit("True", this::dealWithTrue);
        addVisit("False", this::dealWithFalse);
        addVisit("Negation", this::dealNegation);
        addVisit("WhileStatement", this::dealWithWhile);
        addVisit("Comparation", this::dealWithComparison);
        addVisit("IndexAccess", this::dealWithIndexAccess);
        addVisit("IndexAssignment", this::dealWithIndexAssignment);
        addVisit("MethodLength", this::dealGeneralExpression);
        addVisit("NewIntArray", this::dealGeneralExpression);
    }

    // Common method for multiple kinds, use expression visitor
    private Void dealGeneralExpression(JmmNode jmmNode, Void unused){
        String loc = expressionVisitor.visitWrap(jmmNode, this.ident);
        if(midAssignment) stack.push(loc);
        else code.append(loc);
        return null;
    }

    private Void dealWithIndexAssignment(JmmNode jmmNode, Void unused) {
        String var = jmmNode.get("var");
        String index = expressionVisitor.visitWrap(jmmNode.getJmmChild(0), this.ident);
        String val = expressionVisitor.visitWrap(jmmNode.getJmmChild(1), this.ident);
        Type type = getType(st, currentMethod, var);

        // If array is field
        if(fieldsLoaded.containsKey(var))
            var = fieldsLoaded.get(var);
        else if(variableIsField(st, currentMethod, var)){
            String _type = dealWithType(Objects.requireNonNull(type));
            String aux = this.ident + "temp" + _type + " :=" + _type;
            code.append(aux).append(" getfield(this, ").append(var).append(_type).append(")").append(_type).append(";\n");
            var = "temp";
            fieldsLoaded.put(var, "temp");
        }

        // If array is a parameter
        List<Symbol> parameters = st.getParameters(currentMethod);
        int parameterIndex = parameters.stream().map(Symbol::getName).toList().indexOf(var);

        // If array is local variable
        code.append(this.ident).append("i.i32 :=.i32 ").append(index).append(";\n");
        String t = dealWithType(new Type(type.getName(), false));
        code.append(this.ident).append(parameterIndex != -1 ? "$" + parameterIndex + "." : "").append(var).append("[i.i32]").append(t).append(" :=").append(t).append(" ").append(val).append(";\n");
        return null;
    }

    private Void dealWithIndexAccess(JmmNode jmmNode, Void unused) {
        String var = jmmNode.getJmmChild(0).get("value");
        Type arrayType = getType(st, currentMethod, var);

        // If array in a field
        if(fieldsLoaded.containsKey(var))
            var = fieldsLoaded.get(var);
        else if(variableIsField(st, currentMethod, var)){
            String _type = dealWithType(Objects.requireNonNull(arrayType));
            String aux = this.ident + "temp" + _type + ":=" + _type;
            code.append(aux).append(" getfield(this, ").append(var).append(_type).append(")").append(_type).append(";\n");
            fieldsLoaded.put(var, "temp");
            var = "temp";
        }

        // If array is a parameter
        List<Symbol> parameters = st.getParameters(currentMethod);
        int parameterIndex = parameters.stream().map(Symbol::getName).toList().indexOf(var);

        // If array is a local variable
        String aux = "";
        aux += (parameterIndex != -1 ? "$"+parameterIndex+"." : "")+var+"[";
        String loc = expressionVisitor.visitWrap(jmmNode.getJmmChild(1), this.ident);
        code.append(this.ident).append("i.i32 :=.i32 ").append(loc).append(";\n");
        aux += "i.i32";
        aux += "]"+ dealWithType(new Type(Objects.requireNonNull(arrayType).getName(), false));

        if(midAssignment)
            stack.push(aux);
        else
            code.append(aux);

        return null;
    }

    private Void dealNegation(JmmNode jmmNode, Void unused) {
        this.negation = true;
        visit(jmmNode.getJmmChild(0));
        return null;
    }

    private Void dealWithFalse(JmmNode jmmNode, Void unused) {
        if(midAssignment)
            stack.push( "0.bool");
        else
            code.append("0.bool");
        return null;
    }

    private Void dealWithTrue(JmmNode jmmNode, Void unused) {
        if(midAssignment)
            stack.push( "1.bool");
        else
            code.append("1.bool");
        return null;
    }

    private Void dealWithMethodCall(JmmNode jmmNode, Void unused) {
        if(jmmNode.getJmmChild(0).hasAttribute("value") && getType(st, currentMethod, jmmNode.getJmmChild(0).get("value")) == null)
            dealWithImportMethod(jmmNode);
        else{
            stack.push(expressionVisitor.visitWrap(jmmNode, this.ident));
        }
        return null;
    }

    private Void dealWithNewClass(JmmNode jmmNode, Void unused) {
        stack.push(expressionVisitor.visitWrap(jmmNode, this.ident));
        return null;
    }

    private Void dealWithMainMethod(JmmNode jmmNode, Void unused) {
        // Open method block
        code.append("\n\n" + ident + ".method public static main(args.array.String).V {\n");
        ident += "\t";

        currentMethod = "main";
        expressionVisitor.setMethod("main");

        // Inside method
        for (JmmNode child : jmmNode.getChildren()){
            visit(child);
        }

        // Return and close method block
        boolean hasReturn = jmmNode.getJmmChild(0).getKind().equals("ReturnType");
        if(hasReturn) code.append("\n\t}");
        else code.append(ident).append("ret.V;\n\t}");

        this.ident = this.ident.substring(0, ident.length() - 1);
        return null;
    }

    private String getOppositeComp(String op){
        return switch (op) {
            case "<" -> ">=";
            case ">" -> "<=";
            case ">=" -> "<";
            case "<=" -> ">";
            case "==" -> "!=";
            case "!=" -> "==";
            default -> "";
        };
    }

    private Void dealWithComparison(JmmNode comparison, Void unused){
        if(midAssignment){
            String location = expressionVisitor.visitWrap(comparison, this.ident);
            stack.push(location);
            return null;
        }

        String op = getOppositeComp(comparison.get("op"));

        String left = expressionVisitor.visitWrap(comparison.getJmmChild(0), this.ident);
        String right = expressionVisitor.visitWrap(comparison.getJmmChild(1), this.ident);
        if(insideIf)
            code.append(this.ident).append("if(");
        code.append(left).append(" ").append(op).append(".bool ").append(right);
        return null;
    }

    private Void dealWithWhile(JmmNode jmmNode, Void unused) {
        code.append(this.ident).append("Loop:\n");
        this.ident += "\t";

        code.append("#");

        JmmNode comparison = jmmNode.getJmmChild(0);
        if(comparison.getKind().equals("Comparation")){
            insideIf = true;
            dealWithComparison(comparison, null);
            insideIf = false;
        }
        else{
            String location = expressionVisitor.visitWrap(jmmNode.getJmmChild(0), this.ident);
            code.append(this.ident).append("if (");
            code.append(location);
        }

        int aux = code.indexOf("#");
        if(aux != -1){
            String a = code.substring(aux).substring(1);
            code.delete(aux, code.length());
            code.append(a);
        }
        code.append(") goto EndLoop;\n");
        this.ident = this.ident.substring(0, ident.length() - 1);

        code.append(this.ident).append("Body:\n");
        this.ident += "\t";
        visit(jmmNode.getJmmChild(1));
        code.append(this.ident).append("goto Loop;\n");
        this.ident = this.ident.substring(0, ident.length() - 1);
        code.append(this.ident).append("EndLoop:\n");
        return null;
    }

    private Void dealWithIf(JmmNode jmmNode, Void unused) {
        JmmNode comparison = jmmNode.getJmmChild(0);
        JmmNode ifTrue = jmmNode.getJmmChild(1);

        if(comparison.getKind().equals("Comparation")){
            this.insideIf = true;
            dealWithComparison(comparison, null);
            this.insideIf = false;
        } else if(comparison.getKind().equals("Negation") || comparison.getKind().equals("MethodCall") || comparison.getKind().equals("BinaryOp")){
            String loc = expressionVisitor.visitWrap(comparison, this.ident);
            code.append("if(").append(loc);
        } else{
            code.append("if(");
            evaluateValue(comparison);
        }

        boolean hasElseBlock = jmmNode.getChildren().size() > 2;
        int ifCounter = ++ifCount; // Cache value for endif label, otherwise nested ifs fail
        if(hasElseBlock) code.append(") goto else").append(ifCount).append(";\n");
        else code.append(") goto endif").append(ifCount).append(";\n");

        visit(ifTrue);

        if(hasElseBlock){
            code.append(this.ident).append("goto endif").append(ifCount).append(";\n");
            JmmNode ifFalse = jmmNode.getJmmChild(2);
            this.ident = this.ident.substring(0, ident.length() - 1);
            code.append("\n").append(ident).append("else").append(ifCount).append(":\n");
            this.ident += "\t";
            visit(ifFalse);
        }

        this.ident = this.ident.substring(0, ident.length() - 1);
        code.append(ident).append("endif").append(ifCounter).append(":\n");
        this.ident += "\t";
        return null;
    }

    private void evaluateValue(JmmNode comparison) {
        if(comparison.getKind().equals("False"))
            code.append("0.bool");
        else if(comparison.getKind().equals("True"))
            code.append("1.bool");
        else{
            code.append(expressionVisitor.visitWrap(comparison, this.ident));
        }
    }

    private Void dealWithInteger(JmmNode jmmNode, Void v) {
        if(midAssignment)
            stack.push(jmmNode.get("value") + ".i32");
        else
            code.append(jmmNode.get("value") + ".i32");
        return null;
    }

    private Void dealWithAssignment(JmmNode jmmNode, Void v) {
        midAssignment = true;
        String var = jmmNode.get("var");
        String type = dealWithType(Objects.requireNonNull(getType(st, currentMethod, var)));

        boolean directExpression = false;
        String aux = "";
        JmmNode child = jmmNode.getJmmChild(0);
        if(child.getKind().equals("BinaryOp")
                && child.getJmmChild(0).getNumChildren() == 0
                && child.getJmmChild(1).getNumChildren() == 0
        ){
            directExpression = true;
            code.append(this.ident).append(var).append(type).append(" :=").append(type).append(" ");
            visit(jmmNode.getJmmChild(0));
        }
        else{
            aux = expressionVisitor.visitWrap(jmmNode.getJmmChild(0), this.ident);
        }


        String result = "";
        boolean assignmentOnField = OllirUtilities.variableIsField(st, currentMethod, var);

        if(assignmentOnField)
            result = this.ident + "putfield(this, " + var + type + ", ";
        else if(!directExpression)
            result = this.ident + var + type + " :=" + type + " ";

        result += aux;

        if(assignmentOnField)
            result += ").V";
        code.append(result).append(";\n");
        if(this.negation){
            this.negation = false;
            code.append(this.ident).append(var).append(type).append(" :=.bool !.bool ").append(var).append(type).append(";\n");
        }
        midAssignment = false;
        return null;
    }

    private Void dealWithIdentifier(JmmNode jmmNode, Void v) {
        String var = jmmNode.get("value");

        // It's a method and will be handled on a different place
        if(st.getMethods().contains(var)) return null;

        // Imports methods should be treated differently
        if(st.getImports().contains(var)){
            dealWithImportMethod(jmmNode.getJmmParent());
            return null;
        }

        // If it is a field getfield is needed
        if(OllirUtilities.variableIsField(st, currentMethod, var)) {
            String type = dealWithType(Objects.requireNonNull(getType(st, currentMethod, var)));
            String aux = this.ident + "temp" + type + ":=" + type;
            code.append(aux).append(" getfield(this, ").append(var).append(type).append(")").append(type).append(";\n");
            if(midAssignment)
                stack.push("temp" + type);
            else
                code.append("temp").append(type);
            return null;
        }

        List<Symbol> parameters = st.getParameters(currentMethod);
        Type type = getType(st, currentMethod, var);
        if(type == null) return null; // Any method of the import, we can't verify the methods
        String t = dealWithType(type);

        // If it is a parameter we need the $
        for(int i = 1; i <= parameters.size(); i++){
            if(parameters.get(i - 1).getName().equals(var)){
                if(midAssignment)
                    stack.push("$"+i+"."+var+t);
                else
                    code.append("$").append(i).append(".").append(var).append(t);
                return null;
            }
        }

        // Otherwise, it is a local variable
        if(midAssignment)
            stack.push(var+t);
        else
            code.append(var).append(t);
        return null;
    }

    private void dealWithImportMethod(JmmNode node) {
        breakSkip = true;

        JmmNode imp = node.getJmmChild(0);
        JmmNode method = node.getJmmChild(1);

        StringBuilder args = new StringBuilder();
        for(int i = 2; i < node.getNumChildren(); i++){
            String location = expressionVisitor.visitWrap(node.getJmmChild(i), this.ident);
            args.append(", ").append(location);
        }

        code.append(ident).append("invokestatic(").append(imp.get("value")).append(", \"").append(method.get("value")).append("\"").append(args).append(").V;\n");
    }

    private Void dealWithBinaryOp(JmmNode jmmNode, Void v) {
        String op = jmmNode.get("op");

        String locationLeft = expressionVisitor.visitWrap(jmmNode.getJmmChild(0), this.ident);
        String locationRight = expressionVisitor.visitWrap(jmmNode.getJmmChild(1), this.ident);

        code.append(locationLeft).append(" ").append(op).append(OllirUtilities.getTypeOfBinaryOp(op)).append(" ").append(locationRight);
        return null;
    }

    private Void dealWithMethod(JmmNode jmmNode, Void v) {
        currentMethod = jmmNode.get("methodName");
        expressionVisitor.setMethod(currentMethod);
        fieldsLoaded.clear();

        // Open method block
        code.append("\n\n").append(ident).append(".method public ");
        code.append(currentMethod);
        code.append("(");

        // Parameters
        List<Symbol> methodParameters = st.getParameters(currentMethod);
        if(!methodParameters.isEmpty()){
            int i = 0;
            for(Symbol s : methodParameters){
                code.append(i == 0 ? "" : ", ").append(s.getName()).append(dealWithType(s.getType()));
                i++;
            }
        }
        code.append(")");

        // Return type
        code.append(dealWithType(st.getReturnType(currentMethod)));
        code.append(" {\n");
        this.ident += "\t";

        boolean hasReturn = jmmNode.getJmmChild(0).getKind().equals("ReturnType");
        // Inside method
        for (JmmNode child : jmmNode.getChildren()){
            // Return
            if(child.getIndexOfSelf() == jmmNode.getNumChildren() - 1 && hasReturn){
                String location = expressionVisitor.visitWrap(child, this.ident);
                code.append(ident).append("ret").append(dealWithType(st.getReturnType(currentMethod))).append(" ").append(location);
                break;
            }

            visit(child);
        }

        // Close method block
        if(hasReturn) code.append(";");
        else code.append(ident).append("ret.V;");
        code.append("\n\t}");
        this.ident = this.ident.substring(0, ident.length() - 1);
        return null;
    }

    private Void skip(JmmNode jmmNode, Void v){
        for(JmmNode child : jmmNode.getChildren()){
            visit(child);
            if(breakSkip){ // Special case with imported method, don't visit children's
                breakSkip = false;
                return null;
            }
        }
        return null;
    }

    private String addDefaultConstructor(){
        return ".construct " + st.getClassName() + "().V {\n\t\tinvokespecial(this, \"<init>\").V;\n\t}";
    }

    private Void dealWithClass(JmmNode jmmNode, Void v){
        // Imports
        for (String i : st.getImports()){
            code.append("import ").append(i).append(";\n");
        }

        // Open class block
        code.append("\n").append(st.getClassName());
        if(st.getSuper() != null)
            code.append(" extends ").append(st.getSuper());
        code.append(" {\n");
        this.ident += "\t";

        // Class fields
        for(Symbol field : st.getFields()){
            code.append(ident).append(".field private ").append(field.getName()).append(dealWithType(field.getType())).append(";\n");
        }

        // Default constructor
        code.append("\n").append(ident).append(addDefaultConstructor());

        // Class Methods
        for(JmmNode child : jmmNode.getChildren()){
            visit(child);
        }

        // Close class block
        code.append("\n}");
        return null;
    }
}
