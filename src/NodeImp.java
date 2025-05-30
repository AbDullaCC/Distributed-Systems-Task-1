
import java.io.*;
import java.net.*;
import java.nio.file.Path;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.nio.file.Files;
import java.nio.file.Paths;


public class NodeImp extends UnicastRemoteObject implements NodeInt {
    private String id;

    public static final String FILE_SYNC_MULTICAST_GROUP = "239.0.0.2"; // Different from your chat example
    public static final int FILE_SYNC_MULTICAST_PORT = 5678;        // Dedicated port for file sync
    public static final int FILE_CHUNK_SIZE = 1024; // Size of each file chunk in bytes (e.g., 1KB)
    public static final int PACKET_BUFFER_SIZE = FILE_CHUNK_SIZE + 512; // Max expected packet size (chunk + headers)
    private FileSyncReceiver fileSyncReceiver;
    private Thread fileSyncReceiverThread;
    private final String storageBasePath;
   public NodeImp(String nodeId, String publicIp) throws RemoteException {
        super();
        this.id = nodeId;


        this.storageBasePath = "storage/"+nodeId + "/";
        File storageDir = new File(this.storageBasePath);
      if (!storageDir.exists()) {
            if (storageDir.mkdirs()) {
                System.out.println("Storage directory created: " + this.storageBasePath);
            } else {
                System.err.println("Failed to create storage directory: " + this.storageBasePath);
            }
        }
        System.out.println("Node " + id + " is ready at path: " + this.storageBasePath);

       // Inside NodeImp constructor, after other initializations
       this.fileSyncReceiver = new FileSyncReceiver(this.id, this.storageBasePath);
       this.fileSyncReceiverThread = new Thread(this.fileSyncReceiver, "FileSyncReceiver-" + this.id);
       this.fileSyncReceiverThread.start();
       System.out.println("Node " + this.id + ": FileSyncReceiver thread started.");
    }


    private String getFileSystemPath(String name) {
        return this.storageBasePath + name;
    }

    @Override
    public String getNodeId() throws RemoteException {
        return this.id;
    }

    @Override
    public void syncDeleteFile(String fullName) throws RemoteException {

        System.out.println("Node " + id + ": Attempting to delete file: " + fullName);

        boolean existsOnDisk = Files.exists(Paths.get(getFileSystemPath(fullName)));

        if (!existsOnDisk) {
            System.out.println("Node " + id + ": File " + fullName + " not found in memory or disk.");

        }


        boolean deletedFromFileSystem = false;

        try {
            deletedFromFileSystem = Files.deleteIfExists(Paths.get(getFileSystemPath(fullName)));
        } catch (IOException e) {
            System.err.println("Node " + id + ": Failed to delete file " + fullName + ": " + e.getMessage());
            e.printStackTrace();

        }

        System.out.println("Node " + id + ": File " + fullName + " deleted : " + deletedFromFileSystem + ").");

    }

