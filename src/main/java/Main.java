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
            else if (input.startsWith("type ")) {
                String rem = input.substring(5, input.length());
                if (rem.equals("echo") || rem.equals("type") || rem.equals("exit") || rem.equals("pwd") || rem.equals("cd")) {
                    System.out.println(rem + " is a shell builtin");
                } else {
                    java.io.File exeFile = findExecutable(rem);
                    if (exeFile != null) {
                        System.out.println(rem + " is " + exeFile.getAbsolutePath());
                    } else {
                        System.out.println(rem + ": not found");
                    }
                }
            } else if (input.equals("pwd")) {
                System.out.println(currentDirectory);
            } else if (input.startsWith("cd ")) {
                String targetPath = input.substring(3).trim();

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
                    System.out.println("cd: " + targetPath + ": No such file or directory");
                }
            } else if (input.startsWith("echo ")) {
                List<String> tokens = tokenize(input.substring(5));
                System.out.println(String.join(" ", tokens));
            } else {
                List<String> tokens = tokenize(input.trim());
                if (tokens.isEmpty()) continue;

                String command = tokens.get(0);
                java.io.File exeFile = findExecutable(command);

                if (exeFile != null) {
                    runExternalProgram(tokens.toArray(new String[0]));
                } else {
                    System.out.println(input + ": command not found");
                }
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
                } else {
                    current.append(c);
                }
                i++;
            } else {
                if (c == '\'') {
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

    private static void runExternalProgram(String[] tokens) {
        try {
            ProcessBuilder pb = new ProcessBuilder(tokens);
            pb.inheritIO();
            Process process = pb.start();
            process.waitFor();
        } catch (Exception e) {
            System.out.println(tokens[0] + ": error executing program");
        }
    }
}