import soot.*;

public class PA4 {
    public static void main(String[] args) {
        String classPath = "."; 	// change to appropriate path to the test class
        String dir = "./testcase";    // change to appropriate path to the test class

        //Set up arguments for Soot
        String[] sootArgs = {
            "-cp", classPath, "-pp", // sets the class path for Soot
            // "-f", "J", // produce output in jimple format
            // "-keep-line-number", // preserves line numbers in input Java files  
            "-main-class", "Test",	// specify the main class
            "-process-dir", dir,      // directory of classes to analyze
            "-d", "./sootOutput/" + getDirName(args[0],args[1]), // output directory
        };

        // Create transformer for analysis
        AnalysisTransformer analysisTransformer = new AnalysisTransformer();

        if (args[1].equals("no_op")){
            System.out.println("No optimization");
            analysisTransformer.optimize = false;
        }
        else if (args[1].equals("op")){
            System.out.println("Optimization");
            analysisTransformer.optimize = true;
        }
        else{
            System.out.println("Invalid optimization argument");
            return;
        }

        // Add transformer to appropriate pack in PackManager; PackManager will run all packs when soot.Main.main is called
        PackManager.v().getPack("jtp").add(new Transform("jtp.MyOptimization", analysisTransformer));
        
        // Call Soot's main method with arguments
        soot.Main.main(sootArgs);
    }

    private static String getDirName(String testcase, String opt){
        String[] parts = testcase.split("/");
        return parts[parts.length - 1].split(".java")[0] + "_" + opt;
    }
}