import javax.naming.ServiceUnavailableException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.security.InvalidParameterException;
import java.util.*;

public class CoordinatorImp extends UnicastRemoteObject implements CoordinatorInt {

    static final HashMap<String, Integer> load = new HashMap<>();
    static final HashMap<String, NodeInt> nodes = new HashMap<>();
    static final HashMap<String, FileMeta> filesMeta = new HashMap<>();
    private static final HashMap<String, Character> filesStatus = new HashMap<>(); // 'R' or 'W'
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
        } catch (Exception e) {
            System.out.println(e);
        }
    }

//public static void main(String[] args) {
//    String rmiRegistryHost = "localhost";
//    String rmiRegistryPort = "5000"; // Ensure this matches NodeImp's setup
//
//    // To store NodeImp instances for creating dummy files via getStorageBasePath()
//    // This requires NodeImp to have a getStorageBasePath() method.
//    // And NodeImp class needs to be accessible here.
//    Map<String, NodeImp> localNodeInstances = new HashMap<>();
//
//    try {
//        // 1. Start RMI Registry
//        try {
//            LocateRegistry.createRegistry(Integer.parseInt(rmiRegistryPort));
//            System.out.println("Coordinator.main: RMI registry created on port " + rmiRegistryPort + ".");
//        } catch (RemoteException e) {
//            System.out.println("Coordinator.main: RMI registry may already be running on port " + rmiRegistryPort + ".");
//        }
//        System.setProperty("java.rmi.server.hostname", rmiRegistryHost);
//
//            CoordinatorImp coordinator = new CoordinatorImp();
//            coordinator.addEmployee("asd", "asd", List.of("IT"));
//
//        String coordinatorBindingName = "coordinator";
//        Naming.rebind("rmi://" + rmiRegistryHost + ":" + rmiRegistryPort + "/" + coordinatorBindingName, coordinator);
//        System.out.println("Coordinator.main: Coordinator is running and bound as '" + coordinatorBindingName + "'.");
//
//        // 3. Start Multiple NodeImp Instances
//        // IMPORTANT: This assumes NodeImp.startInstance(nodeId) correctly starts a NodeImp,
//        // binds it to RMI, and that NodeImp's constructor starts its multicast listener.
//        // If NodeImp.startInstance is not available or suitable, you'd create NodeImp objects directly here.
//        String[] nodeIds = {"nodeX", "nodeY", "nodeZ"};
//        for (String nodeId : nodeIds) {
//            try {
//                // Assuming NodeImp has a main that calls startInstance, or you have a launcher.
//                // For a self-contained test in Coordinator's main, we create NodeImp instances directly:
//                NodeImp node = new NodeImp(nodeId); // NodeImp constructor should start its listener
//                localNodeInstances.put(nodeId, node); // Store for local file creation
//
//                // Manually bind the node to RMI for the coordinator to find
//                Naming.rebind("rmi://" + rmiRegistryHost + ":" + rmiRegistryPort + "/" + nodeId, node);
//                System.out.println("Coordinator.main: NodeImp '" + nodeId + "' created and bound to RMI.");
//
//                // Register node with the coordinator
//                coordinator.addNode(nodeId);
//                System.out.println("Coordinator.main: Node '" + nodeId + "' registered with coordinator.");
//
//            } catch (Exception e) {
//                System.err.println("Coordinator.main: CRITICAL - Failed to start or register node '" + nodeId + "': " + e.getMessage());
//                e.printStackTrace();
//                // Decide if test should continue if a node fails
//            }
//        }
//
//        // Brief pause for RMI and multicast listeners to settle
//        System.out.println("Coordinator.main: All nodes initialized. Pausing briefly...");
//        Thread.sleep(3000);
//
//
//        // 4. Setup File Scenarios for nodesSync
//        System.out.println("\nCoordinator.main: Setting up file metadata for nodesSync test...");
//
//        // Scenario 1: File "alpha.txt" exists only on nodeX, should sync to nodeY, nodeZ
//        String fileAlpha = "IT/alpha.txt";
//        String fileAlpha2 = "IT/newfile.txt";
//        FileMeta metaAlpha = new FileMeta(fileAlpha);
//        FileMeta metaAlpha2 = new FileMeta(fileAlpha2);
//        metaAlpha.addNode("nodeX"); // nodeX has the file
//        metaAlpha2.addNode("nodeX"); // nodeX has the file
//        CoordinatorImp.filesMeta.put(fileAlpha, metaAlpha);
//        CoordinatorImp.filesMeta.put(fileAlpha2, metaAlpha2);
//        // Create the actual file on nodeX's storage
//        // This requires NodeImp to have a method like getStorageBasePath()
//        NodeImp nodeXInstance = localNodeInstances.get("nodeX");
//
//
//        // Scenario 2: File "beta.txt" is marked for deletion (empty node list in FileMeta)
//        // It might exist on nodeY and nodeZ, and should be deleted from them.
//        String fileBeta = "HR/beta.txt";
//        FileMeta metaBeta = new FileMeta(fileBeta); // No nodes added, means it should be deleted
//        CoordinatorImp.filesMeta.put(fileBeta, metaBeta);
//        // Create beta.txt on nodeY and nodeZ to test deletion
//        for (String nodeId : new String[]{"nodeY", "nodeZ"}) {
//            NodeImp tempNode = localNodeInstances.get(nodeId);
//            if (tempNode != null) {
//                try {
//                    File actualFileBeta = new File(tempNode.getStorageBasePath() + fileBeta);
//                    actualFileBeta.getParentFile().mkdirs();
//                    try (FileOutputStream fos = new FileOutputStream(actualFileBeta)) {
//                        fos.write(("This " + fileBeta + " on " + nodeId + " should be deleted.").getBytes());
//                    }
//                    System.out.println("  - Created '" + fileBeta + "' on " + nodeId + "'s storage (for deletion test).");
//                } catch (IOException e) {
//                    System.err.println("  - Error creating dummy file '" + fileBeta + "' on " + nodeId + ": " + e.getMessage());
//                }
//            }
//        }
//        System.out.println("Coordinator.main: File metadata setup complete.");
//        System.out.println("  filesMeta before sync: " + CoordinatorImp.filesMeta);
//
//
//        // 5. Call nodesSync
//        System.out.println("\nCoordinator.main: >>> Calling coordinator.nodesSync() <<<");
//        try {
//            coordinator.nodesSync(); // Make sure nodesSync is not private
//            System.out.println("Coordinator.main: <<< coordinator.nodesSync() call finished. >>>");
//        } catch (RemoteException e) {
//            System.err.println("Coordinator.main: Error during nodesSync call: " + e.getMessage());
//        }
//
//
//        // 6. Observe Results
//        System.out.println("\nCoordinator.main: nodesSync executed. Waiting a few seconds for operations to complete...");
//        System.out.println("Check node console logs for sync/delete messages.");
//        System.out.println("Check node storage directories:");
//        if (localNodeInstances.get("nodeY") != null) {
//            System.out.println("  - nodeY (" + localNodeInstances.get("nodeY").getStorageBasePath() + "): Should have '" + fileAlpha + "', should NOT have '" + fileBeta + "'.");
//        }
//        if (localNodeInstances.get("nodeZ") != null) {
//            System.out.println("  - nodeZ (" + localNodeInstances.get("nodeZ").getStorageBasePath() + "): Should have '" + fileAlpha + "', should NOT have '" + fileBeta + "'.");
//        }
//        if (localNodeInstances.get("nodeX") != null) {
//            System.out.println("  - nodeX (" + localNodeInstances.get("nodeX").getStorageBasePath() + "): Should NOT have '" + fileBeta + "' (if it was created there by mistake or if delete is global).");
//        }
//
//        Thread.sleep(10000); // Time for multicast and file ops
//
//        System.out.println("\nCoordinator.main: Test sequence finished.");
//
//    } catch (Exception e) {
//        System.err.println("Coordinator.main: An error occurred in test setup: " + e.toString());
//        e.printStackTrace();
//    } finally {
//        // Optional: clean up RMI bindings, stop nodes, etc.
//        System.out.println("Coordinator.main: Exiting test.");
//        System.exit(0); // Force exit if RMI threads are lingering
//    }
//}

    public static NodeInt getBestNode(List<String> availableNodes) throws ServiceUnavailableException {
        String bestNode = null;
        int min = Integer.MAX_VALUE;
        for (Map.Entry<String, Integer> a : load.entrySet()) {
            if (a.getValue() < min && availableNodes.contains(a.getKey())) {
                bestNode = a.getKey();
                min = a.getValue();
            }
        }
        if (bestNode == null) throw new ServiceUnavailableException("No nodes available");
        return nodes.get(bestNode);
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
            filesMeta.get(fullName).removeNodes(filesMeta.get(fullName).getNodes());
        }
    }

    @Override
    public void addNode(String id) throws RemoteException, MalformedURLException, NotBoundException {
        NodeInt node1 = (NodeInt) Naming.lookup("rmi://localhost:5000/" + id);
        CoordinatorImp.nodes.put(id, node1);
        CoordinatorImp.load.put(id, 0);
    }

    public boolean addEmployee(String token, String username, String password, List<String> roles) throws RemoteException {
        isValidToken(token);
        if(!isManager(token)) throw new InvalidParameterException("Forbidden operation, you should be a manager to add new employees");
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
        otherActionsAllowed(token, fullName.split(",")[0]);
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
        otherActionsAllowed(token, fullName.split(",")[0]);
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
        NodeInt node = null;
        try {
            node = CoordinatorImp.getBestNode(nodes);
            CoordinatorImp.increaseLoad(node);
            CoordinatorImp.makeWrite(fullName);
            node.createFile(ip, port, fullName);
            CoordinatorImp.decreaseLoad(node);
            CoordinatorImp.removeStatus(fullName);

            FileMeta fm = new FileMeta(fullName);
            fm.addNode(node.getNodeId());

            CoordinatorImp.filesMeta.put(fullName, fm);
        } catch (ServiceUnavailableException | RemoteException e) {
            throw new RuntimeException(e);
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
        NodeInt node = null;
        try {
            node = CoordinatorImp.getBestNode(nodes);
            CoordinatorImp.increaseLoad(node);
            CoordinatorImp.makeRead(fullName);
            node.getFile(ip, port, fullName);
            CoordinatorImp.decreaseLoad(node);
            CoordinatorImp.removeStatus(fullName);
        } catch (ServiceUnavailableException | RemoteException e) {
            throw new RuntimeException(e);
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
        NodeInt node = null;
        try {
            node = CoordinatorImp.getBestNode(nodes);
            CoordinatorImp.increaseLoad(node);
            CoordinatorImp.makeWrite(fullName);
            node.updateFile(ip, port, fullName);
            CoordinatorImp.decreaseLoad(node);
            CoordinatorImp.removeStatus(fullName);
        } catch (ServiceUnavailableException | RemoteException e) {
            throw new RuntimeException(e);
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
        NodeInt node = null;
        try {
            node = CoordinatorImp.getBestNode(nodes);
            CoordinatorImp.increaseLoad(node);
            CoordinatorImp.makeWrite(fullName);
            node.deleteFile(fullName);
            CoordinatorImp.decreaseLoad(node);
            CoordinatorImp.removeStatus(fullName);
            CoordinatorImp.deleteFile(fullName);
        } catch (ServiceUnavailableException | RemoteException e) {
            throw new RuntimeException(e);
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
        NodeInt node = null;
        try {
            node = CoordinatorImp.getBestNode(nodes);
            CoordinatorImp.increaseLoad(node);
            node.syncFile(fullName);
            CoordinatorImp.decreaseLoad(node);
        } catch (ServiceUnavailableException | RemoteException e) {
            throw new RuntimeException(e);
        }
    }
}