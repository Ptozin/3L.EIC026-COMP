package pt.up.fe.comp2023.regAlloc;

import org.specs.comp.ollir.*;

import java.util.*;

public class LVAnalysis {
    List<Integer> visitedNodes;
    private Method method;
    private List<String> varList;
    private Map<Integer, Set<String>> out;
    private Map<Integer, Set<String>> gen;
    private Map<Integer, String> kill;
    private boolean updated;
    private Map<String, List<String>> conflicts;
    public LVAnalysis(Method method){

        this.method = method;
        this.method.buildCFG();

        varList = new ArrayList<>();
        for (String var : method.getVarTable().keySet())
            varList.add(var);
        List<String> paramCheck = new ArrayList<>();
        for (Element elem : method.getParams()) {
            Operand c = (Operand) elem;
            if (varList.contains(c.getName()))
                varList.remove(c.getName());
        }

        //Setup sets
        out = new HashMap<>();
        gen = new HashMap<>();
        kill = new HashMap<>();
        visitedNodes = new ArrayList<>();
        setup(method.getBeginNode().getSucc1()); // node 0 is not instruction

        //Run LVA
        updated = true;
        while (updated) {
            updated = false;
            visitedNodes = new ArrayList<>();
            run(method.getEndNode().getPredecessors().get(0));
        }

        conflicts = new HashMap<>();
        visitedNodes = new ArrayList<>();
        for (String var : varList)
            conflicts.put(var, new ArrayList<>());
        checkConflicts(method.getBeginNode().getSucc1());
    }

    private void setup(Node node) {
        if (node.getId() == 0 || visitedNodes.contains(node.getId()))
            return; // Visited status necessary to avoid getting stuck in loops
        visitedNodes.add(node.getId());

        // Default values
        out.put(node.getId(), new TreeSet<>());
        kill.put(node.getId(), null);
        gen.put(node.getId(), new TreeSet<>());

        if (method.getInstr(node.getId()-1).getInstType() == InstructionType.ASSIGN) {
            AssignInstruction inst = (AssignInstruction) method.getInstr(node.getId()-1);
            Operand var = (Operand) inst.getDest(); // If var is being assigned define KILL[n]
            kill.put(node.getId(), var.getName());
        }
        for (String var : varList) { // Check if var is being used
            if (method.getInstr(node.getId()-1).getInstType() == InstructionType.ASSIGN) {
                AssignInstruction inst = (AssignInstruction) method.getInstr(node.getId()-1);
                if (inst.getRhs().toString().contains(var + ".")) {
                    Set<String> varSet = gen.get(node.getId());
                    varSet.add(var); // If in assignment GEN[n] must be limited to right hand side
                    gen.put(node.getId(),varSet);
                }
            }
            else if(method.getInstr(node.getId()-1).toString().contains(var + ".")) {
                Set<String> varSet = gen.get(node.getId());
                varSet.add(var);
                gen.put(node.getId(),varSet);
            }
        }
        if (node.getSuccessors() != null)
            for (Node succ : node.getSuccessors())
                setup(succ);
    }

    private void run(Node node) {
        if (node.getId() == 0 || visitedNodes.contains(node.getId()))
            return; // Visited status necessary to avoid getting stuck in loops
        visitedNodes.add(node.getId());

        // IN[node]
        Set<String> varsActive = new TreeSet<>();
        if (!(node.getSuccessors() == null))
            for (Node succ : node.getSuccessors()) {
                if (succ.getId() == 0)  // First we get all variables that have yet to be assigned
                    continue;   // at this point in the flow
                for (String var : out.get(succ.getId()))
                    if (!varsActive.contains(var))
                        varsActive.add(var);
            }
        // KILL[node]
        String assign = kill.get(node.getId()); // KILL is done before GEN: in "a = a" a would still need to be assigned
        if (assign != null && varsActive.contains(assign))
            varsActive.remove(assign);
        // GEN[node]
        for (String var : gen.get(node.getId()))
            if (!varsActive.contains(var))
                varsActive.add(var);

        // Check for changes, tree sets are ordered by default
        if (updated) {}
        else if (!varsActive.equals(out.get(node.getId())))
            updated = true; // Code execution will stop when no OUT[n] is changed
        out.put(node.getId(), varsActive);

        // Continue execution
        if (node.getPredecessors() != null)
            for (Node pred : node.getPredecessors())
                run(pred);
    }

    private void checkConflicts(Node node) {
        if (node.getId() == 0 || visitedNodes.contains(node.getId()))
            return; // Visited status necessary to avoid getting stuck in loops
        visitedNodes.add(node.getId());

        List<String> varsActive = new ArrayList<>(); // I would turn this into a set, but inputs are already being checked
        if (node.getSuccessors() != null)   // and the list is exported to other classes
            for (Node succ : node.getSuccessors()) {
                if (succ.getId() == 0)
                    continue;
                for (String var : out.get(succ.getId()))
                    if (!varsActive.contains(var))
                        varsActive.add(var); // Getting OUTs from successors
            }
/*
        for (String var : gen.get(node.getId()))
            if (!varsActive.contains(var))
                varsActive.add(var); // Getting GEN from itself

 */

        if (kill.get(node.getId()) != null && !varsActive.contains(kill.get(node.getId())))
            varsActive.add(kill.get(node.getId())); // KILL must be added as well because of dead assignments

        for (String var : varsActive){
            List<String> varConflict = conflicts.get(var);
            for (String var2 : varsActive)
                if (!var.equals(var2) && !varConflict.contains(var2))
                    varConflict.add(var2);
            conflicts.put(var, varConflict);
        }

        if (node.getSuccessors() != null)
            for (Node succ : node.getSuccessors())
                checkConflicts(succ);
    }

    public Map<String, List<String>> getConflictInfo() {
        return conflicts;
    }
}
