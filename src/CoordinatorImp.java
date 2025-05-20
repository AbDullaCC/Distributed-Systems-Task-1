import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.security.InvalidParameterException;
import java.util.*;

public class CoordinatorImp extends UnicastRemoteObject implements CoordinatorInt {

    private static final List<String> departments = new ArrayList<>();

    private static HashMap<String, Employee> employees;

    private static HashMap<String, Employee> tokens;

    private static HashMap<String, FileMeta> filesMeta;

    private static HashMap<String, NodeInt> nodes;

    private static HashSet<Map.Entry<String, String>> load;

    protected CoordinatorImp() throws RemoteException {
        super();
        departments.addAll(Arrays.asList("IT", "HR", "QA", "GRAPHICS", "SALES"));
    }

    public static void main(String[] args) {
        try {
            CoordinatorImp coordinator = new CoordinatorImp();
            LocateRegistry.createRegistry(5000);

            Naming.bind("rmi://localhost:5000" + "/coordinator", coordinator);
            System.out.println("coordinator is running");
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    @Override
    public String login(String username, String password) throws RemoteException, InvalidParameterException {
        return "Some Token";
    }

    @Override
    public boolean otherActionsAllowed(String token, String department) throws RemoteException {
        return true;
    }

    @Override
    public List<String> getDepartments(String token) throws RemoteException, InvalidParameterException {
        this.checkTokenValidity(token);
        return departments;
    }

    @Override
    public List<String> getDepartmentFiles(String token, String department) throws RemoteException, InvalidParameterException {
        this.checkTokenValidity(token);
        return List.of("File_1", "File_2", "File_3", "File_4", "File_5");
    }

    private void checkTokenValidity(String token) throws RemoteException, InvalidParameterException {
        if (!isValidToken(token)) {
            throw new InvalidParameterException("Invalid Token");
        }
    }

    @Override
    public boolean isValidToken(String token) throws RemoteException {
        return token != null;
    }

    @Override
    public boolean createFile(String token, byte[] content, String fullName) throws RemoteException {
        return false;
    }

    @Override
    public byte[] getFile(String token, String name, String dep) throws RemoteException {
        return new byte[0];
    }

    @Override
    public boolean updateFile(String token, byte[] content, String fullName) throws RemoteException {
        return false;
    }

    @Override
    public boolean deleteFile(String token, String name, String dep) throws RemoteException {
        return false;
    }

    private String addEmployee(String username, String password, List<String> roles) {
        return "";
    }

    private boolean checkPassword(Employee employee, String password) {
        return false;
    }

    private String generateToken(Employee employee) {
        return "";
    }

    private boolean nodesSync() {
        return false;
    }

    private NodeInt getBestNode(List<String> nodes) {
        return null;
    }


}
