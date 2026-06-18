import org.jline.reader.*;
import org.jline.reader.impl.completer.StringsCompleter;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Main {

    private static File currentDirectory =
            new File(System.getProperty("user.dir"));

    private static final List<String> BUILTINS =
            List.of("echo", "exit", "type", "pwd", "cd", "jobs");

    private static class Job {
        int number;
        long pid;
        String command;
        Process process;

        Job(int number, long pid, String command, Process process) {
            this.number = number;
            this.pid = pid;
            this.command = command;
            this.process = process;
        }

        boolean isRunning() {
            return process.isAlive();
        }
    }

    private static final List<Job> jobs = new ArrayList<>();

    /**
     * Automatic reaping logic that runs *before the prompt*.
     * It checks for dead jobs, prints their 'Done' status line in correct list order,
     * and clears them from our job tracker.
     */
    private static void reapCompletedJobsBeforePrompt() {
        int lastIdx = jobs.size() - 1;
        int secondLastIdx = jobs.size() - 2;

        List<Job> jobsToRemove = new ArrayList<>();

        for (int i = 0; i < jobs.size(); i++) {
            Job job = jobs.get(i);
            if (!job.isRunning()) {
                String marker;
                if (i == lastIdx) {
                    marker = "+";
                } else if (i == secondLastIdx) {
                    marker = "-";
                } else {
                    marker = " ";
                }

                String statusStr = String.format("%-24s", "Done");
                System.out.println("[" + job.number + "]" + marker + "  " + statusStr + job.command);
                jobsToRemove.add(job);
            }
        }
        jobs.removeAll(jobsToRemove);
    }

    /**
     * Finds the smallest available job number starting from 1.
     */
    private static int getNextAvailableJobNumber() {
        int candidate = 1;
        while (true) {
            boolean used = false;
            for (Job job : jobs) {
                if (job.number == candidate) {
                    used = true;
                    break;
                }
            }
            if (!used) {
                return candidate;
            }
            candidate++;
        }
    }

    /**
     * Inserts a job into the jobs list keeping it sorted by job number.
     */
    private static void addJobSorted(Job newJob) {
        int index = 0;
        while (index < jobs.size() && jobs.get(index).number < newJob.number) {
            index++;
        }
        jobs.add(index, newJob);
    }

    private static List<String> parseCommand(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (inDoubleQuote && c == '\\') {
                if (i + 1 < input.length()) {
                    char next = input.charAt(i + 1);
                    if (next == '"' || next == '\\') {
                        current.append(next);
                        i++;
                    } else {
                        current.append('\\');
                        current.append(next);
                        i++;
                    }
                } else {
                    current.append('\\');
                }
                continue;
            }

            if (!inSingleQuote && !inDoubleQuote && c == '\\') {
                if (i + 1 < input.length()) {
                    current.append(input.charAt(i + 1));
                    i++;
                } else {
                    current.append('\\');
                }
                continue;
            }

            if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
                continue;
            }

            if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                continue;
            }

            if (Character.isWhitespace(c) && !inSingleQuote && !inDoubleQuote) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }

        if (current.length() > 0) {
            tokens.add(current.toString());
        }

        return tokens;
    }

    private static String findExecutable(String cmd) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) return null;

        for (String dir : pathEnv.split(File.pathSeparator)) {
            File file = new File(dir, cmd);
            if (file.exists() && file.isFile() && file.canExecute()) {
                return file.getAbsolutePath();
            }
        }

        return null;
    }

    private static class Redirect {
        int index;
        String operator;
        String file;

        Redirect(int index, String operator, String file) {
            this.index = index;
            this.operator = operator;
            this.file = file;
        }

        boolean isStderr() {
            return operator.equals("2>") || operator.equals("2>>");
        }

        boolean isAppend() {
            return operator.equals(">>") || operator.equals("1>>") || operator.equals("2>>");
        }
    }

    private static Redirect findRedirect(List<String> parts) {
        for (int i = 0; i < parts.size(); i++) {
            String token = parts.get(i);
            if ((token.equals(">")
                    || token.equals("1>")
                    || token.equals(">>")
                    || token.equals("1>>")
                    || token.equals("2>")
                    || token.equals("2>>"))
                    && i + 1 < parts.size()) {
                return new Redirect(i, token, parts.get(i + 1));
            }
        }
        return null;
    }

    private static File resolveFile(String fileName) {
        File target = new File(fileName);
        if (!target.isAbsolute()) {
            target = new File(currentDirectory, fileName);
        }
        return target;
    }

    private static void writeToFile(File target, String content, boolean append) throws Exception {
        if (target.getParentFile() != null) {
            target.getParentFile().mkdirs();
        }
        try (FileWriter writer = new FileWriter(target, append)) {
            writer.write(content);
        }
    }

    public static void main(String[] args) throws Exception {
        Terminal terminal = TerminalBuilder.builder()
                .system(true)
                .build();

        Completer completer = (reader, line, candidates) -> {
            String buffer = line.line();
            int cursor = line.cursor();
            String beforeCursor = buffer.substring(0, cursor);
            if (beforeCursor.contains(" ")) return;

            String word = line.word();
            for (String builtin : BUILTINS) {
                if (builtin.startsWith(word) && !builtin.equals(word)) {
                    candidates.add(new Candidate(
                            builtin,
                            builtin,
                            null,
                            null,
                            null,
                            null,
                            true
                    ));
                }
            }
        };

        LineReader lineReader = LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(completer)
                .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
                .option(LineReader.Option.AUTO_LIST, false)
                .option(LineReader.Option.AUTO_MENU, false)
                .option(LineReader.Option.MENU_COMPLETE, false)
                .variable(LineReader.BELL_STYLE, "none")
                .build();

        while (true) {
            String line;
            try {
                // Reap completed jobs cleanly right before presenting the prompt string
                reapCompletedJobsBeforePrompt();
                line = lineReader.readLine("$ ");
            } catch (EndOfFileException | UserInterruptException e) {
                break;
            }

            List<String> parts = parseCommand(line);

            if (parts.isEmpty()) continue;

            // Check for background operator &
            boolean background = false;
            if (parts.get(parts.size() - 1).equals("&")) {
                background = true;
                parts = new ArrayList<>(parts.subList(0, parts.size() - 1));
                if (parts.isEmpty()) continue;
            }

            String cmd = parts.get(0);

            if (cmd.equals("exit")) break;

            if (cmd.equals("pwd")) {
                System.out.println(currentDirectory.getCanonicalPath());
                continue;
            }

            if (cmd.equals("cd")) {
                if (parts.size() < 2) continue;

                String path = parts.get(1);
                File target;

                if (path.equals("~")) {
                    target = new File(System.getenv("HOME"));
                } else if (new File(path).isAbsolute()) {
                    target = new File(path);
                } else {
                    target = new File(currentDirectory, path);
                }

                target = target.getCanonicalFile();

                if (target.exists() && target.isDirectory()) {
                    currentDirectory = target;
                } else {
                    System.out.println("cd: " + path + ": No such file or directory");
                }
                continue;
            }

            if (cmd.equals("jobs")) {
                // Read frame markers ahead of modifications across the job pool layout
                int lastIdx = jobs.size() - 1;
                int secondLastIdx = jobs.size() - 2;

                List<Job> jobsToRemove = new ArrayList<>();

                // Single linear loop preserves exact order matching index tracking
                for (int i = 0; i < jobs.size(); i++) {
                    Job job = jobs.get(i);
                    boolean active = job.isRunning();

                    String marker;
                    if (i == lastIdx) {
                        marker = "+";
                    } else if (i == secondLastIdx) {
                        marker = "-";
                    } else {
                        marker = " ";
                    }

                    if (active) {
                        String statusStr = String.format("%-24s", "Running");
                        System.out.println("[" + job.number + "]" + marker + "  " + statusStr + job.command + " &");
                    } else {
                        String statusStr = String.format("%-24s", "Done");
                        System.out.println("[" + job.number + "]" + marker + "  " + statusStr + job.command);
                        jobsToRemove.add(job);
                    }
                }

                // Discard entries evaluated as Done
                jobs.removeAll(jobsToRemove);
                continue;
            }

            if (cmd.equals("echo")) {
                Redirect redirect = findRedirect(parts);

                StringBuilder out = new StringBuilder();
                int limit = redirect == null ? parts.size() : redirect.index;

                for (int i = 1; i < limit; i++) {
                    if (i > 1) out.append(" ");
                    out.append(parts.get(i));
                }

                if (redirect != null) {
                    if (redirect.isStderr()) {
                        File target = resolveFile(redirect.file);
                        writeToFile(target, "", redirect.isAppend());
                        System.out.println(out);
                    } else {
                        File target = resolveFile(redirect.file);
                        writeToFile(target, out.toString() + System.lineSeparator(), redirect.isAppend());
                    }
                } else {
                    System.out.println(out);
                }
                continue;
            }

            if (cmd.equals("type")) {
                if (parts.size() < 2) continue;

                String target = parts.get(1);

                if (BUILTINS.contains(target)) {
                    System.out.println(target + " is a shell builtin");
                } else {
                    String executable = findExecutable(target);
                    if (executable != null) {
                        System.out.println(target + " is " + executable);
                    } else {
                        System.out.println(target + ": not found");
                    }
                }
                continue;
            }

            // external commands
            String executable = findExecutable(cmd);

            if (executable != null) {
                Redirect redirect = findRedirect(parts);

                if (background) {
                    List<String> command = redirect != null
                            ? new ArrayList<>(parts.subList(0, redirect.index))
                            : new ArrayList<>(parts);

                    ProcessBuilder pb = new ProcessBuilder(command);
                    pb.directory(currentDirectory);
                    pb.inheritIO();

                    Process process = pb.start();
                    
                    // Calculate and assign the smallest available recycled job number
                    int assignedJobNumber = getNextAvailableJobNumber();
                    long pid = process.pid();

                    String commandStr = String.join(" ", command);
                    
                    // Always add sorted to maintain consistent output rendering
                    addJobSorted(new Job(assignedJobNumber, pid, commandStr, process));

                    System.out.println("[" + assignedJobNumber + "] " + pid);

                } else if (redirect != null) {
                    List<String> command = new ArrayList<>(parts.subList(0, redirect.index));

                    ProcessBuilder pb = new ProcessBuilder(command);
                    pb.directory(currentDirectory);
                    pb.redirectErrorStream(false);

                    Process process = pb.start();

                    byte[] stdoutBytes = process.getInputStream().readAllBytes();
                    byte[] stderrBytes = process.getErrorStream().readAllBytes();

                    process.waitFor();

                    File target = resolveFile(redirect.file);

                    if (redirect.isStderr()) {
                        writeToFile(target, new String(stderrBytes), redirect.isAppend());
                        System.out.print(new String(stdoutBytes));
                    } else {
                        writeToFile(target, new String(stdoutBytes), redirect.isAppend());
                        System.err.print(new String(stderrBytes));
                    }

                } else {
                    ProcessBuilder pb = new ProcessBuilder(parts);
                    pb.directory(currentDirectory);
                    pb.inheritIO();

                    Process process = pb.start();
                    process.waitFor();
                }

            } else {
                System.out.println(cmd + ": command not found");
            }
        }

        terminal.close();
    }
}
