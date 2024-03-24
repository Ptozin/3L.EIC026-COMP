package pt.up.fe.comp2023.ollir;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static pt.up.fe.comp2023.ollir.OllirUtilities.*;

public class OllirExpressionVisitor extends AJmmVisitor<String, String> {
    private final StringBuilder code;
    private final SymbolTable st;
    private String currentMethod;
    private String ident;
    private Map<String, String> fieldsLoaded;

    private int auxCounter = 0;
    private boolean negation = false;

    public OllirExpressionVisitor(StringBuilder code, SymbolTable st, Map<String, String> fieldsLoaded, String ident){
        this.code = code;
        this.currentMethod = "";
        this.st = st;
        this.ident = ident;
        this.fieldsLoaded = fieldsLoaded;
    }

    public void setMethod(String method){
        this.currentMethod = method;
    }

    @Override
    protected void buildVisitor() {
        setDefaultVisit(this::skip);
        addVisit("MethodCall", this::dealWithMethodCall);
        addVisit("Identifier", this::dealWithIdentifier);
        addVisit("BinaryOp", this::dealWithBinaryOp);
        addVisit("Integer", this::dealWithInteger);
        addVisit("NewClass", this::dealWithNewClass);
        addVisit("This", this::dealWithThis);
        addVisit("True", this::dealWithTrue);
        addVisit("False", this::dealWithFalse);
        addVisit("Comparation", this::dealWithComparasion);
        addVisit("IndexAccess", this::dealWithIndexAccess);
        addVisit("MethodLength", this::dealWithMethodLength);
        addVisit("NewIntArray", this::dealWithNewIntArray);
        addVisit("Negation", this::dealWithNegation);
    }

    private String dealWithNegation(JmmNode jmmNode, String s) {
        this.negation = true;
        return visit(jmmNode.getJmmChild(0));
    }

    public String visitWrap(JmmNode node, String ident){
        this.ident = ident;
        //this.auxCounter = 0;
        return visit(node);
    }

    private String dealWithNewIntArray(JmmNode jmmNode, String s) {
        String sizeloc = visit(jmmNode.getJmmChild(0));
        code.append(ident).append("aux").append(++auxCounter).append(".array.i32 :=.array.i32 new(array, ").append(sizeloc).append(").array.i32;\n");
        return "aux" + auxCounter + ".array.i32";
    }

    private String dealWithMethodLength(JmmNode jmmNode, String s) {
        if(!jmmNode.getJmmChild(0).getKind().equals("Identifier"))
            return null;

        int i = code.toString().indexOf("#");
        String saved = "";
        if(i != -1){
            saved = code.substring(i);
            code.delete(i, code.length());
        }

        String aux = ident+"aux"+(++auxCounter)+".i32 :=.i32 "+"arraylength(";
        String loc = visit(jmmNode.getJmmChild(0));
        code.append(aux).append(loc);
        code.append(").i32;\n");
        if(saved.length() != 0)
            code.append(saved.substring(1));
        return "aux" + auxCounter + ".i32";
    }

    private String dealWithIndexAccess(JmmNode jmmNode, String s) {
        String var = jmmNode.getJmmChild(0).get("value");
        Type arrayType = getType(st, currentMethod, var);

        // If array in a field
        if(fieldsLoaded.containsKey(var))
            var = fieldsLoaded.get(var);
        else if(variableIsField(st, currentMethod, var)){
            String _type = dealWithType(Objects.requireNonNull(arrayType));
            String aux = this.ident + "temp" + _type + ":=" + _type;
            code.append(aux).append(" getfield(this, ").append(var).append(_type).append(")").append(_type).append(";\n");
            var = "temp";
            fieldsLoaded.put(var, "temp");
        }

        // If array is a parameter
        List<Symbol> parameters = st.getParameters(currentMethod);
        int parameterIndex = -1;
        for (int i = 1; i <= parameters.size(); i++) {
            if (parameters.get(i - 1).getName().equals(var)) {
                parameterIndex = i;
            }
        }

        String indexLocation;
        if(!jmmNode.getJmmChild(1).getKind().equals("Identifier")){
            code.append(ident + "aux" + (++auxCounter) + ".i32" + " :=.i32 " + visit(jmmNode.getJmmChild(1)) + ";\n");
            indexLocation = "aux" + auxCounter;
        } else
            indexLocation = jmmNode.getJmmChild(1).get("value");


        String typeElements = dealWithType(new Type(Objects.requireNonNull(arrayType).getName(), false));

        String aux = "";
        aux += (parameterIndex != -1 ? "$" + parameterIndex + "." : "") + var + "[";
        aux += indexLocation;
        aux += ".i32]" + typeElements;
        code.append(ident + "aux" + (++auxCounter) + typeElements + " :=" + typeElements + " " + aux + ";\n");
        return "aux" + auxCounter + typeElements;
    }

    private String dealWithComparasion(JmmNode jmmNode, String s) {
        String left = visit(jmmNode.getJmmChild(0));
        String right = visit(jmmNode.getJmmChild(1));

        String aux = ident + "aux" + (++auxCounter) + ".bool :=.bool ";

        code.append(aux).append(left).append(" ").append(jmmNode.get("op")).append(".bool ").append(right).append(";\n");
        if(this.negation){
            this.negation = false;
            code.append(this.ident).append("aux").append(auxCounter).append(".bool :=.bool !.bool ").append("aux").append(auxCounter).append(".bool;\n");
        }
        return "aux" + auxCounter + ".bool";
    }

    private String dealWithTrue(JmmNode jmmNode, String s) {
        if(this.negation) return "0.bool";
        return "1.bool";
    }

