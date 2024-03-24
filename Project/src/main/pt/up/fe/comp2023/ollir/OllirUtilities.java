package pt.up.fe.comp2023.ollir;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;

public class OllirUtilities {
    public static boolean variableIsField(SymbolTable st, String currentMethod, String var){
        for(Symbol s : st.getLocalVariables(currentMethod)){
            if(s.getName().equals(var))
                return false;
        }
        for(Symbol s : st.getParameters(currentMethod)){
            if(s.getName().equals(var))
                return false;
        }
        for(Symbol s : st.getFields()){
            if(s.getName().equals(var))
                return true;
        }
        return false;
    }

    public static Type getType(SymbolTable st, String method, String var){
        for(Symbol s : st.getLocalVariables(method)){
            if(s.getName().equals(var))
                return s.getType();
        }

        for(Symbol s : st.getParameters(method)){
            if(s.getName().equals(var))
                return s.getType();
        }

        for(Symbol s : st.getFields()){
            if(s.getName().equals(var))
                return s.getType();
        }

        return null;
    }

    public static String dealWithType(Type type){
        String result = ".";
        if(type.isArray())
            result += "array.";

        switch (type.getName()){
            case "int" -> result += "i32";
            case "void" -> result += "V";
            case "boolean" -> result += "bool";
            default -> result += type.getName();
        }

        return result;
    }

    public static String getTypeOfBinaryOp(String op){
        return switch (op){
            case "&&", "||" -> ".bool";
            default -> ".i32";
        };
    }
}
