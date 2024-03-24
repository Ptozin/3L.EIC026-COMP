package pt.up.fe.comp2023;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import pt.up.fe.comp.TestUtils;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.jasmin.JasminResult;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.parser.JmmParserResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp2023.ast.ASymbolTable;
import pt.up.fe.comp2023.ast.SymbolTableBuilder;
import pt.up.fe.comp2023.jasmin.Backend;
import pt.up.fe.specs.util.SpecsIo;
import pt.up.fe.specs.util.SpecsLogs;
import pt.up.fe.specs.util.SpecsSystem;

public class Launcher {

    public static void main(String[] args) {
        // Setups console logging and other things
        SpecsSystem.programStandardInit();

        // Parse arguments as a map with predefined options
        var config = parseArgs(args);

        // Get input file
        File inputFile = new File(config.get("inputFile"));

        // Check if file exists
        if (!inputFile.isFile()) {
            throw new RuntimeException("Expected a path to an existing input file, got '" + inputFile + "'.");
        }

        // Read contents of input file
        String code = SpecsIo.read(inputFile);

        System.out.println("\nStarted parsing stage...");
        AParser parser = new AParser();
        JmmParserResult parserResult = parser.parse(code, config);

        // Prints the reports
        for(Report report : parserResult.getReports())
            System.out.println(report);

        // Check if there are parsing errors
        long errors = TestUtils.getNumErrors(parserResult.getReports());
        if(errors > 0){
            System.out.println("Aborting...");
            return;
        }

        // Print AST
        System.out.println("Printing AST...\n");
        System.out.println(parserResult.getRootNode().toTree());
        System.out.println("Parsing done.\n");

        System.out.println("Started Analysis stage...");
        ASymbolTable symbolTable = new ASymbolTable();
        SymbolTableBuilder buildVisitor = new SymbolTableBuilder();
        buildVisitor.visit(parserResult.getRootNode(), symbolTable);
        System.out.println("Printing Symbol Table...\n");
        System.out.println(symbolTable.print());
        System.out.println("Symbol Table done.");

        Analysis analysisStage = new Analysis();
        JmmSemanticsResult semanticsResult = analysisStage.semanticAnalysis(parserResult);

        // Prints the reports
        for(Report report : semanticsResult.getReports())
            System.out.println(report);

        // Check if there are parsing errors
        errors = TestUtils.getNumErrors(semanticsResult.getReports());
        if(errors > 0){
            System.out.println("Aborting...");
            return;
        }

        Optimization ollir = new Optimization();
        if (config.get("optimize") != null && config.get("optimize").equals("true")) {
            System.out.println("Starting AST Optimizations");
            semanticsResult = ollir.optimize(semanticsResult);

            // Print AST
            System.out.println("Printing revised AST...\n");
            System.out.println(parserResult.getRootNode().toTree());
            System.out.println("Parsing done.\n");
        }

        System.out.println("Starting Ollir...\n");
        OllirResult test = ollir.toOllir(semanticsResult);

        System.out.println("Ollir done.\n");

        if (!config.get("registerAllocation").equals("-1")) {
            System.out.println("Starting Register Optimizations\n");
            test = ollir.optimize(test);

            // Prints the reports
            for(Report report : test.getReports())
                System.out.println(report);

            // Check if there are parsing errors
            errors = TestUtils.getNumErrors(test.getReports());
            if(errors > 0){
                System.out.println("Aborting...");
                return;
            }
        }
        System.out.println("Starting with Jasmin.\n");

        JasminResult jasminResult = new Backend().toJasmin(test);

        System.out.println("Jasmin done.\n");

        if(jasminResult.getReports().size() > 0) {
            System.out.println("Jasmin reports:");

            for (Report report : jasminResult.getReports()) {
                System.out.println(report);
            }
        }


        System.out.println("Running Jasmin Code...\n");
        jasminResult.compile(new File("out"));
        jasminResult.run();

    }

    private static Map<String, String> parseArgs(String[] args) {
        SpecsLogs.info("Executing with args: " + Arrays.toString(args));

        // Check if there is at least one argument
        if (args.length < 1) {
            throw new RuntimeException("Expected at least a single argument, a path to an existing input file.");
        }

        // Create config
        Map<String, String> config = new HashMap<>();
        config.put("inputFile", args[0]);
        if (Arrays.stream(args).anyMatch("-o"::equals))
            config.put("optimize", "true");
        else
            config.put("optimize", "false");

        // Update registerAllocation field based on user input
        String regNum = "-1"; // Default value
        for (String arg : args)
            if (arg.startsWith("-r=")) {
                String value = arg.substring(3);
                Integer.parseInt(value);
                regNum = value;
            }
        config.put("registerAllocation", regNum);

        config.put("debug", "false");

        return config;
    }

}
