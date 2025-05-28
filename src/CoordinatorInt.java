import javax.naming.ServiceUnavailableException;
import java.net.MalformedURLException;
import java.rmi.NotBoundException;
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

    boolean fileCreate(String token, String ip, int port, String fullName) throws RemoteException, ServiceUnavailableException, InvalidParameterException;

    boolean fileGet(String token, String ip, int port, String name, String dep) throws RemoteException, ServiceUnavailableException, InvalidParameterException;

    boolean fileUpdate(String token, String ip, int port, String fullName) throws RemoteException, ServiceUnavailableException;

    boolean fileDelete(String token, String fullName) throws RemoteException, ServiceUnavailableException;

    void addNode(String id) throws RemoteException, MalformedURLException, NotBoundException;

}
