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

            // echo builtin
            if (s.startsWith("echo ")) {
                System.out.println(s.substring(5));
                continue;
            }

            // type builtin
            if (s.startsWith("type ")) {
                String cmd = s.substring(5);

                // Builtins
                if (cmd.equals("echo") ||
                    cmd.equals("exit") ||
                    cmd.equals("type")) {

                    System.out.println(cmd + " is a shell builtin");
                    continue;
                }

                // Search PATH
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

            // Invalid command
            System.out.println(s + ": command not found");
        }
    }
}
