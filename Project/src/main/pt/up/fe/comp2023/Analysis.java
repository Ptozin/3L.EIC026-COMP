package pt.up.fe.comp2023;

import pt.up.fe.comp.jmm.analysis.JmmAnalysis;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.parser.JmmParserResult;
import pt.up.fe.comp2023.ast.ASymbolTable;
import pt.up.fe.comp2023.ast.SemanticVisitor;
import pt.up.fe.comp2023.ast.SymbolTableBuilder;

public class Analysis implements JmmAnalysis {
    @Override
    public JmmSemanticsResult semanticAnalysis(JmmParserResult jmmParserResult) {

        //Builds the symbol table to be edited in semantic analysis
        ASymbolTable symbolTable = new ASymbolTable();
        SymbolTableBuilder buildVisitor = new SymbolTableBuilder();
        buildVisitor.visit(jmmParserResult.getRootNode(), symbolTable);

        SemanticVisitor visitor = new SemanticVisitor();
        visitor.visit(jmmParserResult.getRootNode(), symbolTable);

        return new JmmSemanticsResult(jmmParserResult, symbolTable, visitor.getReports());
    }
}
