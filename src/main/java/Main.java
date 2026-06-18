import java.util.Scanner;
import java.util.List;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner in = new Scanner(System.in);
        String currentDirectory = System.getProperty("user.dir");
        while (true) {
            System.out.print("$ ");
            String input = in.nextLine();
            if (input.equals("exit")) break;

            List<String> rawTokens = tokenize(input);
            if (rawTokens.isEmpty()) continue;

            String outputFile = null;
            String errorFile = null;
            List<String> tokens = new ArrayList<>();
            for (int idx = 0; idx < rawTokens.size(); idx++) {
                String t = rawTokens.get(idx);
                if ((t.equals(">") || t.equals("1>")) && idx + 1 < rawTokens.size()) {
                    outputFile = rawTokens.get(idx + 1);
                    idx++;
                } else if (t.equals("2>") && idx + 1 < rawTokens.size()) {
                    errorFile = rawTokens.get(idx + 1);
                    idx++;
                } else {
                    tokens.add(t);
                }
            }

            if (tokens.isEmpty()) continue;
            String command = tokens.get(0);

            if (command.equals("exit")) {
                break;
            } else if (command.equals("type")) {
                String rem = tokens.size() > 1 ? tokens.get(1) : "";
                String result;
                if (rem.equals("echo") || rem.equals("type") || rem.equals("exit") || rem.equals("pwd") || rem.equals("cd")) {
                    result = rem + " is a shell builtin";
                } else {
                    java.io.File exeFile = findExecutable(rem);
                    if (exeFile != null) {
                        result = rem + " is " + exeFile.getAbsolutePath();
                    } else {
                        result = rem + ": not found";
                    }
                }
                writeOutput(result, outputFile);
            } else if (command.equals("pwd")) {
                writeOutput(currentDirectory, outputFile);
            } else if (command.equals("cd")) {
                String targetPath = tokens.size() > 1 ? tokens.get(1) : "";

                if (targetPath.equals("~")) {
                    targetPath = System.getenv("HOME");
                } else if (targetPath.startsWith("~/")) {
                    targetPath = System.getenv("HOME") + targetPath.substring(1);
                }

                java.io.File targetDir;
                if (targetPath.startsWith("/")) {
                    targetDir = new java.io.File(targetPath);
                } else {
                    targetDir = new java.io.File(currentDirectory, targetPath);
                }

                try {
                    targetDir = targetDir.getCanonicalFile();
                } catch (Exception e) {
                }

                if (targetDir.exists() && targetDir.isDirectory()) {
                    currentDirectory = targetDir.getAbsolutePath();
                    System.setProperty("user.dir", currentDirectory);
                } else {
                    writeOutput("cd: " + targetPath + ": No such file or directory", errorFile);
                }
            } else if (command.equals("echo")) {
                String result = String.join(" ", tokens.subList(1, tokens.size()));
                writeOutput(result, outputFile);
            } else {
                java.io.File exeFile = findExecutable(command);

                if (exeFile != null) {
                    runExternalProgram(tokens.toArray(new String[0]), outputFile, errorFile);
                } else {
                    System.out.println(input + ": command not found");
                }
            }
        }
    }

    private static void writeOutput(String text, String targetFile) {
        if (targetFile == null) {
            System.out.println(text);
        } else {
            try {
                java.io.File file = new java.io.File(targetFile);
                java.io.File parent = file.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                try (java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.FileWriter(file))) {
                    writer.println(text);
                }
            } catch (Exception e) {
            }
        }
    }

    private static List<String> tokenize(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;
        boolean tokenStarted = false;
        int i = 0;
        int len = input.length();
        while (i < len) {
            char c = input.charAt(i);
            if (inSingleQuotes) {
                if (c == '\'') {
                    inSingleQuotes = false;
                } else {
                    current.append(c);
                }
                i++;
            } else if (inDoubleQuotes) {
                if (c == '"') {
                    inDoubleQuotes = false;
                    i++;
                } else if (c == '\\' && i + 1 < len &&
                        (input.charAt(i + 1) == '"' || input.charAt(i + 1) == '\\' ||
                         input.charAt(i + 1) == '$' || input.charAt(i + 1) == '`' ||
                         input.charAt(i + 1) == '\n')) {
                    current.append(input.charAt(i + 1));
                    i += 2;
                } else {
                    current.append(c);
                    i++;
                }
            } else {
                if (c == '\\') {
                    if (i + 1 < len) {
                        current.append(input.charAt(i + 1));
                        tokenStarted = true;
                        i += 2;
                    } else {
                        i++;
                    }
                } else if (c == '\'') {
                    inSingleQuotes = true;
                    tokenStarted = true;
                    i++;
                } else if (c == '"') {
                    inDoubleQuotes = true;
                    tokenStarted = true;
                    i++;
                } else if (Character.isWhitespace(c)) {
                    if (tokenStarted) {
                        tokens.add(current.toString());
                        current.setLength(0);
                        tokenStarted = false;
                    }
                    i++;
                } else {
                    current.append(c);
                    tokenStarted = true;
                    i++;
                }
            }
        }
        if (tokenStarted) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    private static java.io.File findExecutable(String command) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) return null;

        String[] dirs = pathEnv.split(java.io.File.pathSeparator);

        for (String dir : dirs) {
            java.io.File file = new java.io.File(dir, command);
            if (file.exists() && file.canExecute()) {
                return file;
            }
        }
        return null;
    }

    private static void runExternalProgram(String[] tokens, String outputFile, String errorFile) {
        try {
            ProcessBuilder pb = new ProcessBuilder(tokens);

            if (outputFile != null) {
                java.io.File file = new java.io.File(outputFile);
                java.io.File parent = file.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                pb.redirectOutput(file);
            } else {
                pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            }

            if (errorFile != null) {
                java.io.File file = new java.io.File(errorFile);
                java.io.File parent = file.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                pb.redirectError(file);
            } else {
                pb.redirectError(ProcessBuilder.Redirect.INHERIT);
            }

            pb.redirectInput(ProcessBuilder.Redirect.INHERIT);

            Process process = pb.start();
            process.waitFor();
        } catch (Exception e) {
            System.out.println(tokens[0] + ": error executing program");
        }
    }
}