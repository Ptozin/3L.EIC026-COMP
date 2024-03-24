package pt.up.fe.comp2023.ast;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ASymbolTable implements SymbolTable {
    List<String> imports;
    String className;
    String superName;
    List<Symbol> fields;
    Map<String, MethodInfo> methods;

    public ASymbolTable(){
        imports = new ArrayList<>();
        fields = new ArrayList<>();
        methods = new HashMap<>();
    }

    @Override
    public List<String> getImports() {
        return imports;
    }

    public void addImport(String name){
        imports.add(name);
    }

    @Override
    public String getClassName() {
        return className;
    }

    public void setClassName(String className){
        this.className = className;
    }

    @Override
    public String getSuper() {
        return superName;
    }

    public void setSuper(String superName){
        this.superName = superName;
    }

    @Override
    public List<Symbol> getFields() {
        return fields;
    }

    public void addField(Symbol field){
        fields.add(field);
    }

    @Override
    public List<String> getMethods() {
        return new ArrayList<>(methods.keySet());
    }

    public void addMethod(MethodInfo method){
        methods.put(method.getName(), method);
    }

    @Override
    public Type getReturnType(String s) {
        return methods.get(s).getReturnType();
    }

    @Override
    public List<Symbol> getParameters(String s) {
        return methods.get(s).getParameters();
    }

    @Override
    public List<Symbol> getLocalVariables(String s) {
        return methods.get(s).getVariables();
    }

    //Constant Propagation
    public void setVarVal(String method, String var, String val) {
        methods.get(method).setVarVal(var, val);
    }

    public String getVarVal(String method, String var) {
        methods.get(method).setVarUsed(var, true);
        return methods.get(method).getVarValue(var);
    }

    public Boolean wasVarUsed(String method, String var) {
        return methods.get(method).wasVarUsed(var);
    }

    public void resetVarInfo(String method) {
        methods.get(method).resetVarInfo();
    }
}
