package pt.up.fe.comp2023.jasmin;

import org.specs.comp.ollir.*;

import java.util.*;
import java.util.stream.Collectors;

public class JasminUtilities {

    public static String getClassName(String className, ClassUnit classUnit) {
        if(Objects.equals(className, "this")) {
            return classUnit.getClassName();
        }

        for (String importName : classUnit.getImports()){
            if (importName.endsWith(className)){
                return importName.replaceAll("\\.", "/");
            }
        }

        return className.replace("\"", "");
    }

    public static String parseName(String name) {
        return name.replace("\"", "/");
    }

    // It may be required to change the Default later on
    // For now, lets keep it public
    public static String getAccessModifier(AccessModifiers accessModifier) {
        if(accessModifier == AccessModifiers.DEFAULT)
            return "public ";
        else
            return (accessModifier.name()).toLowerCase() + " ";
    }

    // Still have to check object references
    public static String getTypeDescriptor(Type type) {
        ElementType elementType = type.getTypeOfElement();
        StringBuilder stringBuilder = new StringBuilder();

        while(elementType == ElementType.ARRAYREF){
            stringBuilder.append("[");
            elementType = ((ArrayType) type).getElementType().getTypeOfElement();
        }

        switch (elementType) {
            case INT32 -> stringBuilder.append("I");
            case VOID -> stringBuilder.append("V");
            case BOOLEAN -> stringBuilder.append("Z");
            case STRING -> stringBuilder.append("Ljava/lang/String;");
            case CLASS -> stringBuilder.append("Ljava/lang/Class;");
            case OBJECTREF -> {
                stringBuilder.append("L");
                String objectName = ((ClassType) type).getName().replace("\"", "");
                stringBuilder.append(objectName).append(";");

            }
            default -> stringBuilder.append("; ERROR: Field type descriptor not present\n");
        }

        return stringBuilder.toString();
    }

    public static String getLabels(List<String> labels) { // TODO check what label it wants to return, is it only the first?
        StringBuilder stringBuilder = new StringBuilder();

        for (String label : labels) {
            stringBuilder.append(label
                    .replace("=", "")
                    .replace(":", "")
                    .replace(".", "")
                    .replace("\"", "")
                    .replace("-", "")
            ).append(":\n");
        }
        return stringBuilder.toString();
    }

    public static String getOperation(Operation operation) {
        return switch (operation.getOpType()) {
            case ADD -> "iadd";
            case SUB -> "isub";
            case MUL -> "imul";
            case DIV -> "idiv";
            case LTH -> "if_icmplt";
            case GTH -> "if_icmpgt";
            case EQ -> "if_icmpeq";
            case NEQ -> "if_icmpne";
            case LTE -> "if_icmple";
            case GTE -> "if_icmpge";
            case ANDB -> "iand";
            case ORB -> "ior";
            case NOTB -> "ifeq";
            default -> "; Something when wrong with the operation";
        };
    }

    public static String getVariableIndex(String name, HashMap<String, Descriptor> varTable) {
        if(name.equals("this")) return "_0";

        StringBuilder stringBuilder = new StringBuilder();

        int register = varTable.get(name).getVirtualReg(); // TODO it seems this line fails in some cases

        if(register <= 3) stringBuilder.append("_");
        else stringBuilder.append(" ");

        stringBuilder.append(register);

        return stringBuilder.toString();
    }

    public static boolean isByte (int value) {
        return (value >= -128 && value <= 127);
    }

    public static boolean isShort (int value) {
        return (value >= -32768 && value <= 32767);
    }

    public static boolean isBooleanOperation (OperationType operationType) {
        return  operationType == OperationType.GTH ||
                operationType == OperationType.GTE ||
                operationType == OperationType.LTH ||
                operationType == OperationType.LTE ||
                operationType == OperationType.EQ ||
                operationType == OperationType.NEQ;
    }

    public static int getLimitLocals(Method method) {
        Set<Integer> virtualRegs = method.getVarTable().values().stream()
                .map(Descriptor::getVirtualReg)
                .collect(Collectors.toCollection(TreeSet::new));
        virtualRegs.add(0);
        return virtualRegs.size();
    }
}
