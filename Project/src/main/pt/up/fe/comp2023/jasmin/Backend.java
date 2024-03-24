package pt.up.fe.comp2023.jasmin;

import org.specs.comp.ollir.*;
import pt.up.fe.comp.jmm.jasmin.JasminBackend;
import pt.up.fe.comp.jmm.jasmin.JasminResult;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;

import java.util.*;

import static  pt.up.fe.comp2023.jasmin.JasminUtilities.*;

public class Backend implements JasminBackend {

    private ClassUnit ollirClass;
    private StringBuilder jasminCode;
    private String superClass; // Need to save the superclass to use in invoke special

    private int conditionNum = 0;
    private int currentStack = 0;

    private int limitStack = 0;

    @Override
    public JasminResult toJasmin(OllirResult ollirResult) {
        this.ollirClass = ollirResult.getOllirClass();
        this.jasminCode = new StringBuilder();
        List<Report> reports = new ArrayList<>();

        this.generator();

        return new JasminResult(ollirResult, this.jasminCode.toString(), reports);
    }

    /**
     * Generates the Jasmin code from the OLLIR code.
     * Calls the main functions for each part of the Jasmin code
     */
    private void generator() {
        this.generateClass();
        this.generateSuperClass();
        this.generateFields();
        this.generateMethods();

        // Uncomment this line to see the generated jasmin code
        System.out.println("\n" + this.jasminCode.toString() + "\n");
    }

    /**
     * Generates the class declaration.
     * The class declaration is defined as:
     *      .class <access-spec> <class-name>
     *
     */
    private void generateClass() {
        this.jasminCode.append(".class ");
        this.jasminCode.append(getAccessModifier(this.ollirClass.getClassAccessModifier()));

        if(this.ollirClass.isStaticClass())
            this.jasminCode.append("static ");

        if(this.ollirClass.isFinalClass())
            this.jasminCode.append("final ");


        this.jasminCode.append(this.ollirClass.getClassName()).append("\n");
    }

    /**
     * Generates the superclass declaration.
     * The superclass declaration is defined as:
     *      .super <superclass-name>
     */
    private void generateSuperClass() {
        if(this.ollirClass.getSuperClass() == null)
            this.superClass = "java/lang/Object";
        else
            this.superClass = this.ollirClass.getSuperClass();

        this.jasminCode.append(".super ").append(getClassName(this.superClass, this.ollirClass)).append("\n");
    }

    /**
     * Generates the fields' declaration.
     * The fields declaration is defined as:
     *      .field <access-spec> <field-name> <descriptor> [ = <value> ]
     */
    private void generateFields() {
        for (Field field : this.ollirClass.getFields()) {
            this.jasminCode.append(".field ");

            if(field.isStaticField())
                this.jasminCode.append("static ");

            if(field.isFinalField())
                this.jasminCode.append("final ");

            this.jasminCode.append(field.getFieldName()).append(" ")
                    .append(getTypeDescriptor(field.getFieldType()));

            if(!Objects.equals(field.getFieldName(), ""))
                this.jasminCode.append(" = ").append(field.getInitialValue());

            this.jasminCode.append("\n");
        }
    }

    /**
     * Generates the methods' declaration.
     * The methods declaration is defined as:
     *     .method <access-spec> <method-spec>
     *         <statements>
     *     .end method
     */
    private void generateMethods() {
        for(Method method : this.ollirClass.getMethods()) {
            this.jasminCode.append(".method ");

            this.jasminCode.append(getAccessModifier(method.getMethodAccessModifier()));

            if(method.isStaticMethod())
                this.jasminCode.append("static ");

            if(method.isFinalMethod())
                this.jasminCode.append("final ");

            if(method.isConstructMethod())
                this.jasminCode.append("<init>");
            else
                this.jasminCode.append(method.getMethodName().replace("\"", ""));

            this.jasminCode.append("(");

            for(Element param : method.getParams())
                this.jasminCode.append(getTypeDescriptor(param.getType()));

            this.jasminCode.append(")");

            this.jasminCode.append(getTypeDescriptor(method.getReturnType())).append("\n");

            this.jasminCode.append(this.getMethodStatements(method));

            this.jasminCode.append(".end method\n\n");

        }
    }

