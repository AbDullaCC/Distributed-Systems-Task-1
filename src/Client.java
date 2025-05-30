import javax.naming.ServiceUnavailableException;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.security.InvalidParameterException;
import java.util.*;

public class Client {

    private String token;
    private final CoordinatorInt coordinator;
    private final Scanner scanner;
    private final String userUploadPath = "storage/upload/";
    private final String userDownloadPath = "storage/downloads/";
    private boolean isManager;

    public Client(CoordinatorInt coordinator) {
        this.coordinator = coordinator;
        scanner = new Scanner(System.in);
    }

    private void run() throws RemoteException {

        boolean working = true;
        while (working) {
            //Not logged in
            if (!isLoggedIn()){
                try {
                    this.token = this.login();
                    this.isManager = coordinator.isManager(token);
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

                    System.out.println("*Action concluded successfully*\n--------------------------------------\n");
                }
                catch (InvalidParameterException | IllegalAccessException exception){
                    System.out.println(exception.getMessage());
                }
                catch (IllegalStateException exception){
                    System.out.println("Application is terminated");
                    working = false;
                }
            }
        }
    }

    private boolean isLoggedIn() {
        try {
            coordinator.isValidToken(token);
            return true;
        } catch (Exception e) {
            return false;
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
        int choice = 0;
        try {
            choice = this.getUserChoice(departments);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        //decrement "choice" by 1 because the index should start from 0.
        return departments.get(choice-1);
    }

    private int chooseAction(String department) throws RemoteException, InvalidParameterException, IllegalStateException, IllegalAccessException {

        List<String> actions = this.getAllowedActions(department);
        System.out.println("Available actions: ");

        try {
            return this.getUserChoice(actions);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
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
        if (isManager){
            actions.add("Add an employee");
        }
        return actions;
    }

    private void executeRequestedAction(int action, String department) throws RemoteException, InvalidParameterException, IllegalStateException, IllegalAccessException {

        switch (action) {
            case 1:
                this.downloadFile(department);
                break;
            case 2:
                this.uploadFile(department);
                break;
            case 3:
                this.updateFile(department);
                break;
            case 4:
                this.deleteFile(department);
                break;
            case 5:
                this.addEmployee(department);
                break;
            default:
                throw new IllegalArgumentException("Invalid action, something went wrong");
        }
    }

    private void downloadFile(String department) throws RemoteException, InvalidParameterException, IllegalStateException, IllegalAccessException {

        String fileName = getFilenameFromUserChoice(
                    this.getDepartmentFiles(department),
                    "Choose a file to download: ");

        try (ServerSocket socket = new ServerSocket(8000);
        ) {
            int port = socket.getLocalPort();

            coordinator.fileGet(token, "localhost", port, fileName, department);

            try (Socket nodeConnection = socket.accept();
                 InputStream fileStream = nodeConnection.getInputStream();
                 FileOutputStream fileOut = new FileOutputStream(userDownloadPath + fileName)){

                fileStream.transferTo(fileOut);
            }


        } catch (IOException | ServiceUnavailableException e) {
            throw new RuntimeException(e);
        }
    }

    private void uploadFile(String department) throws InvalidParameterException, IllegalStateException, IllegalAccessException {

        List<String> fileNames = null;
        try {
            fileNames = getFilesFromUploadDirectory();
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        String fileName = null;
        try {
            fileName = getFilenameFromUserChoice(
                    fileNames,
                    "Choose the file that you want to upload to the cloud: \n" +
                            "(put the file in " + userUploadPath + " dir to appear here):");
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        try (ServerSocket socket = new ServerSocket(8000)) {
            int port = socket.getLocalPort();

            String fullName = getFullName(department, fileName);

            coordinator.fileCreate(token, "localhost", port, fullName);

            try (Socket nodeConnection = socket.accept();
                 OutputStream nodeOut = nodeConnection.getOutputStream();
                 FileInputStream fileIn = new FileInputStream(userUploadPath + fileName)) {

                fileIn.transferTo(nodeOut);
            }

        } catch (IOException | ServiceUnavailableException e) {
            throw new RuntimeException(e);
        }
    }

    private void updateFile(String department) throws RemoteException, InvalidParameterException, IllegalStateException, IllegalAccessException {

        String originalFile = null;
        try {
            originalFile = getFilenameFromUserChoice(
                    getDepartmentFiles(department),
                    "Choose the original file that you want to update from cloud: "
            );
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        String updatedFile = null;
        try {
            updatedFile = getFilenameFromUserChoice(
                    getFilesFromUploadDirectory(),
                    "Choose the updated file that you want to upload to cloud: \n" +
                            "P.S. the updated fileName doesn't matter, original fileName will persist"
            );
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        try (ServerSocket socket = new ServerSocket(8000)) {
            int port = socket.getLocalPort();

            String fullName = getFullName(department, originalFile);

            coordinator.fileUpdate(token, "localhost", port, fullName);

            try (Socket nodeConnection = socket.accept();
                 OutputStream nodeOut = nodeConnection.getOutputStream();
                 FileInputStream fileIn = new FileInputStream(userUploadPath + updatedFile)) {

                fileIn.transferTo(nodeOut);
            }

        } catch (IOException | ServiceUnavailableException e) {
            throw new RuntimeException(e);
        }
    }

    private void deleteFile(String department) throws RemoteException, InvalidParameterException, IllegalStateException, IllegalAccessException {

        String fileName = null;
        try {
            fileName = getFilenameFromUserChoice(getDepartmentFiles(department),
                    "Choose the file that you want to delete from cloud: ");
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        String fullName = getFullName(department, fileName);

        try {
            coordinator.fileDelete(token, fullName);
        } catch (ServiceUnavailableException e) {
            throw new RuntimeException(e);
        }
    }

    private void addEmployee(String department) throws RemoteException {
        System.out.print("Employee username: ");
        String username = scanner.nextLine();
        System.out.print("Employee password: ");
        String password = scanner.nextLine();

        if (username == null || password == null){ throw new IllegalArgumentException("Invalid username or password"); }

        coordinator.addEmployee(token, username, password, List.of(department));
    }

    private List<String> getDepartmentFiles(String department) throws RemoteException {
        return this.coordinator.getDepartmentFiles(token, department);
    }

    private int getUserChoice(List<String> options) throws IllegalStateException, IllegalAccessException {

        if (options.isEmpty()){throw new IllegalAccessException("Nothing to display.\n");}

        int i = 1;
        for(String option : options){
            System.out.println(i++ + " - " + option + ".");
        }
        System.out.println("""
                0 - Exit Application\s
                 ---------------------------
                Enter the number of the choice you want:\s""");

        int choice;
        while (true) {
            choice = scanner.nextInt();
            if(choice >= 0 && choice <= options.size()) break;
            System.out.println("Please choose a valid choice number, or terminate the app: ");
        }

        if (choice == 0) {throw new IllegalStateException();}

        return choice;
    }

    private String getFilenameFromUserChoice(List<String> fileNames, String header) throws IllegalAccessException {

        System.out.println(header);
        System.out.println("----------------------------");

        int choice = this.getUserChoice(fileNames);

        return fileNames.get(choice - 1);
    }

    private List<String> getFilesFromUploadDirectory() throws IllegalAccessException {
        File directory = new File(this.userUploadPath);
        return Arrays.asList(Objects.requireNonNull(directory.list()));
    }

    private static String getFullName(String department, String fileName) {
        return department + "/" + fileName;
    }

    public static void main(String[] args) throws Exception {

        CoordinatorInt coordinator = (CoordinatorInt) Naming.lookup("rmi://localhost:5000/coordinator");
        Client client = new Client(coordinator);
        File uploadDir = new File(client.userUploadPath);
        File downloadDir = new File(client.userDownloadPath);
        uploadDir.mkdirs();
        downloadDir.mkdirs();
        client.run();
    }
}
