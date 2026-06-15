import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        // TODO: Uncomment the code below to pass the first stage
        Scanner in = new Scanner(System.in);
        while(true){
            System.out.print("$ ");
            String input=in.nextLine();
            if(input.equals("exit")) break;
            else if(input.startsWith("type ")){
                String rem=input.substring(5,input.length());
                if(rem.equals("echo") || rem.equals("type") || rem.equals("exit")) System.out.println(rem+" is a shell builtin");
                else System.out.println(rem+": not found");
            }
            else if(input.startsWith("echo ")){
                System.out.println(input.substring(5));
            }
            else System.out.println(input+": command not found");
        }
        
        // in.close();
    }
}