    @Override
    public void syncFile(String fullName) throws RemoteException {
        System.out.println("Node " + this.id + ": Initiating multicast sync for file: " + fullName);
        String filePathStr = getFileSystemPath(fullName); // Use your existing helper
        if (filePathStr == null) {
            System.err.println("Node " + this.id + ": Invalid fullName, cannot sync: " + fullName);
            // Depending on desired behavior, you might throw an exception or return
            throw new RemoteException("Invalid fullName format on node " + this.id + ": " + fullName);
        }

        File file = new File(filePathStr);
        if (!file.exists() || !file.isFile()) {
            System.err.println("Node " + this.id + ": File not found or is not a file, cannot sync: " + filePathStr);
            throw new RemoteException("File not found on node " + this.id + " for sync: " + fullName);
        }

        // Try-with-resources for MulticastSocket and FileInputStream
        try (MulticastSocket multicastSocket = new MulticastSocket(); // OS will pick a port for sending
             FileInputStream fis = new FileInputStream(file)) {

            InetAddress groupAddress = InetAddress.getByName(FILE_SYNC_MULTICAST_GROUP);
            long fileSize = file.length();
            int totalChunks = (int) Math.ceil((double) fileSize / FILE_CHUNK_SIZE);
            if (totalChunks == 0 && fileSize > 0) totalChunks = 1; // Handle very small files
            if (fileSize == 0) totalChunks = 0; // Empty file means 0 data chunks, but SOF/EOF still important

            System.out.println("Node " + this.id + ": Syncing " + fullName + " (Size: " + fileSize + " bytes, Chunks: " + totalChunks + ")");

            // 1. Send Start of File (SOF) packet
            // Format: "SOF:<senderNodeId>:<fullName>:<fileSize>:<totalChunks>"
            String sofMessage = String.format("SOF:%s:%s:%d:%d", this.id, fullName, fileSize, totalChunks);
            byte[] sofBuffer = sofMessage.getBytes("UTF-8");
            DatagramPacket sofPacket = new DatagramPacket(sofBuffer, sofBuffer.length, groupAddress, FILE_SYNC_MULTICAST_PORT);
            multicastSocket.send(sofPacket);
            System.out.println("Node " + this.id + ": Sent SOF for " + fullName);
            Thread.sleep(10); // Small delay

            // 2. Send data chunks (if any)
            if (fileSize > 0) {
                byte[] chunkDataBuffer = new byte[FILE_CHUNK_SIZE];
                int bytesRead;
                int chunkNumber = 0;
                while ((bytesRead = fis.read(chunkDataBuffer)) != -1) {
                    chunkNumber++;
                    // Packet format: "CHUNK:<senderNodeId>:<fullName>:<chunkNumber>:<totalChunks>" (header)
                    // followed by the actual chunk data.
                    // We need to combine the header string and the byte data for the chunk.
                    String header = String.format("CHUNK:%s:%s:%d:%d:", this.id, fullName, chunkNumber, totalChunks);
                    byte[] headerBytes = header.getBytes("UTF-8");

                    byte[] packetContent = new byte[headerBytes.length + bytesRead];
                    System.arraycopy(headerBytes, 0, packetContent, 0, headerBytes.length);
                    System.arraycopy(chunkDataBuffer, 0, packetContent, headerBytes.length, bytesRead);

                    DatagramPacket dataPacket = new DatagramPacket(packetContent, packetContent.length, groupAddress, FILE_SYNC_MULTICAST_PORT);
                    multicastSocket.send(dataPacket);
                    if (chunkNumber % 100 == 0) { // Log progress occasionally
                        System.out.println("Node " + this.id + ": Sent chunk " + chunkNumber + "/" + totalChunks + " for " + fullName);
                    }
                    Thread.sleep(5); // Small delay to avoid flooding, adjust based on network
                }
            }

            // 3. Send End of File (EOF) packet
            String eofMessage = String.format("EOF:%s:%s", this.id, fullName);
            byte[] eofBuffer = eofMessage.getBytes("UTF-8");
            DatagramPacket eofPacket = new DatagramPacket(eofBuffer, eofBuffer.length, groupAddress, FILE_SYNC_MULTICAST_PORT);
            multicastSocket.send(eofPacket);
            System.out.println("Node " + this.id + ": Sent EOF for " + fullName);
            System.out.println("Node " + this.id + ": Multicast sending for " + fullName + " completed.");

        } catch (IOException e) {
            System.err.println("Node " + this.id + ": IOException during multicast sync for " + fullName + ": " + e.getMessage());
            e.printStackTrace();
            throw new RemoteException("Failed to multicast sync file " + fullName + " from node " + this.id, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Node " + this.id + ": Multicast send interrupted for " + fullName + ": " + e.getMessage());
            throw new RemoteException("Multicast send interrupted on node " + this.id, e);
        }
    }

    @Override
    public boolean ping() throws RemoteException {
        System.out.println("Node " + id + ": Ping received.");
        return true;
    }

    @Override
    public synchronized boolean createFile(String socketAddress, int port,  String name) throws RemoteException {
        String filePath = getFileSystemPath(name);

        File file = new File(filePath);
        File parentDir = file.getParentFile();

        // Create parent directories if they don't exist
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                System.err.println("Node " + id + ": Failed to create directory " + parentDir.getAbsolutePath());
                return false;
            }
        }

        if (file.exists()) {
            System.out.println("Node " + id + ": File " + name + " already exists. Cannot create.");
            return false;
        }

