import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {

    private static File currentDirectory =
            new File(System.getProperty("user.dir"));

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

    // Represents a parsed redirect: operator token, file, and index in parts list
    private static class Redirect {
        int index;
        String operator;
        String file;

        Redirect(int index, String operator, String file) {
            this.index = index;
            this.operator  = operator;
            this.file = file;
        }

        boolean isStderr() {
            return operator.equals("2>") || operator.equals("2>>");
        }

        boolean isAppend() {
            return operator.equals(">>") || operator.equals("1>>") || operator.equals("2>>");
        }
    }

    /**
     * Find the FIRST redirect token in parts. Returns null if none found.
     * Recognised operators: >, 1>, >>, 1>>, 2>, 2>>
     */
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
        // Ensure parent dirs exist
        if (target.getParentFile() != null) {
            target.getParentFile().mkdirs();
        }
        try (FileWriter writer = new FileWriter(target, append)) {
            writer.write(content);
        }
    }

    public static void main(String[] args) throws Exception {

        Scanner input = new Scanner(System.in);

        while (true) {

            System.out.print("$ ");

            String line = input.nextLine();
            List<String> parts = parseCommand(line);

            if (parts.isEmpty()) continue;

            String cmd = parts.get(0);

            // exit
            if (cmd.equals("exit")) break;

            // pwd
            if (cmd.equals("pwd")) {
                System.out.println(currentDirectory.getCanonicalPath());
                continue;
            }

            // cd
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

            // echo
            if (cmd.equals("echo")) {
                Redirect redirect = findRedirect(parts);

                StringBuilder out = new StringBuilder();
                int limit = redirect == null ? parts.size() : redirect.index;

                for (int i = 1; i < limit; i++) {
                    if (i > 1) out.append(" ");
                    out.append(parts.get(i));
                }

                if (redirect != null) {
                    File target = resolveFile(redirect.file);
                    // echo only has stdout; 2> with echo writes nothing to the file
                    if (!redirect.isStderr()) {
                        writeToFile(target, out.toString() + System.lineSeparator(), redirect.isAppend());
                    }
                } else {
                    System.out.println(out);
                }
                continue;
            }

            // type
            if (cmd.equals("type")) {
                if (parts.size() < 2) continue;

                String target = parts.get(1);

                if (target.equals("echo") || target.equals("exit")
                        || target.equals("type") || target.equals("pwd")
                        || target.equals("cd")) {
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

                if (redirect != null) {
                    // Build command without redirect tokens
                    List<String> command = new ArrayList<>(parts.subList(0, redirect.index));

                    ProcessBuilder pb = new ProcessBuilder(command);
                    pb.directory(currentDirectory);
                    // Always capture both streams separately
                    pb.redirectErrorStream(false);

                    Process process = pb.start();

                    byte[] stdoutBytes = process.getInputStream().readAllBytes();
                    byte[] stderrBytes = process.getErrorStream().readAllBytes();

                    process.waitFor();

                    File target = resolveFile(redirect.file);

                    if (redirect.isStderr()) {
                        // 2> or 2>> : write stderr to file, print stdout normally
                        writeToFile(target, new String(stderrBytes), redirect.isAppend());
                        System.out.print(new String(stdoutBytes));
                    } else {
                        // > or >> or 1> or 1>> : write stdout to file, print stderr normally
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
    }
}
