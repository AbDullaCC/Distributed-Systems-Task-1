import javax.naming.ServiceUnavailableException;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.security.InvalidParameterException;
import java.util.*;

public class CoordinatorImp extends UnicastRemoteObject implements CoordinatorInt {

    private final List<String> departments;
    private final HashMap<String, Employee> employees;
    private final HashMap<String, FileMeta> filesMeta;
    private final HashMap<String, NodeInt> nodes;
    private final HashSet<Map.Entry<String, String>> load;
    private final HashMap<String, Employee> tokens;

    private final HashMap<String, Character> filesStatus; // 'R' or 'W'


    protected CoordinatorImp() throws RemoteException {
        super();
        departments = Arrays.asList("IT", "HR", "QA", "GRAPHICS", "SALES");
        employees = new HashMap<>();
        tokens = new HashMap<>();
        filesMeta = new HashMap<>();
        nodes = new HashMap<>();
        load = new HashSet<>();
        filesStatus = new HashMap<>();
    }

    public static void main(String[] args) {
        try {
            CoordinatorImp coordinator = new CoordinatorImp();
            LocateRegistry.createRegistry(5000);

            Naming.bind("rmi://localhost:5000/coordinator", coordinator);
            System.out.println("coordinator is running");
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    private boolean addEmployee(String username, String password, List<String> roles) {
        Employee existsEmployee = employees.get(username);
        if (existsEmployee != null) {
            throw new InvalidParameterException("Username exists");
        }
        Employee employee = new Employee(username, password, roles);
        employees.put(username, employee);
        return true;
    }

    @Override
    public String login(String username, String password) throws RemoteException, InvalidParameterException {
        Employee employee = employees.get(username);
        if (employee == null) {
            throw new InvalidParameterException("Username doesn't exist");
        }
        if (!employee.passwordAttempt(password)) {
            throw new InvalidParameterException("Password is wrong");
        }
        return generateToken(employee);
    }

    private String generateToken(Employee employee) {
        String token = TokenGenerator.generateToken(employee.getUsername());
        tokens.put(token, employee);
        return token;
    }

    @Override
    public boolean isValidToken(String token) throws RemoteException, InvalidParameterException {
        if (!TokenGenerator.isValidToken(token)) throw new InvalidParameterException("Invalid token");
        return true;
    }

    @Override
    public boolean otherActionsAllowed(String token, String department) throws RemoteException {
        isValidToken(token);
        Employee employee = tokens.get(token);
        if (employee == null) {
            throw new InvalidParameterException("employee doesn't exist");
        }
        return employee.getRoles().contains(department);
    }

    @Override
    public List<String> getDepartments(String token) throws RemoteException, InvalidParameterException {
        isValidToken(token);
        return departments;
    }

    @Override
    public List<String> getDepartmentFiles(String token, String department) throws RemoteException, InvalidParameterException {
        isValidToken(token);
        return filesMeta.values().stream().filter(fileMeta -> fileMeta.dep.equals(department)).map(fileMeta -> fileMeta.name).toList();
    }

    @Override
    public boolean fileCreateToken(String token, byte[] content, String fullName) throws RemoteException {
        otherActionsAllowed(token, fullName.split(",")[0]);
        return false;
    }

    @Override
    public byte[] fileGetTicket(String token, String name, String dep) throws RemoteException {
        return null;
    }

    @Override
    public boolean fileUpdateTicket(String token, byte[] content, String fullName) throws RemoteException {
        otherActionsAllowed(token, fullName.split(",")[0]);
        return false;
    }

    @Override
    public boolean fileDeleteTicket(String token, String name, String dep) throws RemoteException {
        otherActionsAllowed(token, dep);
        return false;
    }

    private boolean nodesSync() {
        return false;
    }

    private NodeInt getBestNode(List<String> availableNodes) throws ServiceUnavailableException {
        String bestNode = null;
        for (Map.Entry<String, String> n : load) {
            if (availableNodes.contains(availableNodes.getLast())) {
                bestNode = availableNodes.getLast();
            }
        }
        if (bestNode == null) throw new ServiceUnavailableException("No nodes available");
        return nodes.get(bestNode);
    }

}
