package pt.up.fe.comp2023.ast;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;

public class SymbolTableBuilder extends AJmmVisitor<ASymbolTable, Void> {
    @Override
    protected void buildVisitor() {
        setDefaultVisit(this::skip);
        addVisit("ImportDeclaration", this::dealWithImport);
        addVisit("ClassDeclaration", this::dealWithClass);
        addVisit("Method", this::dealWithMethod);
        addVisit("MainMethod", this::dealWithMainMethod);
    }

    private Void skip(JmmNode jmmNode, ASymbolTable st){
        for(JmmNode child : jmmNode.getChildren())
            visit(child, st);
        return null;
    }

    private Void dealWithImport(JmmNode jmmNode, ASymbolTable st){
        StringBuilder fullyQualifiedName = new StringBuilder();
        fullyQualifiedName.append(jmmNode.get("name"));

        for(var child : jmmNode.getChildren())
            fullyQualifiedName.append('.').append(child.get("name"));

        st.addImport(fullyQualifiedName.toString());
        return null;
    }

    private Void dealWithClass(JmmNode jmmNode, ASymbolTable st){
        st.setClassName(jmmNode.get("className"));
        if(jmmNode.getAttributes().contains("extendName"))
            st.setSuper(jmmNode.get("extendName"));
        for(JmmNode child : jmmNode.getChildren()){
            if(child.getKind().equals("VarDeclaration"))
                st.addField(dealWithVariable(child));
            else
                visit(child, st);
        }
        return null;
    }

    private Symbol dealWithVariable(JmmNode jmmNode){
        return new Symbol(dealWithType(jmmNode.getJmmChild(0)), jmmNode.get("name"));
    }

    private Type dealWithType(JmmNode jmmNode){
        if(jmmNode.getKind().equals("Array")){
            var childType = dealWithType(jmmNode.getJmmChild(0));
            return new Type(childType.getName(), true);
        }
        return new Type(jmmNode.get("value"), false);
    }

    private Type dealWithReturnType(JmmNode jmmNode){
        if(jmmNode.getKind().equals("ReturnVoid"))
            return new Type("void", false);
        else
            return dealWithType(jmmNode.getJmmChild(0));
    }

    private Void dealWithMethod(JmmNode jmmNode, ASymbolTable st){
        MethodInfo info = new MethodInfo();
        info.setName(jmmNode.get("methodName"));
        for(var child : jmmNode.getChildren()){
            if(child.getIndexOfSelf() == 0)
                info.setReturnType(dealWithReturnType(child));
            else if(child.getKind().equals("Parameter")) {
                info.addParameter(dealWithVariable(child));
            } else if(child.getKind().equals("VarDeclaration")){
                info.addVariable(dealWithVariable(child));
            }
        }
        st.addMethod(info);
        return null;
    }

    private Void dealWithMainMethod(JmmNode jmmNode, ASymbolTable st){
        MethodInfo info = new MethodInfo();
        info.setName("main");
        info.setReturnType(new Type("void", false));
        info.addParameter(new Symbol(new Type("String", true), jmmNode.get("argName")));
        for(var child : jmmNode.getChildren()){
            if(child.getKind().equals("VarDeclaration")){
                info.addVariable(dealWithVariable(child));
            }
        }
        st.addMethod(info);
        return null;
    }
}
