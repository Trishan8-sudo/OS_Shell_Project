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
                String rem=input.substring(5);
                if(rem=="echo" || rem=="type" || rem=="exit") System.out.println(rem+" is a shell builtin");
                else System.out.println(rem+": not found");
            }
            else System.out.println(input+": command not found");
        }
        
        // in.close();
    }
}
