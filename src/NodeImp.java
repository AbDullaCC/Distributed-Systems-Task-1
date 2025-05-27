
import java.io.*;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.AlreadyBoundException;
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

    private String getFileMapKey(String dep, String name) {
        return dep + File.separator + name;
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
        return true;
    }

    @Override
    public synchronized boolean createFile(String dep, String name, int port) throws RemoteException {
        String fileKey = getFileMapKey(dep, name);
        String filePath = getFileSystemPath(dep, name);

        File file = new File(filePath);
        if (file.exists()) {
            System.out.println("Node " + id + ": File " + fileKey + " already exists. Cannot create.");
            return false;
        }

        try (ServerSocket serverSocket = new ServerSocket(port);
             Socket clientSocket = serverSocket.accept();
             InputStream in = clientSocket.getInputStream();
             FileOutputStream fos = new FileOutputStream(file)) {

            System.out.println("Node " + id + ": Receiving file " + fileKey + " on port " + port);

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }

            System.out.println("Node " + id + ": File " + fileKey + " created successfully.");
            return true;

        } catch (IOException e) {
            System.err.println("Node " + id + ": Error creating file " + fileKey + ": " + e.getMessage());
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
    public synchronized boolean updateFile(String dep, String name, int port) throws RemoteException {
        String fileKey = getFileMapKey(dep, name);
        String filePath = getFileSystemPath(dep, name);

        System.out.println("Node " + id + ": Waiting to receive updated file: " + fileKey + " on port " + port);

        try (ServerSocket serverSocket = new ServerSocket(port);
             Socket clientSocket = serverSocket.accept();
             InputStream in = clientSocket.getInputStream();
             FileOutputStream fos = new FileOutputStream(filePath, false)) {

            File directory = new File(this.storageBasePath + File.separator + dep);
            if (!directory.exists()) {
                directory.mkdirs();
            }

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }

            fos.flush();
            System.out.println("Node " + id + ": File " + fileKey + " updated successfully from socket.");


            return true;

        } catch (IOException e) {
            System.err.println("Node " + id + ": Error updating file " + fileKey + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public synchronized boolean deleteFile(String name, String dep) throws RemoteException {
        String fileKey = getFileMapKey(dep, name);
        String filePath = getFileSystemPath(dep, name);

        System.out.println("Node " + id + ": Attempting to delete file: " + fileKey);

       boolean existsOnDisk = Files.exists(Paths.get(filePath));

        if (!existsOnDisk) {
            System.out.println("Node " + id + ": File " + fileKey + " not found in memory or disk.");
            return false;
        }


        boolean deletedFromFileSystem = false;

        try {
            deletedFromFileSystem = Files.deleteIfExists(Paths.get(filePath));
        } catch (IOException e) {
            System.err.println("Node " + id + ": Failed to delete file " + fileKey + ": " + e.getMessage());
            e.printStackTrace();

            return false;
        }

        System.out.println("Node " + id + ": File " + fileKey + " deleted : " + deletedFromFileSystem + ").");
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
            NodeImp node = new NodeImp("Node1");
            LocateRegistry.createRegistry(5000);
            Naming.rebind("rmi://localhost:5000/node1", node);
            System.out.println(node.id + "is running");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
