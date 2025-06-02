import javax.naming.ServiceUnavailableException;
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.security.InvalidParameterException;
import java.util.*;
import java.util.stream.Collectors;

public class CoordinatorImp extends UnicastRemoteObject implements CoordinatorInt {

    static final HashMap<String, Integer> load = new HashMap<>();
    static final HashMap<String, NodeInt> nodes = new HashMap<>();
    static final HashMap<String, FileMeta> filesMeta = new HashMap<>();
    static final HashMap<String, Boolean> activeNodes = new HashMap<>();
    private static final HashMap<String, Character> filesStatus = new HashMap<>(); // 'R' or 'W'
    private static final long PING_INTERVAL_MS = 30 * 1000; // Ping every 30 seconds
    private static Timer timer = new Timer();
    private final List<String> departments;
    private final HashMap<String, Employee> employees;
    private final HashMap<String, Employee> tokens;

    protected CoordinatorImp() throws RemoteException {
        super();
        departments = Arrays.asList("IT", "HR", "QA", "GRAPHICS", "SALES");
        employees = new HashMap<>();
        tokens = new HashMap<>();
    }

    public static void main(String[] args) {
        try {
            CoordinatorImp coordinator = new CoordinatorImp();
            Employee manager = new Employee("man", "123", List.of("MANAGER"));
            coordinator.employees.put("man", manager);

            LocateRegistry.createRegistry(5000);
            Naming.rebind("rmi://localhost:5000/coordinator", coordinator);

            System.out.println("coordinator is running");

            coordinator.scheduleNodeSync();

            coordinator.schedulePeriodicPing();
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    public static List<String> getBestNode(List<String> availableNodes) throws ServiceUnavailableException {
        if (availableNodes.isEmpty()) {
            throw new ServiceUnavailableException("No nodes available");
        }

        // Create list of node IDs and their loads
        List<Map.Entry<String, Integer>> nodeLoads = new ArrayList<>();
        for (String nodeId : availableNodes) {
            if (load.containsKey(nodeId)) {
                nodeLoads.add(new AbstractMap.SimpleEntry<>(nodeId, load.get(nodeId)));
            }
        }

        // Sort by load (ascending)
        nodeLoads.sort(Map.Entry.comparingByValue());

        // Extract just the node IDs in sorted order
        List<String> sortedNodes = nodeLoads.stream().map(Map.Entry::getKey).collect(Collectors.toList());

        if (sortedNodes.isEmpty()) {
            throw new ServiceUnavailableException("No nodes available");
        }

        return sortedNodes;
    }

    public synchronized static void increaseLoad(NodeInt node) throws RemoteException {
        synchronized (load) {
            String nodeId = node.getNodeId();
            load.put(nodeId, load.get(nodeId) + 1);
        }
    }

    public synchronized static void decreaseLoad(NodeInt node) throws RemoteException {
        synchronized (load) {
            String nodeId = node.getNodeId();
            load.put(nodeId, load.get(nodeId) - 1);
        }
    }

    public synchronized static void makeRead(String fullName) throws RemoteException {
        synchronized (filesStatus) {
            filesStatus.put(fullName, 'R');
        }
    }

    public synchronized static void makeWrite(String fullName) throws RemoteException {
        synchronized (load) {
            filesStatus.put(fullName, 'W');
        }
    }

    public synchronized static void removeStatus(String fullName) throws RemoteException {
        synchronized (load) {
            filesStatus.remove(fullName);
        }
    }

    public synchronized static void deleteFile(String fullName) throws RemoteException {
        synchronized (filesMeta) {
            filesMeta.get(fullName).clearNodes();
        }
    }

    private void schedulePeriodicPing() {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                checkNodeStatus();
            }
        }, PING_INTERVAL_MS, PING_INTERVAL_MS); // Start after initial delay, then repeat
        System.out.println("Coordinator: Periodic node pinging scheduled every " + PING_INTERVAL_MS / 1000 + " seconds.");
    }

    private void checkNodeStatus() {
        System.out.println("Coordinator: Performing periodic node health check...");
        if (nodes.isEmpty()) {
            System.out.println("Coordinator: No nodes currently registered to ping.");
            return;
        }
        Set<String> currentNodeIds = new HashSet<>(nodes.keySet());

        for (String nodeId : currentNodeIds) {
            NodeInt node = nodes.get(nodeId);
            if (node != null) {
                try {
                    boolean isAlive = node.ping();
                    if (isAlive) {
                        activeNodes.put(nodeId, true); // Mark as active
                    } else {
                        // This case might not happen if ping() throws RemoteException on failure
                        System.out.println("Coordinator: Node " + nodeId + " ping returned false (unexpected). Marking as inactive.");
                        handleInactiveNode(nodeId);
                    }
                } catch (RemoteException e) {
                    System.err.println("Coordinator: Node " + nodeId + " failed to respond to ping. Marking as inactive. Error: " + e.getMessage());
                    handleInactiveNode(nodeId);
                }
            }
        }
        System.out.println("Coordinator: Node health check finished. Active nodes: " + activeNodes.keySet());
    }

    private void handleInactiveNode(String nodeId) {
        activeNodes.remove(nodeId);
        load.remove(nodeId);
        nodes.remove(nodeId);
    }

    private void scheduleNodeSync() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 14);
        calendar.set(Calendar.MINUTE, 46);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        Date firstRun = calendar.getTime();
        if (firstRun.before(new Date())) {
            calendar.add(Calendar.DAY_OF_MONTH, 1);
            firstRun = calendar.getTime();
        }

        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    System.out.println("node sync....");
                    nodesSync();

                    scheduleNodeSync();

                    timer.cancel();
                    timer = new Timer();
                } catch (RemoteException e) {
                    System.err.println("Error during node synchronization: " + e.getMessage());
                }
            }
        }, firstRun);
    }

    @Override
    public void addNode(String id) throws RemoteException, MalformedURLException, NotBoundException {
        NodeInt node1 = (NodeInt) Naming.lookup("rmi://localhost:5000/" + id);
        CoordinatorImp.nodes.put(id, node1);
        activeNodes.put(id, true);
        CoordinatorImp.load.put(id, 0);
    }

    public boolean addEmployee(String token, String username, String password, List<String> roles) throws RemoteException {
        isValidToken(token);
        if (!isManager(token))
            throw new InvalidParameterException("Forbidden operation, you should be a manager to add new employees");
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

    @Override
    public boolean isManager(String token) throws RemoteException {
        return tokens.get(token).getRoles().contains("MANAGER");
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
        return employee.getRoles().contains(department) || employee.getRoles().contains("MANAGER");
    }

    @Override
    public List<String> getDepartments(String token) throws RemoteException, InvalidParameterException {
        isValidToken(token);
        return departments;
    }

    @Override
    public List<String> getDepartmentFiles(String token, String department) throws RemoteException, InvalidParameterException {
        isValidToken(token);
        return filesMeta.values().stream().filter(fileMeta -> fileMeta.dep.equals(department) && !fileMeta.getNodes().isEmpty()).map(fileMeta -> fileMeta.name).toList();
    }

    @Override
    public boolean fileCreate(String token, String ip, int port, String fullName) throws RemoteException, ServiceUnavailableException {
        otherActionsAllowed(token, fullName.split("/")[0]);
        checkRWAccess(fullName);
        // exists and not deleted
        if (filesMeta.containsKey(fullName) && !filesMeta.get(fullName).getNodes().isEmpty())
            throw new IllegalArgumentException();
        CreateThread th = new CreateThread(ip, port, fullName, nodes.keySet().stream().toList());
        th.start();
        return true;
    }

    @Override
    public boolean fileGet(String token, String ip, int port, String name, String dep) throws RemoteException, ServiceUnavailableException {
        String fullName = dep + "/" + name;
        isValidToken(token);
        checkRWAccess(fullName);
        // don't exist or exists but deleted
        if (!filesMeta.containsKey(fullName) || (filesMeta.containsKey(fullName) && filesMeta.get(fullName).getNodes().isEmpty()))
            throw new IllegalArgumentException();
        GetThread th = new GetThread(ip, port, fullName, nodes.keySet().stream().toList());
        th.start();
        return true;
    }

    @Override
    public boolean fileUpdate(String token, String ip, int port, String fullName) throws RemoteException, ServiceUnavailableException {
        otherActionsAllowed(token, fullName.split("/")[0]);
        checkRWAccess(fullName);
        // don't exist or exists but deleted
        if (!filesMeta.containsKey(fullName) || (filesMeta.containsKey(fullName) && filesMeta.get(fullName).getNodes().isEmpty()))
            throw new IllegalArgumentException();
        UpdateThread th = new UpdateThread(ip, port, fullName, nodes.keySet().stream().toList());
        th.start();
        return true;
    }

    @Override
    public boolean fileDelete(String token, String fullName) throws RemoteException, ServiceUnavailableException {
        otherActionsAllowed(token, fullName.split("/")[0]);
        checkRWAccess(fullName);
        // don't exist or exists but deleted
        if (!filesMeta.containsKey(fullName) || (filesMeta.containsKey(fullName) && filesMeta.get(fullName).getNodes().isEmpty()))
            throw new IllegalArgumentException();
        DeleteThread th = new DeleteThread(fullName, nodes.keySet().stream().toList());
        th.start();
        return true;
    }

    private boolean nodesSync() throws RemoteException {
        for (var asd : filesMeta.entrySet()) {
            FileMeta fileMeta = asd.getValue();
            if (fileMeta.getNodes().isEmpty()) {
                for (var entry : nodes.entrySet()) {
                    entry.getValue().syncDeleteFile(fileMeta.getFullName());
                }
            } else if (fileMeta.getNodes().size() < nodes.size()) {
                SyncThread th = new SyncThread(fileMeta.getFullName(), fileMeta.getNodes());
                th.start();
            }
        }
        return false;
    }
@Override
    public boolean userSync(String token) throws RemoteException {
        isValidToken(token);
        if (!isManager(token))
            throw new InvalidParameterException("Forbidden operation, you should be a manager to add new employees");

        return nodesSync();
    }

}

