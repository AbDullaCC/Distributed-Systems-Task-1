import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CoordinatorImp extends UnicastRemoteObject implements CoordinatorInt {

    private static List<String> departments = new ArrayList<>();

    protected CoordinatorImp() throws RemoteException {
        super();
        departments.addAll(Arrays.asList("IT", "HR", "QA", "GRAPHICS", "SALES"));
    }

    @Override
    public String login(String username, String password) throws RemoteException, InvalidParameterException {
        return "Some Token";
    }

    @Override
    public boolean otherActionsAllowed(String token, String department) throws RemoteException {
        return true;
    }

    @Override
    public List<String> getDepartments(String token) throws RemoteException, InvalidParameterException {
        this.checkTokenValidity(token);
        return departments;
    }

    @Override
    public List<String> getDepartmentFiles(String token, String department) throws RemoteException, InvalidParameterException {
        this.checkTokenValidity(token);
        return List.of("File_1", "File_2", "File_3", "File_4", "File_5");
    }

    private void checkTokenValidity(String token) throws RemoteException, InvalidParameterException {
        if (!isValidToken(token)) {
            throw new InvalidParameterException("Invalid Token");
        }
    }

    @Override
    public boolean isValidToken(String token) throws RemoteException {
        return token != null;
    }

    public static void main(String[] args) {
        try
        {
            CoordinatorImp coordinator = new CoordinatorImp();
            LocateRegistry.createRegistry(5000);

            Naming.bind("rmi://localhost:5000"+
                    "/coordinator",coordinator);
            System.out.println("coordinator is running");
        }
        catch(Exception e)
        {
            System.out.println(e);
        }
    }
}