    /**
     * Generates the statements of a method.
     * The statements are defined as:
     *      .limit stack <limit-stack>
     *      .limit locals <limit-locals>
     *      <statements>
     */
    private String getMethodStatements(Method method) {
        int limitLocals = getLimitLocals(method);
        this.currentStack = 0;
        this.limitStack = 0;

        String statementInstructions = this.getStatementInstructions(method);

        return ".limit stack " + this.limitStack + "\n" +
                ".limit locals " + limitLocals + "\n" +
                statementInstructions;
    }

    /**
     * Generates the statement instructions
     * Consists of a sequence of newline-separated statements. There are three types of statement
     *     <labels>
     *     <instructions>
     *     <directives>
     */
    private String getStatementInstructions(Method method) {
        StringBuilder stringBuilder = new StringBuilder();
        List<String> emptyString = Collections.emptyList();
        boolean hasReturn = false;

        for(Instruction instruction : method.getInstructions()) {
            if(method.getLabels(instruction) != emptyString)
                stringBuilder.append(getLabels(method.getLabels(instruction)));

            stringBuilder.append(this.getInstruction(instruction, method.getVarTable()));

            // If the method has a return type, we need to pop the return value from the stack
            if(instruction.getInstType() == InstructionType.RETURN) {
                hasReturn = true;
            }
            else if(instruction.getInstType() == InstructionType.CALL
                    && ((CallInstruction) instruction).getReturnType().getTypeOfElement() != ElementType.VOID)  {
                stringBuilder.append("pop\n");

                this.updateStackLimits(-1);
            }
        }

        // Some void methods don't have a return statement, so we need to add it
        if (!hasReturn && method.getReturnType().getTypeOfElement().equals(ElementType.VOID))
            stringBuilder.append("return\n");

        return stringBuilder.toString();
    }

    /**
     * Retrieves the instructions of a method.
     * Assigns the corresponding instruction type to each instruction.
     */
    private String getInstruction(Instruction instruction, HashMap<String, Descriptor> varTable) {
        return switch (instruction.getInstType()) {
            case ASSIGN -> this.getAssignInstruction((AssignInstruction) instruction, varTable);
            case CALL -> this.getCallInstruction((CallInstruction) instruction, varTable);
            case GOTO -> this.getGotoInstruction((GotoInstruction) instruction);
            case BRANCH -> this.getBranchInstruction((CondBranchInstruction) instruction, varTable);
            case RETURN -> this.getReturnInstruction((ReturnInstruction) instruction, varTable);
            case PUTFIELD -> this.getPutFieldInstruction((PutFieldInstruction) instruction, varTable);
            case GETFIELD -> this.getGetFieldInstruction((GetFieldInstruction) instruction, varTable);
            case UNARYOPER -> this.getUnaryOperationInstruction((UnaryOpInstruction) instruction, varTable);
            case BINARYOPER -> this.getBinaryOperationInstruction((BinaryOpInstruction) instruction, varTable);
            case NOPER -> this.addToStack(((SingleOpInstruction) instruction).getSingleOperand(), varTable);
        };
    }