        try (Socket nodeSocket = new Socket(socketAddress, port);
             InputStream Nodein = nodeSocket.getInputStream();
             FileOutputStream fos = new FileOutputStream(file)) {
            File directory = new File(this.storageBasePath + File.separator + name);
            if (!directory.exists()) {
                directory.mkdirs();
            }
            System.out.println("Node " + id + ": Receiving file " + name + " on port " + port);

            Nodein.transferTo(fos);
//            byte[] buffer = new byte[4096];
//            int bytesRead;
//            while ((bytesRead = in.read(buffer)) != -1) {
//                fos.write(buffer, 0, bytesRead);
//            }

            System.out.println("Node " + id + ": File " + name + " created successfully.");
            return true;

        } catch (IOException e) {
            System.err.println("Node " + id + ": Error creating file " + name + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
 @Override
    public void getFile(String socketAddress, int port,String fileName) throws RemoteException {
        File file = new File(storageBasePath +File.separator+ fileName);
        if (!file.exists()) {
            throw new RemoteException("File not found: " + file.getAbsolutePath());
        }

        try (Socket nodeSocket = new Socket(socketAddress, port);
             OutputStream out = nodeSocket.getOutputStream();
             FileInputStream fis = new FileInputStream(file)) {

            byte[] buffer = new byte[4096];
            int bytesRead;

            System.out.println("Sending file: " + file.getName() + " to " + socketAddress + ":" + port);

            while ((bytesRead = fis.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }

            out.flush();
            System.out.println("File sent successfully.");

        } catch (IOException e) {
            e.printStackTrace();
            throw new RemoteException("Error sending file to client socket", e);
        }

    }

    @Override
    public synchronized boolean updateFile(String socketAddress, int port,  String name) throws RemoteException {
    String filePath = getFileSystemPath(name);

        System.out.println("Node " + id + ": Waiting to receive updated file: " + name + " on port " + port);

        try (Socket nodeSocket = new Socket(socketAddress, port);
             InputStream Nodein = nodeSocket.getInputStream();
             FileOutputStream fos = new FileOutputStream(filePath, false)) {

            File directory = new File(this.storageBasePath + File.separator + name);
            if (!directory.exists()) {
                directory.mkdirs();
            }
            Nodein.transferTo(fos);

//            byte[] buffer = new byte[4096];
//            int bytesRead;
//            while ((bytesRead = in.read(buffer)) != -1) {
//                fos.write(buffer, 0, bytesRead);
//            }
//
//            fos.flush();
//            System.out.println("Node " + id + ": File " + fileKey + " updated successfully from socket.");


            return true;

        } catch (IOException e) {
            System.err.println("Node " + id + ": Error updating file " + name + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public synchronized boolean deleteFile(String name) throws RemoteException {

        System.out.println("Node " + id + ": Attempting to delete file: " + name);

       boolean existsOnDisk = Files.exists(Paths.get(getFileSystemPath(name)));

        if (!existsOnDisk) {
            System.out.println("Node " + id + ": File " + name + " not found in memory or disk.");
            return false;
        }


        boolean deletedFromFileSystem = false;

        try {
            deletedFromFileSystem = Files.deleteIfExists(Paths.get(getFileSystemPath(name)));
        } catch (IOException e) {
            System.err.println("Node " + id + ": Failed to delete file " + name + ": " + e.getMessage());
            e.printStackTrace();

            return false;
        }

        System.out.println("Node " + id + ": File " + name + " deleted : " + deletedFromFileSystem + ").");
        return true;
    }


    public static void main(String[] args) { // Removed 'throws RemoteException', handle exceptions locally
        if (args.length < 1) {
            System.err.println("Usage: java NodeImp <nodeId> [publicIpAddress]");
            System.err.println("Example: java NodeImp Node1 localhost");
            System.err.println("If publicIpAddress is not provided, it will attempt to use the local host address.");
            return;
        }
        String nodeId = args[0];
        String publicIp = "localhost"; // Default

        if (args.length > 1) {
            publicIp = args[1];
        } else {
            try {
                // Attempt to get local IP if not provided.
                // This might not be the externally reachable IP in all network setups.
                publicIp = InetAddress.getLocalHost().getHostAddress();
            } catch (UnknownHostException e) {
                System.err.println("Warning: Could not determine local host address for " + nodeId +
                        ". Defaulting to 'localhost'. Provide public IP as argument if needed for cross-machine testing.");
                publicIp = "localhost"; // Fallback
            }
        }

        try {
            // Optional: Check if RMI registry is running or start it.
            // For testing, it's often easier if the Coordinator starts it.
            // If not, you might need to run `rmiregistry 5000` in a separate terminal
            // or ensure one node/coordinator does LocateRegistry.createRegistry(5000).

            NodeImp node = new NodeImp(nodeId, publicIp); // Assumes constructor takes (nodeId, publicIp)

            Naming.rebind("rmi://localhost:5000/" + nodeId, node);
            System.out.println("Node " + nodeId + " (IP: " + publicIp + ") is running and bound in RMI registry as '" + nodeId + "'.");

            // Node registers itself with the Coordinator
            CoordinatorInt coordinator = (CoordinatorInt) Naming.lookup("rmi://localhost:5000/coordinator");
            coordinator.addNode(nodeId); // Pass both nodeId and publicIp
            System.out.println("Node " + nodeId + " successfully registered with the Coordinator.");

        } catch (MalformedURLException e) {
            System.err.println("Node " + nodeId + ": MalformedURLException - RMI URL is incorrect: " + e.getMessage());
        } catch (RemoteException e) {
            System.err.println("Node " + nodeId + ": RemoteException - Could not connect to RMI services. " +
                    "Ensure RMI Registry (port 5000) and Coordinator are running: " + e.getMessage());
            // e.printStackTrace(); // For full trace
        } catch (NotBoundException e) {
            System.err.println("Node " + nodeId + ": Coordinator not found in RMI Registry. " +
                    "Ensure Coordinator is running and has bound itself: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Node " + nodeId + ": An unexpected error occurred during startup: " + e.getMessage());
            e.printStackTrace();
        }
    }
    // Example shutdown method in NodeImp
    public void shutdownNode() {
        System.out.println("Node " + this.id + ": Shutting down...");
        if (fileSyncReceiver != null) {
            fileSyncReceiver.stopReceiver();
        }
        if (fileSyncReceiverThread != null) {
            try {
                fileSyncReceiverThread.join(5000); // Wait for thread to finish
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Node " + this.id + ": Interrupted while waiting for FileSyncReceiver to stop.");
            }
        }
        // ... other RMI unbinding logic if needed ...
        System.out.println("Node " + this.id + ": Shutdown complete.");
    }
}
