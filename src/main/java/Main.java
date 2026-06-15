import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        // TODO: Uncomment the code below to pass the first stage
        Scanner in = new Scanner(System.in);
        while(true){
            System.out.print("$ ");
            String s=in.nextLine();
            if(s.startsWith("echo")){
                int n=s.length();
                String sub=s.substring(5,n);
                System.out.println(sub);
            }
            else System.out.println(s+": command not found");
        }
        
        // in.close();
    }
}