    /**
     * Generates the Assign instruction.
     * In case of receiving an array operand, we need to load the array and the index.
     * In case of receiving a binary instruction, either of the types ADD or SUB, we need to check if it is possible to
     *    use the iinc instruction instead.
     * Else, we retrieve the instruction of the right hand side and store it in the destination variable.
     */
    private String getAssignInstruction(AssignInstruction instruction, HashMap<String, Descriptor> varTable) {
        StringBuilder stringBuilder = new StringBuilder();

        if(instruction.getDest() instanceof ArrayOperand arrayOperand) {
            this.updateStackLimits(1);

            stringBuilder.append("aload").append(getVariableIndex(arrayOperand.getName(), varTable)).append("\n");
            stringBuilder.append(this.addToStack(arrayOperand.getIndexOperands().get(0), varTable));
        } else if (instruction.getRhs().getInstType() == InstructionType.BINARYOPER) {
            BinaryOpInstruction binaryOpInstruction = (BinaryOpInstruction) instruction.getRhs();

            OperationType operationType = binaryOpInstruction.getOperation().getOpType();

            if (operationType == OperationType.ADD || operationType == OperationType.SUB) {
                boolean left = binaryOpInstruction.getLeftOperand().isLiteral();
                boolean right = binaryOpInstruction.getRightOperand().isLiteral();
                if( !(left == right)) {
                    LiteralElement literal = (LiteralElement) (left ? binaryOpInstruction.getLeftOperand() : binaryOpInstruction.getRightOperand());
                    String operandName = ((Operand) (left ? binaryOpInstruction.getRightOperand() : binaryOpInstruction.getLeftOperand())).getName();

                    if(operandName.equals(((Operand)instruction.getDest()).getName())) {
                        int value = Integer.parseInt(literal.getLiteral());

                        if (operationType == OperationType.SUB)
                            if (value > 0)
                                value = - value;

                        if(isByte(value)) {
                            return "iinc " + varTable.get(operandName).getVirtualReg() + " " + value + "\n";
                        }
                    }
                }
            }
        }

        stringBuilder.append(this.getInstruction(instruction.getRhs(), varTable));
        stringBuilder.append(getStore((Operand) instruction.getDest(), varTable));

        return stringBuilder.toString();
    }

    /**
     * Generates the Call instruction.
     * Verifies which type of invocation we are dealing with:
     *     invokevirtual
     *     invokestatic
     *     invokespecial
     *     NEW
     *     arraylength
     *     ldc
     */
    private String getCallInstruction(CallInstruction instruction, HashMap<String, Descriptor> varTable) {
        StringBuilder stringBuilder = new StringBuilder();

        int popValue = 0;

        switch (instruction.getInvocationType()) {
            case invokevirtual -> {
                stringBuilder.append(this.addToStack(instruction.getFirstArg(), varTable));

                for(Element element: instruction.getListOfOperands()) {
                    popValue--;
                    stringBuilder.append(this.addToStack(element, varTable));
                }
                // From here
                stringBuilder.append("invokevirtual ");

                Operand first = (Operand) instruction.getFirstArg();

                ClassType classType = (ClassType) first.getType();

                stringBuilder.append(classType.getName());

                stringBuilder.append("/").append(parseName(((LiteralElement) instruction.getSecondArg()).getLiteral())
                        .replace("/", "")).append("(");

                for(Element element: instruction.getListOfOperands())
                    stringBuilder.append(getTypeDescriptor(element.getType()));

                stringBuilder.append(")").append(getTypeDescriptor(instruction.getReturnType())).append("\n");

                if (instruction.getReturnType().getTypeOfElement() == ElementType.VOID)
                    popValue--;

            }
            case invokespecial -> {
                stringBuilder.append(this.addToStack(instruction.getFirstArg(), varTable));
                stringBuilder.append("invokespecial ");

                if(instruction.getFirstArg().getType().getTypeOfElement() == ElementType.THIS)
                    stringBuilder.append(this.superClass);
                else
                    stringBuilder.append(getClassName(((ClassType) instruction.getFirstArg().getType()).getName(), this.ollirClass));

                stringBuilder.append("/<init>(");

                for(Element element: instruction.getListOfOperands())
                    stringBuilder.append(getTypeDescriptor(element.getType()));

                stringBuilder.append(")").append(getTypeDescriptor(instruction.getReturnType())).append("\n");

                if (instruction.getReturnType().getTypeOfElement() == ElementType.VOID)
                    popValue--;

            }
            case invokestatic -> {
                for (Element element : instruction.getListOfOperands()) {
                    popValue--;
                    stringBuilder.append(this.addToStack(element, varTable));
                }

                stringBuilder.append("invokestatic ")
                        .append(getClassName(((Operand) instruction.getFirstArg()).getName(), this.ollirClass))
                        .append("/").append(parseName(((LiteralElement) instruction.getSecondArg()).getLiteral()).replace("/", "")).append("(");

                for (Element element : instruction.getListOfOperands()) {
                    stringBuilder.append(getTypeDescriptor(element.getType()));
                }

                stringBuilder.append(")").append(getTypeDescriptor(instruction.getReturnType())).append("\n");

                if (instruction.getReturnType().getTypeOfElement() != ElementType.VOID)
                    popValue++;

            }
            case NEW -> {
                popValue++;

                ElementType elementType = instruction.getReturnType().getTypeOfElement();

                if(elementType.equals(ElementType.OBJECTREF)) {
                    for (Element element : instruction.getListOfOperands()) {
                        popValue--;
                        stringBuilder.append(addToStack(element, varTable));
                    }

                    stringBuilder.append("new ").append(getClassName(((Operand) instruction.getFirstArg()).getName(), this.ollirClass))
                            .append("\n"); //.append("dup\n"); // Check this, why doesn't it work now?
                }
                else if(elementType.equals(ElementType.ARRAYREF)) {
                    for (Element element : instruction.getListOfOperands()) {
                        popValue--;
                        stringBuilder.append(addToStack(element, varTable));
                    }

                    stringBuilder.append("newarray ");

                    if(instruction.getListOfOperands().get(0).getType().getTypeOfElement().equals(ElementType.INT32))
                        stringBuilder.append("int\n");
                }
            }
            case arraylength -> {
                stringBuilder.append(addToStack(instruction.getFirstArg(), varTable));
                stringBuilder.append("arraylength\n");
            }
            case ldc -> stringBuilder.append(addToStack(instruction.getFirstArg(), varTable));
            case invokeinterface -> {
                // SKIP, not used in OLLIR
            }
        }

        this.updateStackLimits(popValue);

        return stringBuilder.toString();
    }

