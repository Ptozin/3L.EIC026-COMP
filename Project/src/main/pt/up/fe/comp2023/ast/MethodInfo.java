package pt.up.fe.comp2023.ast;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;

import java.util.ArrayList;
import java.util.List;

public class MethodInfo {
    private String name;
    private Type returnType;
    private final List<Symbol> parameters;
    private final List<Symbol> variables;

    private final List<Boolean> varUsed;

    private final List<String> varVal;

    public MethodInfo(){
        parameters = new ArrayList<>();
        variables = new ArrayList<>();
        varUsed = new ArrayList<>();
        varVal = new ArrayList<>();
    }

    public String getName(){
        return name;
    }

    public void setName(String name){
        this.name = name;
    }

    public Type getReturnType(){
        return returnType;
    }

    public void setReturnType(Type type){
        returnType = type;
    }

    public List<Symbol> getParameters(){
        return parameters;
    }

    public void addParameter(Symbol parameter){
        parameters.add(parameter);
    }

    public List<Symbol> getVariables(){
        return variables;
    }

    public void addVariable(Symbol var){
        variables.add(var);
        varUsed.add(false);
        varVal.add("");
    }

    //Constant Propagation
    public boolean wasVarUsed(String var) {
        for (int i = 0; i < variables.size(); i++) {
            if (variables.get(i).getName().equals(var))
                return varUsed.get(i);
        }
        return false;
    }

    public void setVarUsed(String var, Boolean bool) {
        for (int i = 0; i < variables.size(); i++) {
            if (variables.get(i).getName().equals(var))
                varUsed.set(i, bool);
        }
    }

    public String getVarValue(String var) {
        for (int i = 0; i < variables.size(); i++) {
            if (variables.get(i).getName().equals(var))
                return varVal.get(i);
        }
        return "";
    }

    public void setVarVal(String var, String val) {
        for (int i = 0; i < variables.size(); i++) {
            if (variables.get(i).getName().equals(var)) {
                varVal.set(i, val);
                varUsed.set(i, true);
            }
        }

    }

    public void resetVarInfo() {
        for (int i = 0; i < varVal.size(); i++) {
            varVal.set(i, "");
            varUsed.set(i, false);
        }
    }
}
