import java.util.Scanner;

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

                if (targetPath.startsWith("/")) {
                    java.io.File targetDir = new java.io.File(targetPath);
                    if (targetDir.exists() && targetDir.isDirectory()) {
                        currentDirectory = targetDir.getAbsolutePath();
                        System.setProperty("user.dir", currentDirectory);
                    } else {
                        System.out.println("cd: " + targetPath + ": No such file or directory");
                    }
                } else {
                    System.out.println("cd: " + targetPath + ": No such file or directory");
                }
            } else if (input.startsWith("echo ")) {
                System.out.println(input.substring(5));
            } else {
                String[] tokens = input.trim().split("\\s+");
                if (tokens.length == 0 || tokens[0].isEmpty()) continue;

                String command = tokens[0];
                java.io.File exeFile = findExecutable(command);

                if (exeFile != null) {
                    runExternalProgram(tokens);
                } else {
                    System.out.println(input + ": command not found");
                }
            }
        }

        // in.close();
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