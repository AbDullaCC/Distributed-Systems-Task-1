
import java.io.*;
import java.net.*;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.net.DatagramPacket;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Calendar;
import java.util.Date;

public class NodeImp extends UnicastRemoteObject implements NodeInt {
    private String id;

    public String getStorageBasePath() {
        return storageBasePath;
    }

    // Multicast Configuration
    private static final String MULTICAST_ADDRESS = "239.0.0.1"; // Standard multicast example address
    private static final int MULTICAST_PORT = 1234;         // Standard multicast example port
    private static final int MULTICAST_PACKET_BUFFER_SIZE = 1500; // Max packet size for receiving
    private static final int DATA_CHUNK_MAX_PAYLOAD_SIZE = 1024; // Max data payload for sending (leaves room for headers)
    private static final String MSG_DELIMITER = ":";
    private MulticastSocket multicastListenSocket;
    private InetAddress multicastGroupAddress;
    private Timer timer = new Timer();
    // To keep track of files being received via multicast
    // Key: "senderNodeId:fileName", Value: FileOutputStream
    private final Map<String, FileOutputStream> receivingFilesMap = new HashMap<>();

    private final String storageBasePath;
    public NodeImp(String nodeId) throws RemoteException {
        super();
        this.id = nodeId;
        this.storageBasePath = "storage/" + nodeId + File.separator;
        File storageDir = new File(this.storageBasePath);
        if (!storageDir.exists()) {
            if (storageDir.mkdirs()) {
                System.out.println("Node " + id + ": Storage directory created: " + this.storageBasePath);
            } else {
                System.err.println("Node " + id + ": Failed to create storage directory: " + this.storageBasePath);
            }
        }
        System.out.println("Node " + id + " is ready at path: " + this.storageBasePath);



        // Graceful shutdown of multicast socket
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (multicastListenSocket != null && !multicastListenSocket.isClosed()) {
                try {
                    System.out.println("Node " + id + ": Leaving multicast group and closing socket...");
                    multicastListenSocket.leaveGroup(multicastGroupAddress);
                    multicastListenSocket.close();
                    System.out.println("Node " + id + ": Multicast socket closed on shutdown.");
                } catch (IOException ex) {
                    System.err.println("Node " + id + ": Error closing multicast socket on shutdown: " + ex.getMessage());
                }
            }
        }));
    }
    @Override
    public void syncFile(String fullName) throws RemoteException {
        System.out.println("Node " + id + ": Initiating multicast sync for file: " + fullName);
        File file = new File(this.storageBasePath + fullName);
        if (!file.exists()) {
            System.err.println("Node " + id + ": File " + fullName + " not found for syncing.");
            return;
        }

        try (FileInputStream fis = new FileInputStream(file);
             MulticastSocket sendSocket = new MulticastSocket()) {

            // 1. Send START message
            MulticastMessage startMsg = new MulticastMessage(id, fullName, MulticastMessageType.START, null);
            byte[] startBuffer = startMsg.toBytes();
            sendSocket.send(new DatagramPacket(startBuffer, startBuffer.length, multicastGroupAddress, MULTICAST_PORT));
            System.out.println("Node " + id + ": Sent START for " + fullName);
            Thread.sleep(20);

            // 2. Send DATA chunks
            byte[] dataChunk = new byte[DATA_CHUNK_MAX_PAYLOAD_SIZE];
            int bytesRead;
            while ((bytesRead = fis.read(dataChunk)) != -1) {
                byte[] payload = Arrays.copyOf(dataChunk, bytesRead); // Trim excess
                MulticastMessage dataMsg = new MulticastMessage(id, fullName, MulticastMessageType.DATA, payload);
                byte[] dataBytes = dataMsg.toBytes();
                sendSocket.send(new DatagramPacket(dataBytes, dataBytes.length, multicastGroupAddress, MULTICAST_PORT));
                Thread.sleep(5);
            }

            // 3. Send END message
            MulticastMessage endMsg = new MulticastMessage(id, fullName, MulticastMessageType.END, null);
            byte[] endBuffer = endMsg.toBytes();
            sendSocket.send(new DatagramPacket(endBuffer, endBuffer.length, multicastGroupAddress, MULTICAST_PORT));
            System.out.println("Node " + id + ": Sent END for " + fullName);

        } catch (IOException | InterruptedException e) {
            throw new RemoteException("Error syncing file: " + fullName, e);
        }
    }


    private void listenForMulticastMessages() {
        byte[] buffer = new byte[MULTICAST_PACKET_BUFFER_SIZE];
        System.out.println("Node " + id + ": Multicast listener started on " + MULTICAST_ADDRESS + ":" + MULTICAST_PORT);

        while (multicastListenSocket != null && !multicastListenSocket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                multicastListenSocket.receive(packet);

                MulticastMessage msg = MulticastMessageParser.fromBytes(packet.getData(), packet.getLength());
                if (msg == null) {
                    System.err.println("Node " + id + ": Skipping malformed message.");
                    continue;
                }

                if (msg.getSenderNodeId().equals(this.id)) continue;

                String key = msg.getSenderNodeId() + MSG_DELIMITER + msg.getFilePath();

                switch (msg.getType()) {
                    case START -> {
                        if (receivingFilesMap.containsKey(key)) {
                            try { receivingFilesMap.get(key).close(); } catch (IOException ignored) {}
                            receivingFilesMap.remove(key);
                        }
                        File fileToReceive = new File(this.storageBasePath + msg.getFilePath());
                        if (!fileToReceive.getParentFile().exists()) {
                            fileToReceive.getParentFile().mkdirs();
                        }
                        try {
                            FileOutputStream fos = new FileOutputStream(fileToReceive);
                            receivingFilesMap.put(key, fos);
                            System.out.println("Node " + id + ": Started receiving sync for " + msg.getFilePath() + " from " + msg.getSenderNodeId());
                        } catch (FileNotFoundException e) {
                            System.err.println("Node " + id + ": Cannot open file for receiving: " + e.getMessage());
                        }
                    }

                    case DATA -> {
                        FileOutputStream fos = receivingFilesMap.get(key);
                        if (fos != null && msg.getPayload() != null) {
                            fos.write(msg.getPayload());
                        }
                    }

                    case END -> {
                        FileOutputStream fos = receivingFilesMap.remove(key);
                        if (fos != null) {
                            fos.close();
                            System.out.println("Node " + id + ": Finished receiving sync for " + msg.getFilePath());
                        }
                    }

                    case ERROR_HEADER_TOO_LARGE -> {
                        FileOutputStream fos = receivingFilesMap.remove(key);
                        if (fos != null) {
                            fos.close();
                            new File(this.storageBasePath + msg.getFilePath()).delete();
                            System.err.println("Node " + id + ": Sync for " + msg.getFilePath() + " aborted by sender due to header size.");
                        }
                    }

                    default -> {
                        System.out.println("Node " + id + ": Unknown message type: " + msg.getType());
                    }
                }

            } catch (SocketException se) {
                if (multicastListenSocket.isClosed()) break;
                System.err.println("Node " + id + ": SocketException: " + se.getMessage());
            } catch (IOException e) {
                System.err.println("Node " + id + ": IOException: " + e.getMessage());
            }
        }

        // Cleanup
        for (Map.Entry<String, FileOutputStream> entry : receivingFilesMap.entrySet()) {
            try { entry.getValue().close(); } catch (IOException ignored) {}
        }
        receivingFilesMap.clear();
        System.out.println("Node " + id + ": Multicast listener thread exited.");
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

    /**
     * Static helper method to start a Node instance with a specific ID.
     * @param nodeId The unique identifier for this node instance.
     */
    public static void startInstance(String nodeId) {
        String rmiRegistryHost = "localhost";
        String rmiRegistryPort = "5000";
        String rmiBindingName = nodeId; // Bind the node using its ID

        try {
            System.setProperty("java.rmi.server.hostname", rmiRegistryHost);

            NodeImp node = new NodeImp(nodeId);
            node.scheduleTask();

            try {
                LocateRegistry.createRegistry(Integer.parseInt(rmiRegistryPort));
                System.out.println(node.getNodeId() + ": RMI registry created on port " + rmiRegistryPort);
            } catch (RemoteException e) {
                System.out.println(node.getNodeId() + ": RMI registry likely already running on port " + rmiRegistryPort + ".");
            }

            String rmiUrl = "rmi://" + rmiRegistryHost + ":" + rmiRegistryPort + "/" + rmiBindingName;
            Naming.rebind(rmiUrl, node);
            System.out.println(">>> Node " + node.getNodeId() + " is running and bound to " + rmiUrl + " <<<");

            try {
                System.out.println(node.getNodeId() + ": Attempting to lookup Coordinator...");
                String coordinatorRmiUrl = "rmi://" + rmiRegistryHost + ":" + rmiRegistryPort + "/coordinator";
                CoordinatorInt coordinator = (CoordinatorInt) Naming.lookup(coordinatorRmiUrl);

                System.out.println(node.getNodeId() + ": Coordinator found. Attempting to register...");
                coordinator.addNode(node.getNodeId()); // Use the getter for the ID
                System.out.println(node.getNodeId() + ": Successfully registered with the Coordinator.");
            } catch (NotBoundException e) {
                System.err.println(node.getNodeId() + ": CRITICAL - Coordinator not found. Ensure Coordinator is running. " + e.getMessage());
            } catch (RemoteException e) {
                System.err.println(node.getNodeId() + ": CRITICAL - RemoteException during Coordinator interaction: " + e.getMessage());
            } catch (Exception e) {
                System.err.println(node.getNodeId() + ": CRITICAL - Error during Coordinator interaction: " + e.getMessage());
            }

        } catch (RemoteException e) { // From NodeImp constructor or Naming.rebind
            System.err.println("Node " + nodeId + " CRITICAL - RemoteException during startup: " + e.toString());
            e.printStackTrace(); // Important for debugging startup issues
            // Consider System.exit(1) if a node cannot start
        } catch (Exception e) { // Catch other startup errors
            System.err.println("Node " + nodeId + " CRITICAL - General error during startup: " + e.toString());
            e.printStackTrace();
        }
    }
