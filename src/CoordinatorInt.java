import javax.naming.ServiceUnavailableException;
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

    boolean fileCreate(String token, String group, int port, String fullName) throws RemoteException, ServiceUnavailableException;

    byte[] fileGet(String token, String name, String dep) throws RemoteException;

    boolean fileUpdate(String token, byte[] content, String fullName) throws RemoteException;

    boolean fileDelete(String token, String name, String dep) throws RemoteException;


}
