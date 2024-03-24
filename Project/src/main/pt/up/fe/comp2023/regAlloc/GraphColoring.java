package pt.up.fe.comp2023.regAlloc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GraphColoring {
    private Map<String, Integer> registers;
    private List<String> stack;
    private Map<String, List<String>> conflicts;
    private final int regNum;
    private boolean stackWorking;
    private boolean registersWorking;

    public Map<String, Integer> getRegisters() {
        return registers;
    }
    public boolean isStackWorking() {return stackWorking;}
    public boolean isRegisterWorking() {return registersWorking;}

    public GraphColoring(int regNum, Map<String, List<String>> conflicts) {

        this.conflicts = new HashMap<>(conflicts);
        this.regNum = regNum;
        this.registers = new HashMap<>();

        this.stackWorking = applyChaitin();
        if (stackWorking)
            this.registersWorking = assignRegisters();
    }

    private boolean applyChaitin() {
        this.stack = new ArrayList<>();
        Map<String, List<String>> interferenceGraph = new HashMap<>(conflicts);
        while (interferenceGraph.size() != 0) {
            boolean updated = false;
            // Find node with less than regNum interferences
            for (String var : interferenceGraph.keySet()) {
                if (interferenceGraph.get(var).size() < regNum) {
                    updated = true;
                    stack.add(var);
                    interferenceGraph.remove(var);
                    for (String varUpdate : interferenceGraph.keySet())
                        if (interferenceGraph.get(varUpdate).contains(var)) {
                            List<String> updatedConflicts = interferenceGraph.get(varUpdate);
                            updatedConflicts.remove(var);
                            interferenceGraph.put(varUpdate, updatedConflicts);
                        }
                    break;
                }
            }
            if (!updated)
                return false;
        }
        return true;
    }


    private boolean assignRegisters() {
        for (String var : stack)
            registers.put(var, -1);
        for (int i = stack.size() - 1; i >= 0; i--) {
            String var = stack.get(i);
            //stack.remove(var);
            if (!addRegister(var))
                return false;
        }
        return true;
    }

    private boolean addRegister(String var) {
        for (int i = 0; i < regNum; i++) {
            boolean skip = false;
            for (String conflictVar : conflicts.get(var)) {
                if (registers.get(conflictVar) == i) {
                    skip = true;
                    break;
                }
            }
            if (!skip) {
                registers.put(var, i);
                return true;
            }
        }
        return false;
    }
}
