import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
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

            if (!inSingleQuote
                    && !inDoubleQuote
                    && c == '\\') {

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

            if (Character.isWhitespace(c)
                    && !inSingleQuote
                    && !inDoubleQuote) {

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

        if (pathEnv == null) {
            return null;
        }

        String[] dirs = pathEnv.split(File.pathSeparator);

        for (String dir : dirs) {

            File file = new File(dir, cmd);

            if (file.exists()
                    && file.isFile()
                    && file.canExecute()) {

                return file.getAbsolutePath();
            }
        }

        return null;
    }

    public static void main(String[] args) throws Exception {

        Scanner input = new Scanner(System.in);

        while (true) {

            System.out.print("$ ");

            String line = input.nextLine();

            List<String> parts = parseCommand(line);

            if (parts.isEmpty()) {
                continue;
            }

            String redirectFile = null;

            for (int i = 0; i < parts.size(); i++) {

                if (parts.get(i).equals(">") ||
                    parts.get(i).equals("1>")) {

                    if (i + 1 < parts.size()) {

                        redirectFile = parts.get(i + 1);

                        parts.remove(i + 1);
                        parts.remove(i);
                    }

                    break;
                }
            }

            if (parts.isEmpty()) {
                continue;
            }

            String cmd = parts.get(0);

            PrintStream originalOut = System.out;
            PrintStream redirectedOut = null;

            if (redirectFile != null) {

                File file = new File(redirectFile);

                if (!file.isAbsolute()) {
                    file = new File(currentDirectory, redirectFile);
                }

                redirectedOut =
                        new PrintStream(
                                new FileOutputStream(file, false));

                System.setOut(redirectedOut);
            }

            try {

                // exit
                if (cmd.equals("exit")) {
                    break;
                }

                // pwd
                if (cmd.equals("pwd")) {

                    System.out.println(
                            currentDirectory.getCanonicalPath()
                    );

                    continue;
                }

                // cd
                if (cmd.equals("cd")) {

                    if (parts.size() < 2) {
                        continue;
                    }

                    String path = parts.get(1);

                    File target;

                    if (path.equals("~")) {

                        target = new File(
                                System.getenv("HOME")
                        );

                    } else if (new File(path).isAbsolute()) {

                        target = new File(path);

                    } else {

                        target = new File(
                                currentDirectory,
                                path
                        );
                    }

                    target = target.getCanonicalFile();

                    if (target.exists()
                            && target.isDirectory()) {

                        currentDirectory = target;

                    } else {

                        System.err.println(
                                "cd: "
                                        + path
                                        + ": No such file or directory"
                        );
                    }

                    continue;
                }

                // echo
                if (cmd.equals("echo")) {

                    StringBuilder out = new StringBuilder();

                    for (int i = 1; i < parts.size(); i++) {

                        if (i > 1) {
                            out.append(" ");
                        }

                        out.append(parts.get(i));
                    }

                    System.out.println(out);

                    continue;
                }

                // type
                if (cmd.equals("type")) {

                    if (parts.size() < 2) {
                        continue;
                    }

                    String target = parts.get(1);

                    if (target.equals("echo")
                            || target.equals("exit")
                            || target.equals("type")
                            || target.equals("pwd")
                            || target.equals("cd")) {

                        System.out.println(
                                target + " is a shell builtin"
                        );

                    } else {

                        String executable =
                                findExecutable(target);

                        if (executable != null) {

                            System.out.println(
                                    target
                                            + " is "
                                            + executable
                            );

                        } else {

                            System.out.println(
                                    target + ": not found"
                            );
                        }
                    }

                    continue;
                }

                // external commands
                String executable = findExecutable(cmd);

                if (executable != null) {

                    ProcessBuilder pb =
                            new ProcessBuilder(parts);

                    pb.directory(currentDirectory);

                    if (redirectFile != null) {

                        File file = new File(redirectFile);

                        if (!file.isAbsolute()) {
                            file = new File(
                                    currentDirectory,
                                    redirectFile
                            );
                        }

                        pb.redirectOutput(file);
                        pb.redirectError(
                                ProcessBuilder.Redirect.INHERIT
                        );

                    } else {

                        pb.inheritIO();
                    }

                    Process process = pb.start();
                    process.waitFor();

                } else {

                    System.out.println(
                            cmd + ": command not found"
                    );
                }

            } finally {

                if (redirectedOut != null) {
                    redirectedOut.close();
                    System.setOut(originalOut);
                }
            }
        }
    }
}
