package com.lauriewired.ipax.files;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

public class Macho {
    // Mach-O Magic Numbers
    private static final int FAT_MAGIC = 0xcafebabe;
    private static final int FAT_CIGAM = 0xbebafeca;

    private List<Integer> cpuTypes;
    private List<Integer> cpuSubTypes;
    private List<Long> offsets;
    private List<Long> sizes;
    private boolean isFat;
    private String machoExecutablePath;
    private String outputDirectoryPath;
    private String machoExecutableName;

    public Macho(String machoExecutablePath, String outputDirectoryPath, String machoExecutableName) {
        this.isFat = false;
        this.cpuTypes = new ArrayList<>();
        this.cpuSubTypes = new ArrayList<>();
        this.offsets = new ArrayList<>();
        this.sizes = new ArrayList<>();
        this.machoExecutablePath = machoExecutablePath;
        this.outputDirectoryPath = outputDirectoryPath;
        this.machoExecutableName = machoExecutableName;
        processMacho();
    }

    public void processFatMacho(String selectedArchitecture) {
        extractMachoArchitecture(selectedArchitecture);

        // We do not care about the original macho anymore
        // This will effectively reset the instance variables for the extracted macho
        processMacho();
    }


    private void extractMachoArchitecture(String selectedArchitecture) {
        for (int i = 0; i < cpuTypes.size(); i++) {
            String arch = getArchitectureName(cpuTypes.get(i));
            String fullArchitecture = generateFullArchitectureString(arch, cpuTypes.get(i), cpuSubTypes.get(i));

            if (fullArchitecture.equals(selectedArchitecture)) {
                String tempFileName = machoExecutableName + "_extracted.macho";
                try {
                    extractSlice(machoExecutablePath, tempFileName, offsets.get(i), sizes.get(i));
                    System.out.println("Extracted " + arch + " slice to " + tempFileName);

                    replaceOldMachoWithNew(tempFileName);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            }
        }
    }

    private void extractSlice(String inputFilePath, String outputFileName, long offset, long size) throws IOException {
        // Construct the full path for the output file
        String outputPath = outputDirectoryPath + File.separator + outputFileName;

        try (RandomAccessFile inputFile = new RandomAccessFile(inputFilePath, "r");
             FileOutputStream outputFile = new FileOutputStream(outputPath)) {

            inputFile.seek(offset);
            byte[] buffer = new byte[8192];
            long remaining = size;

            while (remaining > 0) {
                int bytesRead = inputFile.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (bytesRead == -1) break;

                outputFile.write(buffer, 0, bytesRead);
                remaining -= bytesRead;
            }
        }
    }

    private void replaceOldMachoWithNew(String tempFileName) throws IOException {
        File oldMacho = new File(machoExecutablePath);
        File extractedMacho = new File(outputDirectoryPath + File.separator + tempFileName);
        File newMacho = new File(machoExecutablePath);

        if (oldMacho.delete()) {
            if (!extractedMacho.renameTo(newMacho)) {
                throw new IOException("Failed to rename extracted Mach-O file.");
            }
            System.out.println("Replaced old Mach-O file with the extracted one.");
        } else {
            throw new IOException("Failed to delete old Mach-O file.");
        }
    }

    /*
     * Reads in a Mach-O file and sets instance variables based on type and architecture
     */
    private void processMacho() {
        File file = new File(this.machoExecutablePath);

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            int magic = raf.readInt();
            if (magic == FAT_MAGIC || magic == FAT_CIGAM) {
                this.isFat = true;
                System.out.println("Detected FAT binary with architectures:");

                boolean reverseByteOrder = (magic == FAT_CIGAM);
                int archCount = reverseByteOrder ? Integer.reverseBytes(raf.readInt()) : raf.readInt();
                for (int i = 0; i < archCount; i++) {
                    raf.seek(8L + i * 20L);
                    int cpuType = reverseByteOrder ? Integer.reverseBytes(raf.readInt()) : raf.readInt();
                    int cpuSubType = reverseByteOrder ? Integer.reverseBytes(raf.readInt()) : raf.readInt();
                    long offset = reverseByteOrder ? Integer.reverseBytes(raf.readInt()) : raf.readInt();
                    long size = reverseByteOrder ? Integer.reverseBytes(raf.readInt()) : raf.readInt();

                    cpuTypes.add(cpuType);
                    cpuSubTypes.add(cpuSubType);
                    offsets.add(offset);
                    sizes.add(size);
                }
            } else {
                this.isFat = false;
                System.out.println("This is not a FAT binary.");
            }
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
        }
    }

    public List<String> getArchitectureStrings() {
        List<String> architectureStrings = new ArrayList<>();

        for (int i = 0; i < cpuTypes.size(); i++) {
            int cpuType = cpuTypes.get(i);
            int cpuSubType = cpuSubTypes.get(i);
            String arch = getArchitectureName(cpuType);

            String fullArchitecture = generateFullArchitectureString(arch, cpuType, cpuSubType);
            architectureStrings.add(fullArchitecture);
        }

        return architectureStrings;
    } 

    public void printArchitectures() {
        String arch = "";
        String fullArchitecture = "";

        for (int i = 0; i < cpuTypes.size(); i++) {
            int cpuType = cpuTypes.get(i);
            int cpuSubType = cpuSubTypes.get(i);
            arch = getArchitectureName(cpuType);

            fullArchitecture = generateFullArchitectureString(arch, cpuType, cpuSubType);
            System.out.println(fullArchitecture);
        }
    }

    private String generateFullArchitectureString(String arch, int cpuType, int cpuSubType) {
        return arch + " (CPU Type: " + cpuType + ", SubType: " + cpuSubType + ")";
    }

    private String getArchitectureName(int cpuType) {
        switch (cpuType) {
            case 0x00000007:
                return "Intel x86";
            case 0x01000007:
                return "Intel x86_64";
            case 0x0000000C:
                return "ARM";
            case 0x0100000C:
                return "ARM64";
            default:
                return "Unknown";
        }
    }

    public List<Integer> getCpuTypes() {
        return cpuTypes;
    }

    public List<Integer> getCpuSubTypes() {
        return cpuSubTypes;
    }

    public boolean isFatBinary() {
        return this.isFat;
    }

    public String getMachoExecutableName() {
        return this.machoExecutableName;
    }
}
