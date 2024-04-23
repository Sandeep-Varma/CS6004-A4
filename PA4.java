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
            "-main-class", "Test1o",	// specify the main class
            "-process-dir", dir,      // directory of classes to analyze
        };

        // Create transformer for analysis
        AnalysisTransformer analysisTransformer = new AnalysisTransformer();

        // Add transformer to appropriate pack in PackManager; PackManager will run all packs when soot.Main.main is called
        PackManager.v().getPack("jtp").add(new Transform("jtp.MyOptimization", analysisTransformer));
        
        // Call Soot's main method with arguments
        soot.Main.main(sootArgs);
    }
}