import java.io.File;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner input = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");

            String s = input.nextLine();

            // exit builtin
            if (s.equals("exit")) {
                break;
            }

            // pwd builtin
            if (s.equals("pwd")) {
                System.out.println(System.getProperty("user.dir"));
                continue;
            }

            // echo builtin
            if (s.startsWith("echo ")) {
                System.out.println(s.substring(5));
                continue;
            }

            // type builtin
            if (s.startsWith("type ")) {
                String cmd = s.substring(5);

                if (cmd.equals("echo") ||
                    cmd.equals("exit") ||
                    cmd.equals("type") ||
                    cmd.equals("pwd")) {

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

            // External command execution
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

                pb.inheritIO();

                Process p = pb.start();
                p.waitFor();
            } else {
                System.out.println(cmd + ": command not found");
            }
        }
    }
}
