import java.util.Scanner;
import java.util.List;
import java.util.ArrayList;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner in = new Scanner(System.in);
        String currentDirectory = System.getProperty("user.dir");
        mainLoop: while (true) {
            reapBeforePrompt();
            System.out.print("$ ");
            String input = in.nextLine();
            if (input.equals("exit")) break;

            List<String> rawTokens = tokenize(input);
            if (rawTokens.isEmpty()) continue;

            if (rawTokens.contains("|")) {
                runPipeline(splitOnPipe(rawTokens));
                continue;
            }

            Redirection r = new Redirection(rawTokens);
            if (r.outputFile != null) ensureFile(r.outputFile, r.outputAppend);
            if (r.errorFile != null) ensureFile(r.errorFile, r.errorAppend);
            List<String> tokens = r.tokens;
            if (tokens.isEmpty()) continue;

            boolean background = false;
            if (!tokens.isEmpty() && tokens.get(tokens.size() - 1).equals("&")) {
                background = true;
                tokens.remove(tokens.size() - 1);
            }
            if (tokens.isEmpty()) continue;

            String command = tokens.get(0);
            switch (command) {
                case "exit":
                    break mainLoop;
                case "type":
                    String rem = tokens.size() > 1 ? tokens.get(1) : "";
                    String res;
                    if (List.of("echo", "type", "exit", "pwd", "cd", "jobs").contains(rem)) {
                        res = rem + " is a shell builtin";
                    } else {
                        java.io.File exe = findExecutable(rem);
                        res = exe != null ? rem + " is " + exe.getAbsolutePath() : rem + ": not found";
                    }
                    writeOutput(res, r.outputFile, r.outputAppend);
                    break;
                case "pwd":
                    writeOutput(currentDirectory, r.outputFile, r.outputAppend);
                    break;
                case "cd":
                    String target = tokens.size() > 1 ? tokens.get(1) : "";
                    if (target.equals("~")) target = System.getenv("HOME");
                    else if (target.startsWith("~/")) target = System.getenv("HOME") + target.substring(1);
                    java.io.File dir = target.startsWith("/") ? new java.io.File(target) : new java.io.File(currentDirectory, target);
                    try { dir = dir.getCanonicalFile(); } catch (Exception e) {}
                    if (dir.exists() && dir.isDirectory()) {
                        currentDirectory = dir.getAbsolutePath();
                        System.setProperty("user.dir", currentDirectory);
                    } else {
                        writeOutput("cd: " + target + ": No such file or directory", r.errorFile, r.errorAppend);
                    }
                    break;
                case "echo":
                    writeOutput(String.join(" ", tokens.subList(1, tokens.size())), r.outputFile, r.outputAppend);
                    break;
                case "jobs":
                    markExitedJobs();
                    int[] markers = computeJobMarkers();
                    jobs.stream().sorted((a, b) -> Integer.compare(a.number, b.number))
                        .forEach(j -> writeOutput(formatJobLine(j, markers[0], markers[1]), r.outputFile, r.outputAppend));
                    jobs.removeIf(j -> j.status.equals("Done"));
                    break;
                default:
                    if (findExecutable(command) != null) {
                        runExternalProgram(tokens.toArray(new String[0]), r.outputFile, r.outputAppend, r.errorFile, r.errorAppend, background, input);
                    } else {
                        System.out.println(input + ": command not found");
                    }
            }
        }
    }

    private static List<Job> jobs = new ArrayList<>();

    private static class Job {
        int number; long pid; String command, status; Process process;
        Job(int n, long p, String c, String s, Process pr) { number = n; pid = p; command = c; status = s; process = pr; }
    }

    static class Redirection {
        String outputFile, errorFile;
        boolean outputAppend, errorAppend;
        List<String> tokens = new ArrayList<>();
        Redirection(List<String> raw) {
            for (int i = 0; i < raw.size(); i++) {
                String t = raw.get(i);
                if (t.matches("1?>{1,2}|2>{1,2}") && i + 1 < raw.size()) {
                    boolean err = t.startsWith("2");
                    if (err) { errorFile = raw.get(i + 1); errorAppend = t.endsWith(">>"); }
                    else { outputFile = raw.get(i + 1); outputAppend = t.endsWith(">>"); }
                    i++;
                } else {
                    tokens.add(t);
                }
            }
        }
    }

    private static int nextJobNumber() {
        return jobs.stream().mapToInt(j -> j.number).max().orElse(0) + 1;
    }

    private static void markExitedJobs() {
        jobs.forEach(j -> { if (j.process != null && !j.process.isAlive()) j.status = "Done"; });
    }

    private static int[] computeJobMarkers() {
        List<Integer> n = jobs.stream().map(j -> j.number).sorted().toList();
        return new int[] { n.isEmpty() ? -1 : n.get(n.size() - 1), n.size() < 2 ? -1 : n.get(n.size() - 2) };
    }

    private static String formatJobLine(Job job, int cur, int prev) {
        String m = job.number == cur ? "+" : (job.number == prev ? "-" : " ");
        String cmd = job.command;
        if (job.status.equals("Done") && cmd.endsWith(" &")) cmd = cmd.substring(0, cmd.length() - 2);
        return String.format("[%d]%s  %-24s%s", job.number, m, job.status, cmd);
    }

    private static void reapBeforePrompt() {
        markExitedJobs();
        int[] m = computeJobMarkers();
        jobs.stream().filter(j -> j.status.equals("Done"))
            .sorted((a, b) -> Integer.compare(a.number, b.number))
            .forEach(j -> System.out.println(formatJobLine(j, m[0], m[1])));
        jobs.removeIf(j -> j.status.equals("Done"));
    }

    private static List<List<String>> splitOnPipe(List<String> tokens) {
        List<List<String>> res = new ArrayList<>();
        List<String> cur = new ArrayList<>();
        for (String t : tokens) {
            if (t.equals("|")) { res.add(cur); cur = new ArrayList<>(); }
            else cur.add(t);
        }
        res.add(cur);
        return res;
    }

    private static void runPipeline(List<List<String>> segments) {
        List<ProcessBuilder> builders = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            Redirection r = new Redirection(segments.get(i));
            if (r.tokens.isEmpty()) continue;
            if (r.outputFile != null) ensureFile(r.outputFile, r.outputAppend);
            if (r.errorFile != null) ensureFile(r.errorFile, r.errorAppend);
            if (findExecutable(r.tokens.get(0)) == null) {
                System.out.println(r.tokens.get(0) + ": command not found");
                return;
            }
            ProcessBuilder pb = new ProcessBuilder(r.tokens);
            if (i == 0) pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
            if (i == segments.size() - 1) {
                if (r.outputFile != null) {
                    pb.redirectOutput(r.outputAppend ? ProcessBuilder.Redirect.appendTo(new java.io.File(r.outputFile))
                            : ProcessBuilder.Redirect.to(new java.io.File(r.outputFile)));
                } else pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            }
            if (r.errorFile != null) {
                pb.redirectError(r.errorAppend ? ProcessBuilder.Redirect.appendTo(new java.io.File(r.errorFile))
                        : ProcessBuilder.Redirect.to(new java.io.File(r.errorFile)));
            } else pb.redirectError(ProcessBuilder.Redirect.INHERIT);
            builders.add(pb);
        }
        if (builders.isEmpty()) return;
        try {
            for (Process p : ProcessBuilder.startPipeline(builders)) p.waitFor();
        } catch (Exception e) {
            System.out.println("pipeline: error executing");
        }
    }

    private static void ensureFile(String path, boolean append) {
        try {
            java.io.File file = new java.io.File(path);
            java.io.File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            if (!append || !file.exists()) new java.io.FileWriter(file, append).close();
        } catch (Exception e) {}
    }

    private static void writeOutput(String text, String targetFile, boolean append) {
        if (targetFile == null) {
            System.out.println(text);
        } else {
            try {
                java.io.File file = new java.io.File(targetFile);
                if (file.getParentFile() != null) file.getParentFile().mkdirs();
                try (java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.FileWriter(file, append))) {
                    writer.println(text);
                }
            } catch (Exception e) {}
        }
    }

    private static List<String> tokenize(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuotes = false, inDoubleQuotes = false, tokenStarted = false;
        int i = 0, len = input.length();
        while (i < len) {
            char c = input.charAt(i);
            if (inSingleQuotes) {
                if (c == '\'') inSingleQuotes = false;
                else current.append(c);
                i++;
            } else if (inDoubleQuotes) {
                if (c == '"') { inDoubleQuotes = false; i++; }
                else if (c == '\\' && i + 1 < len && (input.charAt(i + 1) == '"' || input.charAt(i + 1) == '\\' ||
                        input.charAt(i + 1) == '$' || input.charAt(i + 1) == '`' || input.charAt(i + 1) == '\n')) {
                    current.append(input.charAt(i + 1));
                    i += 2;
                } else { current.append(c); i++; }
            } else {
                if (c == '\\') {
                    if (i + 1 < len) { current.append(input.charAt(i + 1)); tokenStarted = true; i += 2; }
                    else i++;
                } else if (c == '\'') { inSingleQuotes = true; tokenStarted = true; i++; }
                else if (c == '"') { inDoubleQuotes = true; tokenStarted = true; i++; }
                else if (Character.isWhitespace(c)) {
                    if (tokenStarted) { tokens.add(current.toString()); current.setLength(0); tokenStarted = false; }
                    i++;
                } else { current.append(c); tokenStarted = true; i++; }
            }
        }
        if (tokenStarted) tokens.add(current.toString());
        return tokens;
    }

    private static java.io.File findExecutable(String cmd) {
        String path = System.getenv("PATH");
        if (path == null) return null;
        for (String dir : path.split(java.io.File.pathSeparator)) {
            java.io.File file = new java.io.File(dir, cmd);
            if (file.exists() && file.canExecute()) return file;
        }
        return null;
    }

    private static void runExternalProgram(String[] tokens, String outFile, boolean outApp, String errFile,
            boolean errApp, boolean bg, String origInput) {
        try {
            ProcessBuilder pb = new ProcessBuilder(tokens);
            pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
            pb.redirectOutput(outFile != null ? (outApp ? ProcessBuilder.Redirect.appendTo(new java.io.File(outFile))
                    : ProcessBuilder.Redirect.to(new java.io.File(outFile))) : ProcessBuilder.Redirect.INHERIT);
            pb.redirectError(errFile != null ? (errApp ? ProcessBuilder.Redirect.appendTo(new java.io.File(errFile))
                    : ProcessBuilder.Redirect.to(new java.io.File(errFile))) : ProcessBuilder.Redirect.INHERIT);
            Process p = pb.start();
            if (bg) {
                int jn = nextJobNumber();
                System.out.println("[" + jn + "] " + p.pid());
                String cmd = origInput.trim();
                jobs.add(new Job(jn, p.pid(), cmd.endsWith("&") ? cmd : cmd + " &", "Running", p));
            } else p.waitFor();
        } catch (Exception e) {
            System.out.println(tokens[0] + ": error executing program");
        }
    }
}
