import ghidra.app.decompiler.DecompInterface;
import ghidra.app.script.GhidraScript;
import ghidra.program.model.symbol.Namespace;
import ghidra.program.model.mem.Memory;
import ghidra.program.model.mem.MemoryBlock;
import ghidra.program.model.address.Address;
import ghidra.util.task.ConsoleTaskMonitor;
import ghidra.program.model.listing.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.*;
import org.json.JSONObject;
import org.json.JSONArray;

public class DumpClassData extends GhidraScript {

    private int getPort() {
        String[] args = getScriptArgs();
        if (args.length > 0) {
            return Integer.parseInt(args[0]);
        }
        println("No port provided. Exiting script.");
        return -1;
    }

    private String formatNamespaceName(String namespaceName) {
        if ("<global>".equals(namespaceName)) {
            return "Global";
        } else if ("<EXTERNAL>".equals(namespaceName)) {
            return "External";
        }
        return namespaceName;
    }

    private JSONArray extractClassFunctionData(Program program) {
        FunctionManager functionManager = program.getFunctionManager();
        List<JSONObject> classFunctionData = new ArrayList<>();

        Map<String, List<String>> namespaceFunctionData = new HashMap<>();

        for (Function function : functionManager.getFunctions(true)) {
            Namespace namespace = function.getParentNamespace();
            String namespaceName = formatNamespaceName(namespace != null ? namespace.getName() : "<global>");

            namespaceFunctionData.computeIfAbsent(namespaceName, k -> new ArrayList<>()).add(function.getName());
        }

        for (Map.Entry<String, List<String>> entry : namespaceFunctionData.entrySet()) {
            JSONObject classObject = new JSONObject();
            classObject.put("ClassName", entry.getKey());
            classObject.put("Functions", new JSONArray(entry.getValue()));
            classFunctionData.add(classObject);
        }

        return new JSONArray(classFunctionData);
    }

    private JSONObject listDefinedDataInAllSegments(Program program) {
        Memory memory = program.getMemory();
        Listing listing = program.getListing();
        Map<String, JSONObject> dataStructure = new HashMap<>();

        for (MemoryBlock block : memory.getBlocks()) {
            Address start = block.getStart();
            Address end = block.getEnd();
            String name = block.getName();

            JSONObject segmentData = new JSONObject();
            segmentData.put("start", start.toString());
            segmentData.put("end", end.toString());
            JSONArray dataArray = new JSONArray();

            DataIterator dataIterator = listing.getDefinedData(start, true);
            while (dataIterator.hasNext()) {
                Data data = dataIterator.next();
                if (!block.contains(data.getAddress())) {
                    continue;
                }

                String label = data.getLabel();
                String value = data.getDefaultValueRepresentation();
                String address = data.getAddress().toString();

                JSONObject dataEntry = new JSONObject();
                dataEntry.put("label", label != null ? label : "Unnamed");
                dataEntry.put("value", value);
                dataEntry.put("address", address);
                dataArray.put(dataEntry);
            }

            segmentData.put("data", dataArray);
            dataStructure.put(name, segmentData);
        }

        return new JSONObject(dataStructure);
    }

    private JSONArray listFunctionsAndNamespaces(Program program) {
        DecompInterface decompInterface = new DecompInterface();
        FunctionManager functionManager = program.getFunctionManager();
        Map<String, List<Function>> namespaceFunctionsMap = new HashMap<>();
        JSONArray jsonOutput = new JSONArray();

        decompInterface.openProgram(program);

        // Collect functions for each namespace
        for (Function function : functionManager.getFunctions(true)) { // true for forward direction
            Namespace namespace = function.getParentNamespace();
            String namespaceName = formatNamespaceName(namespace != null ? namespace.getName() : "<global>");
            
            // Add function to namespace map
            namespaceFunctionsMap.computeIfAbsent(namespaceName, k -> new ArrayList<>()).add(function);
        }

        // Populate JSON data
        for (Map.Entry<String, List<Function>> entry : namespaceFunctionsMap.entrySet()) {
            String namespace = entry.getKey();
            List<Function> functions = entry.getValue();

            for (Function function : functions) {
                var decompiledFunction = decompInterface.decompileFunction(function, 0, new ConsoleTaskMonitor());
                if (decompiledFunction.decompileCompleted()) {
                    String decompiledCode = decompiledFunction.getDecompiledFunction().getC();

                    // Add JSON entry
                    JSONObject jsonEntry = new JSONObject();
                    jsonEntry.put("FunctionName", function.getName());
                    jsonEntry.put("ClassName", namespace);
                    jsonEntry.put("DecompiledCode", decompiledCode);
                    jsonOutput.put(jsonEntry);
                }
            }
        }

        decompInterface.dispose();
        return jsonOutput;
    }

    private void sendDataViaSocket(JSONArray classData, JSONObject machoData, JSONArray functionData) {
        int port = getPort();
        if (port == -1) return;

        try (Socket socket = new Socket("localhost", port);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            
            // Send class data
            out.println(classData.toString(4));
            out.println("END_CLASS_DATA");

            // Send Macho data
            out.println(machoData.toString(4));
            out.println("END_MACHO_DATA");

            // Send function decompilation data
            out.println(functionData.toString(4));
            out.println("END_DATA");

        } catch (IOException e) {
            printerr("Error sending data via socket: " + e.getMessage());
        }
    }

    @Override
    public void run() throws Exception {
        System.err.println("Running DumpCombinedData script");

        int port = getPort();
        if (port == -1) {
            return;
        }

        JSONArray classData = extractClassFunctionData(currentProgram);
        JSONObject machoData = listDefinedDataInAllSegments(currentProgram);
        JSONArray functionData = listFunctionsAndNamespaces(currentProgram);

        sendDataViaSocket(classData, machoData, functionData);
    }
}