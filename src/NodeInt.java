import java.rmi.Remote;
import java.rmi.RemoteException;

public interface NodeInt extends Remote {

    boolean createFile(String socketAddress, int port,  String name) throws RemoteException;

 void getFile(String socketAddress, int port,String fileName) throws RemoteException;

    boolean updateFile(String socketAddress, int port,  String name)throws RemoteException;
    boolean deleteFile(String name) throws RemoteException;
     String getNodeId() throws RemoteException;

    void syncDeleteFile(String fullName) throws RemoteException;
    void syncFile(String fullName) throws RemoteException;


    boolean ping() throws RemoteException;
}