class CreateThread extends Thread {
    String ip;
    int port;
    String fullName;
    List<String> nodes;

    public CreateThread(String ip, int port, String fullName, List<String> nodes) {
        this.ip = ip;
        this.port = port;
        this.fullName = fullName;
        this.nodes = nodes;
    }

    @Override
    public void run() {
        try {
            List<String> sortedNodes = CoordinatorImp.getBestNode(nodes);
            for (String nodeId : sortedNodes) {
                NodeInt node = CoordinatorImp.nodes.get(nodeId);
                try {
                    CoordinatorImp.increaseLoad(node);
                    CoordinatorImp.makeWrite(fullName);
                    node.createFile(ip, port, fullName);
                    CoordinatorImp.decreaseLoad(node);
                    CoordinatorImp.removeStatus(fullName);

                    FileMeta fm = new FileMeta(fullName);
                    fm.addNode(node.getNodeId());
                    CoordinatorImp.filesMeta.put(fullName, fm);

                    break; // Operation succeeded, exit loop
                } catch (RemoteException e) {
                    // If this node fails, try the next one
                    System.err.println("Node " + nodeId + " failed to create file: " + e.getMessage());
                    CoordinatorImp.decreaseLoad(node);
                    CoordinatorImp.removeStatus(fullName);
                }
            }
        } catch (ServiceUnavailableException e) {
            throw new RuntimeException("No nodes available", e);
        } catch (RemoteException e) {
            throw new RuntimeException("Unexpected remote error", e);
        }
    }
}