    /**
     * Generates the Goto instruction.
     * Selects the label to jump to.
     */
    private String getGotoInstruction(GotoInstruction instruction) {
        return "goto " + instruction.getLabel() + " ; im in goto instruct\n";
    }

    /**
     * Generates the Branch instruction.
     * First verifies the instruction condition:
     *    BinaryOper
     *      LTH and GTE -> if_icmplt and if_icmpge or if_icmpge and if_icmplt
     *      AND -> ifeq
     *    UnaryOper
     */
    private String getBranchInstruction(CondBranchInstruction instruction, HashMap<String, Descriptor> varTable) {
        StringBuilder stringBuilder = new StringBuilder();

        Instruction condition = instruction.getCondition();

        String operation = "";

        if(instruction instanceof SingleOpCondInstruction singleOpInstruction)
            condition = singleOpInstruction.getCondition();
        else if(instruction instanceof OpCondInstruction opCondInstruction)
            condition = opCondInstruction.getCondition();

        switch (condition.getInstType()) {
            case BINARYOPER -> {
                assert condition instanceof BinaryOpInstruction;
                BinaryOpInstruction binaryOperation = (BinaryOpInstruction) condition;

                switch (binaryOperation.getOperation().getOpType()) {
                    case LTH, GTE-> {
                        operation = (binaryOperation.getOperation().getOpType() == OperationType.GTE) ? "if_icmpge" : "if_icmplt";

                        Element left = binaryOperation.getLeftOperand();
                        Element right = binaryOperation.getRightOperand();
                        int value = 0;
                        Element other = null;

                        if(left instanceof LiteralElement literalElement) {
                            value = Integer.parseInt(literalElement.getLiteral());
                            other = right;

                            operation = (binaryOperation.getOperation().getOpType() == OperationType.GTE) ? "ifle" : "ifgt";
                        } else if(right instanceof LiteralElement literalElement) {
                            value = Integer.parseInt(literalElement.getLiteral());
                            other = left;

                            operation = (binaryOperation.getOperation().getOpType() == OperationType.GTE) ? "ifge" : "iflt";
                        }

                        if(value == 0 && other != null) {
                            stringBuilder.append(this.addToStack(other, varTable));
                        } else {
                            assert left != null;
                            assert right != null;
                            stringBuilder.append(this.addToStack(left, varTable))
                                    .append(this.addToStack(right, varTable));
                            operation = (binaryOperation.getOperation().getOpType() == OperationType.GTE) ? "if_icmpge" : "if_icmplt";
                        }
                        if(operation.equals("if_icmpge") || operation.equals("if_icmplt"))
                            this.updateStackLimits(-1);
                    }
                    case ANDB -> {
                        operation = "ifeq";
                        stringBuilder.append(this.getInstruction(condition, varTable));
                    }
                    case NEQ -> {
                        operation = "ifne";
                        stringBuilder.append(this.addToStack(binaryOperation.getLeftOperand(), varTable))
                                .append(this.addToStack(binaryOperation.getRightOperand(), varTable));
                    }
                    default -> {
                        System.out.println("Binary operation not implemented yet: " + binaryOperation.getOperation().getOpType());
                        stringBuilder.append(this.getInstruction(condition, varTable));
                        operation = "ifne";
                        ; // still need to check the other cases
                    }
                }
            }
            case UNARYOPER -> {
                assert condition instanceof UnaryOpInstruction;
                UnaryOpInstruction unaryOperation = (UnaryOpInstruction) condition;

                // This should be the only operation type that we need to check
                if(unaryOperation.getOperation().getOpType().equals(OperationType.NOTB)) {
                    stringBuilder.append(this.addToStack(unaryOperation.getOperand(), varTable));
                    operation = "ifeq";
                }
            }
            default -> {
                // still need to check the other cases, but this will suffice for now
                stringBuilder.append(this.getInstruction(condition, varTable));
                operation = "ifne";
            }
        }

        this.updateStackLimits(-1);

        stringBuilder.append(operation).append(" ").append(instruction.getLabel()).append("\n");
        return stringBuilder.toString();
    }