//    public static void main(String[] args) throws RemoteException {
//        try {
//            NodeImp node = new NodeImp("node_1");
//
//            Naming.rebind("rmi://localhost:5000/node_1", node);
//            CoordinatorInt coordinator =(CoordinatorInt) Naming.lookup("rmi://localhost:5000/coordinator");
//            coordinator.addNode(node.id);
//            System.out.println(node.id + "is running");
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }

    public static void main(String[] args) {
       startInstance("node_3");

    }
    public enum MulticastMessageType {
        START,
        DATA,
        END,
        ERROR_HEADER_TOO_LARGE
    }



    public static class MulticastMessage { // Made static
        private final String senderNodeId;
        private final String filePath;
        private final MulticastMessageType type;
        private final byte[] payload;


        public MulticastMessage(String senderNodeId, String filePath, MulticastMessageType type, byte[] payload) {
            this.senderNodeId = senderNodeId;
            this.filePath = filePath;
            this.type = type;
            this.payload = payload;
        }


        public String getSenderNodeId() { return senderNodeId; }
        public String getFilePath() { return filePath; }
        public MulticastMessageType getType() { return type; }
        public byte[] getPayload() { return payload; }


        public byte[] toBytes() throws IOException {
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                 DataOutputStream dos = new DataOutputStream(bos)) {
                dos.writeUTF(senderNodeId);
                dos.writeUTF(filePath);
                dos.writeUTF(type.name()); // Use name() for enum serialization
                dos.writeInt(payload != null ? payload.length : 0);
                if (payload != null && payload.length > 0) { // Check length too
                    dos.write(payload);
                }
                return bos.toByteArray();
            }
        }
    }


    public static class MulticastMessageParser { // Made static
        public static MulticastMessage fromBytes(byte[] data, int length) throws IOException {
            // Check for minimal possible length to avoid EOFExceptions prematurely
            // Smallest possible: UTF(senderId_min) + UTF(filePath_min) + UTF(type_min_name) + Int(payloadLength=0)
            // This check is a bit heuristic, robust parsing would be more involved.
            if (length < 10) { // Heuristic minimum length
                throw new IOException("Data array too short to be a valid MulticastMessage.");
            }

            try (ByteArrayInputStream bis = new ByteArrayInputStream(data, 0, length);
                 DataInputStream dis = new DataInputStream(bis)) {
                String senderNodeId = dis.readUTF();
                String filePath = dis.readUTF();
                MulticastMessageType type = MulticastMessageType.valueOf(dis.readUTF());
                int payloadLength = dis.readInt();
                byte[] payload = null;
                if (payloadLength > 0) {
                    if (dis.available() < payloadLength) {
                        throw new IOException("Declared payload length " + payloadLength + " is greater than available bytes " + dis.available());
                    }
                    payload = new byte[payloadLength];
                    dis.readFully(payload);
                } else if (payloadLength < 0) {
                    throw new IOException("Invalid payload length: " + payloadLength);
                }
               return new MulticastMessage(senderNodeId, filePath, type, payload);
            } catch (IllegalArgumentException e) {
               throw new IOException("Failed to parse MulticastMessageType: " + e.getMessage(), e);
            } catch (EOFException e) {
                throw new IOException("Reached end of stream prematurely while parsing MulticastMessage. Data length: " + length, e);
            }
        }
    }


    private void scheduleTask() {
        // Calculate next 12 AM
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
                    multicastGroupAddress = InetAddress.getByName(MULTICAST_ADDRESS);
                    multicastListenSocket = new MulticastSocket(MULTICAST_PORT); // Bind to the port for listening
                    multicastListenSocket.joinGroup(multicastGroupAddress);
                    System.out.println("Node " + id + ": Joined multicast group " + MULTICAST_ADDRESS + ":" + MULTICAST_PORT);

                    Thread multicastListenerThread = new Thread(NodeImp.this::listenForMulticastMessages);
                    multicastListenerThread.setDaemon(true);
                    multicastListenerThread.start();

                } catch (IOException e) {
                    System.err.println("Node " + id + ": Multicast setup error: " + e.getMessage());
                }

                // Reschedule for next day
                scheduleTask();

                // Cancel current timer and create a new one
                timer.cancel();
                timer = new Timer();
            }
        }, firstRun);
    }
}

