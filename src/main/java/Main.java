import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        // TODO: Uncomment the code below to pass the first stage
        Scanner in = new Scanner(System.in);
        while(true){
            System.out.print("$ ");
            String s=in.next();
            if(s.equals("exit")) break;
            System.out.println(s+": command not found");
        }
        
        // in.close();
    }
}