    /**
     * Generates the Return instruction.
     * First verifies if the instruction has a return type.
     */
    private String getReturnInstruction(ReturnInstruction instruction, HashMap<String, Descriptor> varTable) {
        StringBuilder stringBuilder = new StringBuilder();

        if(instruction.hasReturnValue()) {
            stringBuilder.append(this.addToStack(instruction.getOperand(), varTable));

            ElementType elementType = instruction.getOperand().getType().getTypeOfElement();

            if(elementType.equals(ElementType.INT32) || elementType.equals(ElementType.BOOLEAN))
                stringBuilder.append("ireturn\n");
            else if(elementType.equals(ElementType.VOID))
                stringBuilder.append("return\n");
            else
                stringBuilder.append("areturn\n");
        } else {
            stringBuilder.append("return\n");
        }

        return stringBuilder.toString();
    }

    /**
     * Generates the PutField instruction.
     * Pops the 2 values from the stack.
     */
    private String getPutFieldInstruction(PutFieldInstruction instruction, HashMap<String, Descriptor> varTable) {
        String ret =  addToStack(instruction.getFirstOperand(), varTable) +
                addToStack(instruction.getThirdOperand(), varTable) + "putfield " +
                getClassName(((Operand) instruction.getFirstOperand()).getName(), this.ollirClass) +
                "/" + ((Operand)instruction.getSecondOperand()).getName() + " " +
                getTypeDescriptor(instruction.getSecondOperand().getType()) + "\n";

        this.updateStackLimits(-2);

        return ret;
    }

    /**
     * Generates the GetField instruction.
     * Pushes the value to the stack.
     */
    private String getGetFieldInstruction(GetFieldInstruction instruction, HashMap<String, Descriptor> varTable) {
        // Does it need to update the stack?
        return addToStack(instruction.getFirstOperand(), varTable) + "getfield " +
                getClassName(((Operand) instruction.getFirstOperand()).getName(), this.ollirClass) +
                "/" + ((Operand)instruction.getSecondOperand()).getName() + " " +
                getTypeDescriptor(instruction.getSecondOperand().getType()) + "\n";
    }

    private String getUnaryOperationInstruction(UnaryOpInstruction instruction, HashMap<String, Descriptor> varTable) {
        StringBuilder stringBuilder = new StringBuilder();

        Operation operation = instruction.getOperation();

        stringBuilder.append(this.addToStack(instruction.getOperand(), varTable))
                .append(getOperation(operation));

        System.out.println("UNARY: " + operation.getOpType());

        if(operation.getOpType() == OperationType.NOTB)
            stringBuilder.append(this.getBooleanOperation());

        stringBuilder.append("\n");

        return stringBuilder.toString();
    }

