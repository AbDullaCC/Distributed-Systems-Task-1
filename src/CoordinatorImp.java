import javax.naming.ServiceUnavailableException;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.security.InvalidParameterException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CoordinatorImp extends UnicastRemoteObject implements CoordinatorInt {

    static final HashMap<String, Integer> load = new HashMap<>();
    static final HashMap<String, NodeInt> nodes = new HashMap<>();
    private final List<String> departments;
    private final HashMap<String, Employee> employees;
    private final HashMap<String, FileMeta> filesMeta;
    private final HashMap<String, Employee> tokens;
    private final HashMap<String, Character> filesStatus; // 'R' or 'W'

    protected CoordinatorImp() throws RemoteException {
        super();
        departments = Arrays.asList("IT", "HR", "QA", "GRAPHICS", "SALES");
        employees = new HashMap<>();
        tokens = new HashMap<>();
        filesMeta = new HashMap<>();
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

    public static NodeInt getBestNode(List<String> availableNodes) throws ServiceUnavailableException {
        String bestNode = null;
        for (Map.Entry<String, Integer> a : load.entrySet()) {
            if (availableNodes.contains(a.getKey())) {
                bestNode = a.getKey();
                break;
            }
        }
        if (bestNode == null) throw new ServiceUnavailableException("No nodes available");
        return nodes.get(bestNode);
    }

    public static void increaseLoad(NodeInt node) throws RemoteException {
        String nodeId = node.getNodeId();
        load.put(nodeId, load.get(nodeId) + 1);
    }

    public static void decreaseLoad(NodeInt node) throws RemoteException {
        String nodeId = node.getNodeId();
        load.put(nodeId, load.get(nodeId) - 1);
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

    public void checkRWAccess(String fullName) throws ServiceUnavailableException {
        if (filesStatus.get(fullName) != null) {
            throw new ServiceUnavailableException("Someone is " + (filesStatus.get(fullName) == 'R' ? "reading" : "writing"));
        }
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
    public boolean fileCreate(String token, String group, int port, String fullName) throws RemoteException, ServiceUnavailableException {
        otherActionsAllowed(token, fullName.split(",")[0]);
        checkRWAccess(fullName);
        // exists and not deleted
        if (filesMeta.containsKey(fullName) && !filesMeta.get(fullName).getNodes().isEmpty())
            throw new IllegalArgumentException();
        CreateThread th = new CreateThread(group, port, fullName, nodes.keySet().stream().toList());
        th.start();
        return true;
    }

    @Override
    public boolean fileGet(String token, String group, int port, String name, String dep) throws RemoteException, ServiceUnavailableException {
        String fullName = dep + "/" + name;
        isValidToken(token);
        checkRWAccess(fullName);
        // don't exist or exists but deleted
        if (!filesMeta.containsKey(fullName) || (filesMeta.containsKey(fullName) && filesMeta.get(fullName).getNodes().isEmpty()))
            throw new IllegalArgumentException();
        GetThread th = new GetThread(group, port, fullName, nodes.keySet().stream().toList());
        th.start();
        return true;
    }

    @Override
    public boolean fileUpdate(String token, String group, int port, String fullName) throws RemoteException, ServiceUnavailableException {
        otherActionsAllowed(token, fullName.split(",")[0]);
        checkRWAccess(fullName);
        // don't exist or exists but deleted
        if (!filesMeta.containsKey(fullName) || (filesMeta.containsKey(fullName) && filesMeta.get(fullName).getNodes().isEmpty()))
            throw new IllegalArgumentException();
        UpdateThread th = new UpdateThread(group, port, fullName, nodes.keySet().stream().toList());
        th.start();
        return true;
    }

    @Override
    public boolean fileDelete(String token, String group, int port, String fullName) throws RemoteException, ServiceUnavailableException {
        otherActionsAllowed(token, fullName.split(",")[0]);
        checkRWAccess(fullName);
        // don't exist or exists but deleted
        if (!filesMeta.containsKey(fullName) || (filesMeta.containsKey(fullName) && filesMeta.get(fullName).getNodes().isEmpty()))
            throw new IllegalArgumentException();
        DeleteThread th = new DeleteThread(group, port, fullName, nodes.keySet().stream().toList());
        th.start();
        return true;
    }

    private boolean nodesSync() {
        return false;
    }

}

class CreateThread extends Thread {
    String group;
    int port;
    String fullName;
    List<String> nodes;

    public CreateThread(String group, int port, String fullName, List<String> nodes) {
        this.group = group;
        this.port = port;
        this.fullName = fullName;
        this.nodes = nodes;
    }

    @Override
    public void run() {
        NodeInt node = null;
        try {
            node = CoordinatorImp.getBestNode(nodes);
            CoordinatorImp.increaseLoad(node);
            node.getFile(group, port, fullName);
            CoordinatorImp.decreaseLoad(node);
        } catch (ServiceUnavailableException | RemoteException e) {
            throw new RuntimeException(e);
        }
    }
}


class GetThread extends Thread {
    String group;
    int port;
    String fullName;
    List<String> nodes;

    public GetThread(String group, int port, String fullName, List<String> nodes) {
        this.group = group;
        this.port = port;
        this.fullName = fullName;
        this.nodes = nodes;
    }

    @Override
    public void run() {
        NodeInt node = null;
        try {
            node = CoordinatorImp.getBestNode(nodes);
            CoordinatorImp.increaseLoad(node);
            node.getFile(group, port, fullName);
            CoordinatorImp.decreaseLoad(node);
        } catch (ServiceUnavailableException | RemoteException e) {
            throw new RuntimeException(e);
        }
    }
}


class UpdateThread extends Thread {
    String group;
    int port;
    String fullName;
    List<String> nodes;

    public UpdateThread(String group, int port, String fullName, List<String> nodes) {
        this.group = group;
        this.port = port;
        this.fullName = fullName;
        this.nodes = nodes;
    }

    @Override
    public void run() {
        NodeInt node = null;
        try {
            node = CoordinatorImp.getBestNode(nodes);
            CoordinatorImp.increaseLoad(node);
            node.updateFile(group, port, fullName);
            CoordinatorImp.decreaseLoad(node);
        } catch (ServiceUnavailableException | RemoteException e) {
            throw new RuntimeException(e);
        }
    }
}


class DeleteThread extends Thread {
    String group;
    int port;
    String fullName;
    List<String> nodes;

    public DeleteThread(String group, int port, String fullName, List<String> nodes) {
        this.group = group;
        this.port = port;
        this.fullName = fullName;
        this.nodes = nodes;
    }

    @Override
    public void run() {
        NodeInt node = null;
        try {
            node = CoordinatorImp.getBestNode(nodes);
            CoordinatorImp.increaseLoad(node);
            node.deleteFile(group, port, fullName);
            CoordinatorImp.decreaseLoad(node);
        } catch (ServiceUnavailableException | RemoteException e) {
            throw new RuntimeException(e);
        }
    }
}
