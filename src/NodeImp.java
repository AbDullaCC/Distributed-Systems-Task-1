
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.nio.file.Files;
import java.nio.file.Paths;


public class NodeImp extends UnicastRemoteObject implements NodeInt {
    private String id;

    private final String storageBasePath;
   public NodeImp(String nodeId) throws RemoteException {
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

    @Override
    public boolean cloneFile(String sourceIp, int sourcePort, String fileName, String dep) throws RemoteException {
        File destFile = new File(storageBasePath + File.separator + dep + File.separator + fileName);

        try (Socket socket = new Socket(sourceIp, sourcePort);
             InputStream in = socket.getInputStream();
             FileOutputStream fos = new FileOutputStream(destFile)) {

            byte[] buffer = new byte[4096];
            int bytesRead;

            while ((bytesRead = in.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }

            System.out.println("File cloned from " + sourceIp + ":" + sourcePort + " to " + destFile.getAbsolutePath());
            return true;

        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Failed to clone file from " + sourceIp + ":" + sourcePort);
            return false;
        }
    }

    public static void main(String[] args) throws RemoteException {
        try {
            NodeImp node = new NodeImp("node_1");

            Naming.rebind("rmi://localhost:5000/node_1", node);
            CoordinatorInt coordinator =(CoordinatorInt) Naming.lookup("rmi://localhost:5000/coordinator");
            coordinator.addNode(node.id);
            System.out.println(node.id + "is running");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
