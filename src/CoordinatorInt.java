import java.rmi.Remote;
import java.rmi.RemoteException;
import java.security.InvalidParameterException;
import java.util.List;

public interface CoordinatorInt extends Remote {

    boolean isValidToken(String token) throws RemoteException;

    boolean otherActionsAllowed(String token, String department) throws RemoteException;

    String login(String username, String password) throws RemoteException, InvalidParameterException;

    List<String> getDepartments(String token) throws RemoteException, InvalidParameterException;

    List<String> getDepartmentFiles(String token, String department) throws RemoteException, InvalidParameterException;

    boolean createFile(String token, byte[] content, String fullName) throws RemoteException;

    byte[] getFile(String token, String name, String dep) throws RemoteException;

    boolean updateFile(String token, byte[] content, String fullName) throws RemoteException;

    boolean deleteFile(String token, String name, String dep) throws RemoteException;


}
