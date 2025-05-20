
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.io.File; // For java.io.File, if used directly
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList; // For initializing FileMeta nodes list
import java.util.List;

public class NodeImp extends UnicastRemoteObject implements NodeInt {
    private String id; // Unique identifier for this node
    // Stores files using a key like "department/filename"
    private HashMap<String, ActualFile> files;
    // Base directory for storing actual files on this node's filesystem
    private final String storageBasePath;


    /**
     * Constructor for NodeImp.
     * @param nodeId The unique identifier for this node.
     * @throws RemoteException if an RMI error occurs.
     */
    public NodeImp(String nodeId) throws RemoteException {
        super(); // Call to UnicastRemoteObject constructor
        this.id = nodeId;
        this.files = new HashMap<>();
        // Example: "node_storage/node1_files/"
        // Ensure this directory exists or is created upon node startup.
        this.storageBasePath = "../storage/"+nodeId + "/";
        File storageDir = new File(this.storageBasePath);
        if (!storageDir.exists()) {
            if (storageDir.mkdirs()) {
                System.out.println("Storage directory created: " + this.storageBasePath);
            } else {
                System.err.println("Failed to create storage directory: " + this.storageBasePath);
                // Consider throwing an exception or handling this more gracefully
            }
        }
        System.out.println("Node " + id + " is ready at path: " + this.storageBasePath);
    }

    private String getFileMapKey(String dep, String name) {
        return dep + File.separator + name; // Use File.separator for OS independence
    }

    private String getFileSystemPath(String dep, String name) {
        return this.storageBasePath + dep + File.separator + name;
    }

    @Override
    public String getNodeId() throws RemoteException {
        return this.id;
    }

    @Override
    public boolean ping() throws RemoteException {
        System.out.println("Node " + id + ": Ping received.");
        return true; // Simply return true to indicate responsiveness
    }


