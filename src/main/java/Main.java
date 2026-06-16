import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner in = new Scanner(System.in);
        while (true) {
            System.out.print("$ ");
            String input = in.nextLine();
            if (input.equals("exit")) break;
            else if (input.startsWith("type ")) {
                String rem = input.substring(5, input.length());
                if (rem.equals("echo") || rem.equals("type") || rem.equals("exit") || rem.equals("pwd")) {
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
                System.out.println(System.getProperty("user.dir"));
            } else if (input.startsWith("echo ")) {
                System.out.println(input.substring(5));
            } else {
                // Split the input into the command name and its arguments.
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

    // Searches PATH for an executable matching the given command name.
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

    // Runs an external program, passing the program name and arguments,
    // and lets its output/error streams pass through to this process.
    private static void runExternalProgram(String[] tokens) {
        try {
            // tokens[0] is the program name as typed by the user (argv[0]),
            // which ProcessBuilder will also use to locate/launch the program.
            ProcessBuilder pb = new ProcessBuilder(tokens);
            pb.inheritIO(); // pass through stdin/stdout/stderr directly
            Process process = pb.start();
            process.waitFor();
        } catch (Exception e) {
            System.out.println(tokens[0] + ": error executing program");
        }
    }
}