class GetThread extends Thread {
    String ip;
    int port;
    String fullName;
    List<String> nodes;

    public GetThread(String ip, int port, String fullName, List<String> nodes) {
        this.ip = ip;
        this.port = port;
        this.fullName = fullName;
        this.nodes = nodes;
    }

    @Override
    public void run() {
        try {
            List<String> sortedNodes = CoordinatorImp.getBestNode(nodes);
            for (String nodeId : sortedNodes) {
                NodeInt node = CoordinatorImp.nodes.get(nodeId);
                try {
                    CoordinatorImp.increaseLoad(node);
                    CoordinatorImp.makeRead(fullName);
                    node.getFile(ip, port, fullName);
                    CoordinatorImp.decreaseLoad(node);
                    CoordinatorImp.removeStatus(fullName);
                    break; // Operation succeeded, exit loop
                } catch (RemoteException e) {
                    // If this node fails, try the next one
                    System.err.println("Node " + nodeId + " failed to get file: " + e.getMessage());
                    CoordinatorImp.decreaseLoad(node);
                    CoordinatorImp.removeStatus(fullName);
                }
            }
        } catch (ServiceUnavailableException e) {
            throw new RuntimeException("No nodes available", e);
        } catch (RemoteException e) {
            throw new RuntimeException("Unexpected remote error", e);
        }
    }
}

