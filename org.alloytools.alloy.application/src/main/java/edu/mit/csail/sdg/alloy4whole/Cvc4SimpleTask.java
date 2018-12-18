package edu.mit.csail.sdg.alloy4whole;

import edu.mit.csail.sdg.alloy4.WorkerEngine;


import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class Cvc4SimpleTask implements WorkerEngine.WorkerTask
{
    public static final String OS                  = System.getProperty("os.name");
    public static final String SEP                 = File.separator;
    public static final String BIN_PATH = System.getProperty("user.dir")+SEP+"bin"+SEP;
    public static final int TIMEOUT = 300;

    private final Map<String, String> alloyFiles;

    Cvc4SimpleTask(Map<String, String> alloyFiles)
    {
        this.alloyFiles  = alloyFiles;
    }
    @Override
    public void run(WorkerEngine.WorkerCallback workerCallback) throws Exception
    {
        //ToDo: implement the case when there are multiple files
        Iterator<Map.Entry<String, String>> iterator = alloyFiles.entrySet().iterator();

        Map.Entry<String, String> entry = iterator.next();
        String fileName                 = entry.getKey();
        String smtFileName              = fileName + ".smt2";

        ProcessBuilder  processBuilder  = new ProcessBuilder();
        String [] command               = new String[]{
                "java",
                "-jar",
                "bin" + SEP + "alloy2smt.jar",
                "-o",
                smtFileName
        };

        processBuilder.command(command);

        workerCallback.callback("Executing command: " + String.join(" ", command));


        Process process           = processBuilder.start();

        OutputStream processInput = process.getOutputStream();

        processInput.write(entry.getValue().getBytes());
        processInput.close();

        if(process.waitFor((long) TIMEOUT, TimeUnit.SECONDS))
        {
            String error = getProcessOutput(process.getErrorStream());

            if(!error.isEmpty())
            {
                throw new Exception(error);
            }

            String output = getProcessOutput(process.getInputStream());

            workerCallback.callback("Translation output:" + output + "\n");

            executeCVC4(smtFileName, workerCallback);
        }
        else
        {
            workerCallback.callback("Timeout: Translation from ally to smt2 did not finish after " + TIMEOUT  + " seconds");
        }
    }

    private String getProcessOutput(InputStream inputStream)
    {
        StringBuilder stringBuilder = new StringBuilder();

        Scanner scanner = new Scanner(inputStream);

        while(scanner.hasNextLine())
        {
            stringBuilder.append(scanner.nextLine()).append("\n");
        }

        return stringBuilder.toString();
    }

    private static boolean isNumeric(String str)
    {
        return str.matches("\\d+(\\.\\d+)?");
    }
    private void executeCVC4(String fileName, WorkerEngine.WorkerCallback workerCallback) throws Exception
    {
        String cvc4;

        if(OS.startsWith("Windows"))
        {
            cvc4 = BIN_PATH + "cvc4_win64.exe";
        }
        else if(OS.startsWith("Linux"))
        {
            cvc4 = BIN_PATH + "cvc4_linux";
        }
        else
        {
            throw new Exception("No CVC4 binary availble for the OS: " + OS);
        }
        if(fileName == null)
        {
            throw new Exception("No input file for CVC4!");
        }

        File cvc4Binary = new File(cvc4);

        if(!cvc4Binary.exists())
        {
            throw new Exception("CVC4 binary does not exist at: " + cvc4);
        }
        if(!cvc4Binary.canExecute())
        {
            throw new Exception("CVC4 binary cannot be executed at: " + cvc4);
        }

        ProcessBuilder  processBuilder = new ProcessBuilder();
        List<String>    command       = new ArrayList<>();

        command.add(cvc4);
        command.add(fileName);

        processBuilder.command(command);

        workerCallback.callback("Executing command: " + String.join(" ", command));

        Process process = processBuilder.start();

        if(process.waitFor(TIMEOUT, TimeUnit.SECONDS))
        {
            String error = getProcessOutput(process.getErrorStream());

            if(!error.isEmpty())
            {
                throw new Exception(error);
            }

            String cvc4Output = getProcessOutput(process.getInputStream());
            workerCallback.callback("\nCVC4 Output:\n" + cvc4Output);
        }
        else
        {
            workerCallback.callback("CVC4 Timeout: " + TIMEOUT + " seconds");
        }
        process.destroy();
    }
}