    private String getBinaryOperationInstruction(BinaryOpInstruction instruction, HashMap<String, Descriptor> varTable) {
        StringBuilder stringBuilder = new StringBuilder();

        Element leftElement = instruction.getLeftOperand();
        Operation operation = instruction.getOperation();
        Element rightElement = instruction.getRightOperand();

        stringBuilder.append(this.addToStack(leftElement, varTable))
                .append(this.addToStack(rightElement, varTable))
                .append(getOperation(operation));

        System.out.println("Binary: " + operation.getOpType());

        if(isBooleanOperation(operation.getOpType()))
            stringBuilder.append(this.getBooleanOperation());

        stringBuilder.append("\n");

        this.updateStackLimits(-1);

        return stringBuilder.toString();
    }

    private String addToStack(Element element, HashMap<String, Descriptor> varTable) {
        StringBuilder stringBuilder = new StringBuilder();
        ElementType elementType = element.getType().getTypeOfElement();
        this.updateStackLimits(1);
        if(element instanceof LiteralElement literalElement) {
            if(elementType == ElementType.INT32 || elementType == ElementType.BOOLEAN) {
                int value = Integer.parseInt(literalElement.getLiteral());

                if(value >= -1 && value <= 5)
                    stringBuilder.append("iconst_");
                else if(isByte(value))
                    stringBuilder.append("bipush ");
                else if(isShort(value))
                    stringBuilder.append("sipush ");
                else
                    stringBuilder.append("ldc ");

                if (value == -1)
                    stringBuilder.append("m1");
                else
                    stringBuilder.append(value);
            } else { // This should suffice for Strings and Classes
                stringBuilder.append("ldc ").append(literalElement.getLiteral()); // TODO: it seems we can get this: ldc "val = " ; on this: "t2.String :=.String ldc("val = ").String;" ; we need to change this
            }
        } else if (element instanceof ArrayOperand arrayOperand) {
            stringBuilder.append("aload").append(getVariableIndex(arrayOperand.getName(), varTable)).append("\n");
            stringBuilder.append(addToStack(arrayOperand.getIndexOperands().get(0), varTable));
            stringBuilder.append("iaload");

            this.updateStackLimits(-1);
        } else if(element instanceof Operand operand) {
            String index = getVariableIndex(operand.getName(), varTable);

            switch (operand.getType().getTypeOfElement()) {
                case INT32, BOOLEAN -> stringBuilder.append("iload").append(index);
                case STRING, ARRAYREF, OBJECTREF, THIS, CLASS -> stringBuilder.append("aload").append(index);
            }
        }

        stringBuilder.append("\n");

        return stringBuilder.toString();
    }

    /**
     * Generates the boolean operation for the given operation type.
     */
    private String getBooleanOperation() {
        String trueLabel = "TRUE" + this.conditionNum;
        String nextLabel = "NEXT" + this.conditionNum;

        return " " + trueLabel + this.conditionNum + "\n"
                + "iconst_0\n"
                + "goto " + nextLabel + this.conditionNum + " ; its boolean op\n"
                + trueLabel + this.conditionNum + ":\n"
                + "iconst_1\n"
                + nextLabel + this.conditionNum++ + ":";
    }

    public String getStore(Operand destination, HashMap<String, Descriptor> varTable) {
        StringBuilder stringBuilder = new StringBuilder();

        this.updateStackLimits(-1);

        switch (destination.getType().getTypeOfElement()) {
            case INT32, BOOLEAN -> {
                if(varTable.get(destination.getName()).getVarType().getTypeOfElement() == ElementType.ARRAYREF) {
                    stringBuilder.append("iastore\n");
                }
                else {
                    stringBuilder.append("istore").append(getVariableIndex(destination.getName(), varTable)).append("\n");
                }
            }
            case STRING, ARRAYREF, THIS, OBJECTREF, CLASS -> { // TODO check if class is correct here
                stringBuilder.append("astore").append(getVariableIndex(destination.getName(), varTable)).append("\n");
            }
        }

        return stringBuilder.toString();
    }

    private void updateStackLimits(int stackLimit) {
        this.currentStack += stackLimit;

        if(this.currentStack > this.limitStack)
            this.limitStack = this.currentStack;
        else if (this.currentStack < 0)
            this.currentStack = 0;
    }

}
