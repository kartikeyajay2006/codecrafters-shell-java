import java.io.File;
import java.util.Scanner;

public class Main {

    private static File currentDirectory =
            new File(System.getProperty("user.dir"));

    public static void main(String[] args) throws Exception {
        Scanner input = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");

            String s = input.nextLine();

            // exit
            if (s.equals("exit")) {
                break;
            }

            // pwd
            if (s.equals("pwd")) {
                System.out.println(currentDirectory.getCanonicalPath());
                continue;
            }

            // cd
            if (s.startsWith("cd ")) {
                String path = s.substring(3);

                File target;

                if (new File(path).isAbsolute()) {
                    target = new File(path);
                } else {
                    target = new File(currentDirectory, path);
                }

                target = target.getCanonicalFile();

                if (target.exists() && target.isDirectory()) {
                    currentDirectory = target;
                } else {
                    System.out.println(
                        "cd: " + path + ": No such file or directory"
                    );
                }

                continue;
            }

            // echo
            if (s.startsWith("echo ")) {
                System.out.println(s.substring(5));
                continue;
            }

            // type
            if (s.startsWith("type ")) {
                String cmd = s.substring(5);

                if (cmd.equals("echo") ||
                    cmd.equals("exit") ||
                    cmd.equals("type") ||
                    cmd.equals("pwd") ||
                    cmd.equals("cd")) {

                    System.out.println(cmd + " is a shell builtin");
                    continue;
                }

                String pathEnv = System.getenv("PATH");
                String[] dirs = pathEnv.split(File.pathSeparator);

                boolean found = false;

                for (String dir : dirs) {
                    File file = new File(dir, cmd);

                    if (file.exists() &&
                        file.isFile() &&
                        file.canExecute()) {

                        System.out.println(
                            cmd + " is " + file.getAbsolutePath()
                        );

                        found = true;
                        break;
                    }
                }

                if (!found) {
                    System.out.println(cmd + ": not found");
                }

                continue;
            }

            // external commands
            String[] parts = s.split(" ");

            String cmd = parts[0];

            String pathEnv = System.getenv("PATH");
            String[] dirs = pathEnv.split(File.pathSeparator);

            boolean found = false;

            for (String dir : dirs) {
                File file = new File(dir, cmd);

                if (file.exists() &&
                    file.isFile() &&
                    file.canExecute()) {

                    found = true;
                    break;
                }
            }

            if (found) {
                ProcessBuilder pb = new ProcessBuilder(parts);

                pb.directory(currentDirectory);
                pb.inheritIO();

                Process p = pb.start();
                p.waitFor();
            } else {
                System.out.println(cmd + ": command not found");
            }
        }
    }
}
