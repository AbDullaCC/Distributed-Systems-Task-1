import javax.crypto.SecretKey;
import javax.naming.ServiceUnavailableException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.security.InvalidParameterException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class Client {

    private String token;
    private final CoordinatorInt coordinator;
    private final Scanner scanner;

    public Client(CoordinatorInt coordinator) {
        this.coordinator = coordinator;
        scanner = new Scanner(System.in);
    }

    private void run() throws RemoteException {

        boolean working = true;
        while (working) {
            //Not logged in
            if (!coordinator.isValidToken(token)){
                try {
                    this.token = this.login();
                }
                catch (InvalidParameterException exception){
                    System.out.println("Invalid username or password, try again");
                }
            }

            //Is actually logged in
            else {
                try {
                    String department = this.chooseDepartment();

                    int action = this.chooseAction(department);

                    this.executeRequestedAction(action, department);

                    System.out.println("Action concluded successfully.");
                }
                catch (InvalidParameterException exception){
                    System.out.println(exception.getMessage());
                }
                catch (IllegalStateException exception){
                    System.out.println("Application is terminated");
                    working = false;
                }

            }
        }
    }

    private String login() throws RemoteException, InvalidParameterException {
        System.out.println("*You must login*");

        System.out.print("username: ");
        String username = scanner.nextLine();

        System.out.print("password: ");
        String password = scanner.nextLine();

        return coordinator.login(username, password);
    }

    private String chooseDepartment() throws RemoteException, InvalidParameterException, IllegalStateException {

        List<String> departments = coordinator.getDepartments(token);

        System.out.println("Available departments: ");
        int choice = this.getUserChoice(departments);

        //decrement "choice" by 1 because the index should start from 0.
        return departments.get(choice-1);
    }

    private int chooseAction(String department) throws RemoteException, InvalidParameterException, IllegalStateException {

        List<String> actions = this.getAllowedActions(department);
        System.out.println("Available actions: ");

        return this.getUserChoice(actions);
    }

    private List<String> getAllowedActions(String department) throws RemoteException, InvalidParameterException {
        List<String> actions = new ArrayList<>(List.of("Get files"));
        if (coordinator.otherActionsAllowed(this.token, department)){
            actions.addAll(Arrays.asList(
                    "Upload a file",
                    "Update a file",
                    "Delete a file"
            ));
        }
        return actions;
    }

    private void executeRequestedAction(int action, String department) throws RemoteException, InvalidParameterException, IllegalStateException {

        switch (action) {
            case 1:
                this.downloadFile(department);
                break;
//            case 2:
//                this.uploadFile(department);
//                break;
//            case 3:
//                this.updateFile(department);
//                break;
//            case 4:
//                this.deleteFile(department);
//                break;
            default:
                throw new IllegalArgumentException("Invalid action, something went wrong");
        }
    }

    private void downloadFile(String department) throws RemoteException, InvalidParameterException, IllegalStateException {

        List<String> fileNames = this.getDepartmentFiles(department);

        System.out.println("Available files to download: ");
        int choice = this.getUserChoice(fileNames);

        String fileName = fileNames.get(choice - 1);

        try (ServerSocket socket = new ServerSocket(8000)) {  // 0 = auto-pick port
            int port = socket.getLocalPort();

            coordinator.fileGet(token, "localhost", port, fileName, department);

            Socket nodeConnection = socket.accept();

            try (InputStream fileStream = nodeConnection.getInputStream();
                 FileOutputStream fileOut = new FileOutputStream(fileName)) {

                // Step 3: Receive file directly
                fileStream.transferTo(fileOut);  // Java 9+
            }
        } catch (IOException | ServiceUnavailableException e) {
            throw new RuntimeException(e);
        }
    }

    private List<String> getDepartmentFiles(String department) throws RemoteException {
        return this.coordinator.getDepartmentFiles(token, department);
    }

    private int getUserChoice(List<String> options) throws IllegalStateException{

        int i = 1;
        for(String option : options){
            System.out.println(i++ + " - " + option + ".");
        }
        System.out.println("0 - Exit Application \n -----------------------\n" +
                "Enter the number of the choice you want: ");

        int choice;
        while (true) {
            choice = scanner.nextInt();
            if(choice >= 0 && choice <= options.size()) break;
            System.out.println("Please choose a valid choice number, or terminate the app: ");
        }

        if (choice == 0) {throw new IllegalStateException();}

        return choice;
    }



    public static void main(String[] args) throws Exception {

        try {
            CoordinatorInt coordinator = (CoordinatorInt) Naming.lookup("rmi://localhost:5000/coordinator");
            Client client = new Client(coordinator);

            client.run();
        }
        catch (Exception exception){
            System.out.println(exception);
        }
    }
}
