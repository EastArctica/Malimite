package com.lauriewired.ipax.decompile;

import com.lauriewired.ipax.utils.FileProcessing;
import com.lauriewired.ipax.files.Macho;

public class GhidraProject {
    private String ghidraProjectName;

    public GhidraProject(String infoPlistBundleExecutable, String executableFilePath) {
        this.ghidraProjectName = infoPlistBundleExecutable + "_ipax";
    }

    public void decompileMacho(String executableFilePath, String projectDirectoryPath, Macho targetMacho) {
        try {   
            //FIXME why is this not seeing my env vars
            //FIXME use "-scriptPath" for ghidra scripts location
            ProcessBuilder builder = new ProcessBuilder(
                "C:\\Users\\Laurie\\Documents\\GitClones\\ghidra_10.4_PUBLIC\\support\\analyzeHeadless.bat",
                projectDirectoryPath,
                this.ghidraProjectName,
                "-import",
                executableFilePath,
                "-postScript",
                "Ghidra-DumpClassData.py",
                projectDirectoryPath
            );

            System.out.println("Analyzing classes with Ghidra" + builder.command().toString());
            Process process = builder.start();

            // Read output and error streams
            FileProcessing.readStream(process.getInputStream());
            FileProcessing.readStream(process.getErrorStream());

            process.waitFor();
            System.out.println("Finished dumping class data");
            
            //FIXME move to another function and make this multithreaded and run in background
            builder = new ProcessBuilder(
                "C:\\Users\\Laurie\\Documents\\GitClones\\ghidra_10.4_PUBLIC\\support\\analyzeHeadless.bat",
                projectDirectoryPath,
                this.ghidraProjectName,
                "-postScript",
                "Ghidra-DumpMacho.py",
                projectDirectoryPath,
                "-process",
                "-noanalysis"
            );

            System.out.println("Running Ghidra decompliation" + builder.command().toString());
            process = builder.start();

            // Read output and error streams
            FileProcessing.readStream(process.getInputStream());
            FileProcessing.readStream(process.getErrorStream());

            process.waitFor();

            System.out.println("Done with Ghidra analysis");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void getPro
}