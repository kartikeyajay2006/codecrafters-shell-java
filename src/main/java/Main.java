import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner input = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");

            String s = input.nextLine();

            if (s.equals("exit")) {
                break;
            }

            if (s.startsWith("echo ")) {
                System.out.println(s.substring(5));
                continue;
            }

            System.out.println(s + ": command not found");
        }
    }
}
