import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        // TODO: Uncomment the code below to pass the first stage
         Scanner input = new Scanner(System.in);
         while(true){
              System.out.print("$ ");
              String s = input.nextLine();
              if (s.equals("exit")){
                   break;
              }
              System.out.println(s+": command not found");
         }
    }
}