    private String dealWithFalse(JmmNode jmmNode, String s) {
        if(this.negation) return "1.bool";
        return "0.bool";
    }

    private String dealWithThis(JmmNode jmmNode, String s) {
        return "this." + st.getClassName();
    }

    private String dealWithNewClass(JmmNode jmmNode, String s) {
        String className = jmmNode.get("className");
        code.append(ident + "aux" + (++auxCounter) + "." + className + " :=." + className + " new(" + className + ")." + className + ";\n");
        code.append(ident).append("invokespecial(aux" + auxCounter + "." + className + ", \"<init>\").V;\n");
        return "aux" + auxCounter + "." + className;
    }

    private String dealWithInteger(JmmNode jmmNode, String s) {
        return jmmNode.get("value") + ".i32";
    }

    private String dealWithBinaryOp(JmmNode jmmNode, String s) {
        String left = visit(jmmNode.getJmmChild(0));
        String right = visit(jmmNode.getJmmChild(1));

        String optype = OllirUtilities.getTypeOfBinaryOp(jmmNode.get("op"));
        String aux = ident + "aux" + (++auxCounter) + optype + " :=" + optype + " ";

        code.append(aux).append(left).append(" ").append(jmmNode.get("op")).append(optype).append(" ").append(right).append(";\n");
        if(this.negation){
            this.negation = false;
            code.append(this.ident).append("aux").append(auxCounter).append(".bool :=.bool !.bool ").append("aux").append(auxCounter).append(".bool;\n");
        }
        return "aux" + auxCounter + optype;
    }

    private String dealWithIdentifier(JmmNode jmmNode, String ident) {
        String var = jmmNode.get("value");

        List<Symbol> parameters = st.getParameters(currentMethod);
        String t = dealWithType(Objects.requireNonNull(getType(st, currentMethod, var)));

        for(int i = 1; i <= parameters.size(); i++){
            if(parameters.get(i - 1).getName().equals(var)){
                if(this.negation){
                    this.negation = false;
                    code.append(this.ident).append("aux").append(auxCounter).append(".bool :=.bool !.bool ").append("aux").append(auxCounter).append(".bool;\n");
                    return "aux" + auxCounter + ".bool";
                }
                return "$" + i + "." + var + t;
            }
        }

        if(OllirUtilities.variableIsField(st, currentMethod, var)) {
            String typeStr = dealWithType(Objects.requireNonNull(getType(st, currentMethod, var)));
            String aux = this.ident + "aux" + (++auxCounter) + typeStr + " :=" + typeStr;
            code.append(aux).append(" getfield(this, ").append(var).append(typeStr).append(")").append(typeStr).append(";\n");
            if(this.negation){
                this.negation = false;
                code.append(this.ident).append("aux").append(auxCounter).append(".bool :=.bool !.bool ").append("aux").append(auxCounter).append(".bool;\n");
                return "aux" + auxCounter + ".bool";
            }
            return "aux" + auxCounter + OllirUtilities.dealWithType(Objects.requireNonNull(getType(st, currentMethod, var)));
        }

        for(Symbol local : st.getLocalVariables(currentMethod)){
            if(local.getName().equals(var)){
                if(this.negation){
                    this.negation = false;
                    code.append(this.ident).append("aux").append(++auxCounter).append(".bool :=.bool !.bool ").append("aux").append(auxCounter).append(".bool;\n");
                    return "aux" + auxCounter + ".bool";
                }
                return var + t;
            }
        }

        return null;
    }

    private String dealWithImportMethod(JmmNode node) {
        JmmNode imp = node.getJmmChild(0);
        JmmNode method = node.getJmmChild(1);

        StringBuilder args = new StringBuilder();
        for(int i = 2; i < node.getNumChildren(); i++){
            String location = visit(node.getJmmChild(i));
            args.append(", ").append(location);
        }

        code.append(ident).append("aux").append(++auxCounter).append(".i32 :=.i32 ").append("invokestatic(").append(imp.get("value")).append(", \"").append(method.get("value")).append("\"").append(args).append(").i32;\n");
        return "aux" + auxCounter + ".i32";
    }

    private String dealWithMethodCall(JmmNode jmmNode, String str) {
        if(jmmNode.getJmmChild(0).hasAttribute("value") && getType(st, currentMethod, jmmNode.getJmmChild(0).get("value")) == null)
            return dealWithImportMethod(jmmNode);

        JmmNode origin = jmmNode.getJmmChild(0);
        String o = visit(origin);

        JmmNode method = jmmNode.getJmmChild(1);
        String aux = "invokevirtual(" + o + ", \"";
        aux += method.get("value") + "\"";

        for(int i = 2; i < jmmNode.getNumChildren(); i++){
            aux += ", " + visit(jmmNode.getJmmChild(i));
        }
        aux += ")";
        String t = "";
        if(!st.getMethods().contains(method.get("value")))
            t = ".i32"; // We can't know the return type of imported methods
        else
            t = dealWithType(st.getReturnType(method.get("value")));
        aux += t + ";";

        code.append(ident + "aux" + (++auxCounter) + t + " :=" + t + " " + aux + "\n");
        if(this.negation){
            this.negation = false;
            code.append(this.ident).append("aux").append(auxCounter).append(".bool :=.bool !.bool ").append("aux").append(auxCounter).append(".bool;\n");
        }
        return "aux" + auxCounter + t;
    }

    private String skip(JmmNode jmmNode, String str) {
        if(jmmNode.getNumChildren() == 0) return "";
        return visit(jmmNode.getJmmChild(0));
    }
}
