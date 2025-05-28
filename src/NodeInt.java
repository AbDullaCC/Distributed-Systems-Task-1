import java.rmi.Remote;
import java.rmi.RemoteException;

public interface NodeInt extends Remote {

    boolean createFile(String dep, String name, int port) throws RemoteException;

 void getFile(String socketAddress, int port,String fileName) throws RemoteException;

     boolean updateFile(String dep, String name, int port) throws RemoteException;
    boolean deleteFile(String name, String dep) throws RemoteException;
    boolean cloneFile(String sourceIp, int sourcePort, String fileName, String dep) throws RemoteException;
    String getNodeId() throws RemoteException;

    void syncDeleteFile(String fullName) throws RemoteException;
    void syncFile(String fullName) throws RemoteException;


    boolean ping() throws RemoteException;
}