    @Override
    public synchronized boolean createFile(ActualFile actualFile) throws RemoteException {
        if (actualFile == null || actualFile.getMeta() == null) {
            System.err.println("Node " + id + ": Attempted to create a null file or file with null metadata.");
            return false;
        }
        FileMeta meta = actualFile.getMeta();
        String fileKey = getFileMapKey(meta.getDep(), meta.getName());
        String filePath = getFileSystemPath(meta.getDep(), meta.getName());

        System.out.println("Node " + id + ": Attempting to create file: " + fileKey + " at " + filePath);

        if (files.containsKey(fileKey)) {
            System.out.println("Node " + id + ": File " + fileKey + " already exists. Use updateFile instead.");
            return false; // Or handle as an update if that's desired behavior
        }

        try {
            File directory = new File(this.storageBasePath + meta.getDep());
            if (!directory.exists()) {
                if (!directory.mkdirs()) {
                    System.err.println("Node " + id + ": Failed to create department directory: " + directory.getPath());
                    return false;
                }
            }
            // Write file content to the node's local filesystem
            Files.write(Paths.get(filePath), actualFile.getFileContent(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            // Store the ActualFile object (metadata + reference or content) in the map
            files.put(fileKey, actualFile);
            System.out.println("Node " + id + ": File " + fileKey + " created successfully.");
            return true;
        } catch (IOException e) {
            System.err.println("Node " + id + ": Error creating file " + fileKey + " on filesystem: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public synchronized ActualFile getFile(String name, String dep) throws RemoteException {
        String fileKey = getFileMapKey(dep, name);
        String filePath = getFileSystemPath(dep, name);
        System.out.println("Node " + id + ": Attempting to get file: " + fileKey);

        // First, check our in-memory map
        ActualFile actualFileFromMap = files.get(fileKey);

        if (actualFileFromMap != null) {
            // Optionally, verify if file content needs to be re-read from disk or if map version is sufficient
            // For this example, we assume the map's content is up-to-date or doesn't store the full content.
            // If the map only stores metadata, or if content can change externally, reload from disk.

            try {
                if (Files.exists(Paths.get(filePath))) {
                    byte[] content = Files.readAllBytes(Paths.get(filePath));
                    // Ensure the ActualFile object has the latest content
                    actualFileFromMap.setFileContent(content);
                    System.out.println("Node " + id + ": File " + fileKey + " retrieved successfully (from map, content reloaded).");
                    return actualFileFromMap;
                } else {
                    // File in map but not on disk: inconsistency
                    System.err.println("Node " + id + ": Inconsistency! File " + fileKey + " in map but not on disk at " + filePath);
                    files.remove(fileKey); // Clean up inconsistent entry
                    return null;
                }
            } catch (IOException e) {
                System.err.println("Node " + id + ": Error reading file content for " + fileKey + " from filesystem: " + e.getMessage());
                e.printStackTrace();
                return null; // Or throw a specific exception
            }
        } else {
            // If not in map, try to load from disk (e.g., if node restarted and map is not persisted)
            // This part is more for robustness if the 'files' map isn't the sole source of truth
            try {
                if (Files.exists(Paths.get(filePath))) {
                    byte[] content = Files.readAllBytes(Paths.get(filePath));
                    // We need FileMeta. For simplicity, let's assume if we find it on disk,
                    // we can reconstruct basic metadata or this scenario is for files already known.
                    // In a real system, metadata persistence would be crucial.
                    List<String> nodeIds = new ArrayList<>(); // Placeholder
                    nodeIds.add(this.id);
                    FileMeta meta = new FileMeta(name, dep, nodeIds);
                    ActualFile retrievedFile = new ActualFile(meta, content);
                    files.put(fileKey, retrievedFile); // Add to map for future access
                    System.out.println("Node " + id + ": File " + fileKey + " retrieved successfully (from disk, added to map).");
                    return retrievedFile;
                }
            } catch (IOException e) {
                System.err.println("Node " + id + ": Error reading file " + fileKey + " from filesystem: " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        }
        System.out.println("Node " + id + ": File " + fileKey + " not found.");
        return null;
    }

    @Override
    public synchronized boolean updateFile(ActualFile actualFile) throws RemoteException {
        if (actualFile == null || actualFile.getMeta() == null) {
            System.err.println("Node " + id + ": Attempted to update a null file or file with null metadata.");
            return false;
        }
        FileMeta meta = actualFile.getMeta();
        String fileKey = getFileMapKey(meta.getDep(), meta.getName());
        String filePath = getFileSystemPath(meta.getDep(), meta.getName());
        System.out.println("Node " + id + ": Attempting to update file: " + fileKey);

        if (!files.containsKey(fileKey) && !Files.exists(Paths.get(filePath))) {
            System.out.println("Node " + id + ": File " + fileKey + " not found. Cannot update.");
            return false;
        }

        try {
            // Ensure department directory exists (it should if file exists, but good practice)
            File directory = new File(this.storageBasePath + meta.getDep());
            if (!directory.exists()) {
                directory.mkdirs();
            }
            // Overwrite existing file content on the node's local filesystem
            Files.write(Paths.get(filePath), actualFile.getFileContent(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            // Update the ActualFile object in the map
            files.put(fileKey, actualFile);
            System.out.println("Node " + id + ": File " + fileKey + " updated successfully.");
            return true;
        } catch (IOException e) {
            System.err.println("Node " + id + ": Error updating file " + fileKey + " on filesystem: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public synchronized boolean deleteFile(String name, String dep) throws RemoteException {
        String fileKey = getFileMapKey(dep, name);
        String filePath = getFileSystemPath(dep, name);
        System.out.println("Node " + id + ": Attempting to delete file: " + fileKey);

        if (!files.containsKey(fileKey) && !Files.exists(Paths.get(filePath))) {
            System.out.println("Node " + id + ": File " + fileKey + " not found. Cannot delete.");
            return false;
        }

        boolean deletedFromMap = files.remove(fileKey) != null;
        boolean deletedFromFileSystem = false;
        try {
            deletedFromFileSystem = Files.deleteIfExists(Paths.get(filePath));
        } catch (IOException e) {
            System.err.println("Node " + id + ": Error deleting file " + fileKey + " from filesystem: " + e.getMessage());
            e.printStackTrace();
            // If map removal succeeded but disk failed, there's an inconsistency.
            // May need to re-add to map or log for manual intervention.
            if(deletedFromMap) files.put(fileKey, null); // Or some placeholder indicating issue
            return false;
        }

        if (deletedFromMap || deletedFromFileSystem) {
            System.out.println("Node " + id + ": File " + fileKey + " deleted (Map: " + deletedFromMap + ", FS: " + deletedFromFileSystem + ").");
            return true;
        } else {
            System.out.println("Node " + id + ": File " + fileKey + " was not found for deletion (neither in map nor FS).");
            return false;
        }
    }

    @Override
    public synchronized boolean fileExists(String name, String dep) throws RemoteException {
        String fileKey = getFileMapKey(dep, name);
        String filePath = getFileSystemPath(dep, name);
        // Check in memory first, then filesystem for robustness
        if (files.containsKey(fileKey)) {
            // Optionally, verify it also exists on disk if there could be discrepancies
            // if (Files.exists(Paths.get(filePath))) {
            //     return true;
            // } else {
            //     files.remove(fileKey); // Inconsistency
            //     return false;
            // }
            return true;
        }
        return Files.exists(Paths.get(filePath));
    }


    @Override
    public synchronized boolean cloneFile(NodeInt targetNodeStub, String fileKeyToClone) throws RemoteException {
        // fileKeyToClone is expected to be in "dep/name" format
        System.out.println("Node " + id + ": Attempting to clone file " + fileKeyToClone + " to node " + targetNodeStub.getNodeId());

        ActualFile fileToClone = null;
        // First, try to get from map
        if (files.containsKey(fileKeyToClone)) {
            fileToClone = files.get(fileKeyToClone);
            // Ensure content is loaded if not already fully in 'ActualFile'
            String[] parts = fileKeyToClone.split(File.separator, 2);
            if (parts.length == 2) {
                String dep = parts[0];
                String name = parts[1];
                String filePath = getFileSystemPath(dep, name);
                try {
                    if (Files.exists(Paths.get(filePath))) {
                        byte[] content = Files.readAllBytes(Paths.get(filePath));
                        fileToClone.setFileContent(content); // Make sure content is up-to-date
                    } else {
                        System.err.println("Node " + id + ": File " + fileKeyToClone + " found in map but not on disk for cloning.");
                        return false;
                    }
                } catch (IOException e) {
                    System.err.println("Node " + id + ": Error reading content of " + fileKeyToClone + " for cloning: " + e.getMessage());
                    return false;
                }
            } else {
                System.err.println("Node " + id + ": Invalid file key format for cloning: " + fileKeyToClone);
                return false;
            }

        } else { // If not in map, try to load from disk directly
            String[] parts = fileKeyToClone.split(File.separator, 2);
            if (parts.length == 2) {
                String dep = parts[0];
                String name = parts[1];
                fileToClone = this.getFile(name, dep); // This method handles loading from disk
            } else {
                System.err.println("Node " + id + ": Invalid file key format for cloning: " + fileKeyToClone);
                return false;
            }
        }


        if (fileToClone == null) {
            System.err.println("Node " + id + ": File " + fileKeyToClone + " not found on this node. Cannot clone.");
            return false;
        }

        try {
            // Attempt to create the file on the target node
            boolean success = targetNodeStub.createFile(fileToClone);
            if (success) {
                System.out.println("Node " + id + ": File " + fileKeyToClone + " successfully cloned to node " + targetNodeStub.getNodeId());
            } else {
                System.err.println("Node " + id + ": Failed to clone file " + fileKeyToClone + " to node " + targetNodeStub.getNodeId() + ". Target node reported failure.");
            }
            return success;
        } catch (RemoteException e) {
            System.err.println("Node " + id + ": RMI error during cloning file " + fileKeyToClone + " to node " + targetNodeStub.getNodeId() + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // Main method for starting a Node (Example Usage)
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java NodeImp <nodeId> [port]");
            System.exit(1);
        }
        String nodeId = args[0];
        int port = 1099; // Default RMI registry port
        if (args.length >= 2) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number: " + args[1] + ". Using default port " + port);
            }
        }

        try {
            // Set hostname for RMI stub. Important for clients to connect.
            // java.rmi.server.hostname property can be set if needed, e.g., for specific network interface
            // System.setProperty("java.rmi.server.hostname", "your.server.ip.address");


            NodeImp node = new NodeImp(nodeId);

            // Bind the remote object's stub in the RMI registry
            // Registry registry;
            // try {
            //     registry = LocateRegistry.getRegistry(port);
            //     registry.list(); // Check if registry is there
            // } catch (RemoteException e) {
            //     System.out.println("RMI registry cannot be contacted at port " + port + ", creating new one.");
            //     registry = LocateRegistry.createRegistry(port);
            // }
            // For simplicity, we'll use Naming.rebind which uses default registry or specified URL
            // The RMI registry must be running. You can start it with `rmiregistry [port]`
            // Or create it within the application if not running.

            java.rmi.registry.LocateRegistry.createRegistry(port); // Start registry within this JVM
            System.out.println("RMI registry created on port " + port);


            String rmiUrl = "//localhost:" + port + "/" + nodeId;
            java.rmi.Naming.rebind(rmiUrl, node);

            System.out.println("Node " + nodeId + " bound in RMI registry as " + rmiUrl);
            System.out.println("Node " + nodeId + " is running and waiting for requests...");

        } catch (Exception e) {
            System.err.println("Node " + nodeId + " server exception: " + e.toString());
            e.printStackTrace();
        }
    }
}