class UpdateThread extends Thread {
    String ip;
    int port;
    String fullName;
    List<String> nodes;

    public UpdateThread(String ip, int port, String fullName, List<String> nodes) {
        this.ip = ip;
        this.port = port;
        this.fullName = fullName;
        this.nodes = nodes;
    }

    @Override
    public void run() {
        try {
            List<String> sortedNodes = CoordinatorImp.getBestNode(nodes);
            for (String nodeId : sortedNodes) {
                NodeInt node = CoordinatorImp.nodes.get(nodeId);
                try {
                    CoordinatorImp.increaseLoad(node);
                    CoordinatorImp.makeWrite(fullName);
                    node.updateFile(ip, port, fullName);
                    CoordinatorImp.decreaseLoad(node);
                    CoordinatorImp.removeStatus(fullName);
                    break; // Operation succeeded, exit loop
                } catch (RemoteException e) {
                    // If this node fails, try the next one
                    System.err.println("Node " + nodeId + " failed to update file: " + e.getMessage());
                    CoordinatorImp.decreaseLoad(node);
                    CoordinatorImp.removeStatus(fullName);
                }
            }
        } catch (ServiceUnavailableException e) {
            throw new RuntimeException("No nodes available", e);
        } catch (RemoteException e) {
            throw new RuntimeException("Unexpected remote error", e);
        }
    }
}

class DeleteThread extends Thread {
    String ip;
    int port;
    String fullName;
    List<String> nodes;

    public DeleteThread(String fullName, List<String> nodes) {
        this.fullName = fullName;
        this.nodes = nodes;
    }

    @Override
    public void run() {
        try {
            List<String> sortedNodes = CoordinatorImp.getBestNode(nodes);
            for (String nodeId : sortedNodes) {
                NodeInt node = CoordinatorImp.nodes.get(nodeId);
                try {
                    CoordinatorImp.increaseLoad(node);
                    CoordinatorImp.makeWrite(fullName);
                    node.deleteFile(fullName);
                    CoordinatorImp.decreaseLoad(node);
                    CoordinatorImp.removeStatus(fullName);
                    CoordinatorImp.deleteFile(fullName);
                    break; // Operation succeeded, exit loop
                } catch (RemoteException e) {
                    // If this node fails, try the next one
                    System.err.println("Node " + nodeId + " failed to delete file: " + e.getMessage());
                    CoordinatorImp.decreaseLoad(node);
                    CoordinatorImp.removeStatus(fullName);
                }
            }
        } catch (ServiceUnavailableException e) {
            throw new RuntimeException("No nodes available", e);
        } catch (RemoteException e) {
            throw new RuntimeException("Unexpected remote error", e);
        }
    }
}

class SyncThread extends Thread {
    String fullName;
    List<String> nodes;

    public SyncThread(String fullName, List<String> nodes) {
        this.fullName = fullName;
        this.nodes = nodes;
    }

    @Override
    public void run() {
        try {
            List<String> sortedNodes = CoordinatorImp.getBestNode(nodes);
            for (String nodeId : sortedNodes) {
                NodeInt node = CoordinatorImp.nodes.get(nodeId);
                try {
                    CoordinatorImp.increaseLoad(node);
                    node.syncFile(fullName);
                    CoordinatorImp.decreaseLoad(node);
                    break; // Operation succeeded, exit loop
                } catch (RemoteException e) {
                    // If this node fails, try the next one
                    System.err.println("Node " + nodeId + " failed to sync file: " + e.getMessage());
                    CoordinatorImp.decreaseLoad(node);
                }
            }
        } catch (ServiceUnavailableException e) {
            throw new RuntimeException("No nodes available", e);
        } catch (RemoteException e) {
            throw new RuntimeException("Unexpected remote error", e);
        }
    }
}