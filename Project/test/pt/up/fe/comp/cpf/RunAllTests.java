package pt.up.fe.comp.cpf;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import pt.up.fe.comp.OllirTest;
import pt.up.fe.comp.cp2.AppTest;
import pt.up.fe.comp.cp2.JasminTest;
import pt.up.fe.comp.cp2.SemanticAnalysisTest;


@RunWith(Suite.class)
@Suite.SuiteClasses({
        Cpf1_ParserAndTree.class,
        Cpf2_SemanticAnalysis.class,
        Cpf3_Ollir.class,
        Cpf4_Jasmin.class,
        Cpf5_Optimizations.class,
        AppTest.class,
        JasminTest.class,
        OllirTest.class,
        SemanticAnalysisTest.class,
        pt.up.fe.comp.cp2eval.SemanticAnalysisTest.class

})
public class RunAllTests {

}