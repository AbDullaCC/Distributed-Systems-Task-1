import java.rmi.Remote;
import java.rmi.RemoteException;

public interface NodeInt extends Remote {

    /**
     * Creates a new file on this node.
     * The key for storing the file in the internal map could be "dep/name".
     * @param file The ActualFile object containing metadata and content.
     * @return true if the file was created successfully, false otherwise.
     * @throws RemoteException if a remote communication error occurs.
     */
    boolean createFile(ActualFile file) throws RemoteException;

    /**
     * Retrieves a file from this node.
     * @param name The name of the file.
     * @param dep The department the file belongs to.
     * @return The ActualFile object if found, null otherwise.
     * @throws RemoteException if a remote communication error occurs.
     */
    byte[] getFile(String name, String dep) throws RemoteException;

    /**
     * Updates an existing file on this node.
     * The key for identifying the file could be "dep/name" from file.getMeta().
     * @param file The ActualFile object with updated content or metadata.
     * @return true if the file was updated successfully, false otherwise (e.g., file not found).
     * @throws RemoteException if a remote communication error occurs.
     */
    boolean updateFile(ActualFile file) throws RemoteException;

    /**
     * Deletes a file from this node.
     * @param name The name of the file.
     * @param dep The department the file belongs to.
     * @return true if the file was deleted successfully, false otherwise (e.g., file not found).
     * @throws RemoteException if a remote communication error occurs.
     */
    boolean deleteFile(String name, String dep) throws RemoteException;

    /**
     * Clones a file from this node to another target node.
     * This method would typically involve reading the file from the current node
     * and then calling a creation method on the targetNode.
     * @param targetNodeStub The RMI stub of the target node where the file should be cloned.
     * @param filePath The path or identifier (e.g., "dep/name") of the file to clone.
     * @return true if the file was cloned successfully, false otherwise.
     * @throws RemoteException if a remote communication error occurs.
     */
    boolean cloneFile(NodeInt targetNodeStub, String filePath) throws RemoteException;

    /**
     * Gets the unique identifier of this node.
     * @return The node ID.
     * @throws RemoteException if a remote communication error occurs.
     */
    String getNodeId() throws RemoteException;

    /**
     * Checks if a file exists on this node.
     * @param name The name of the file.
     * @param dep The department of the file.
     * @return true if the file exists, false otherwise.
     * @throws RemoteException if a remote communication error occurs.
     */
    boolean fileExists(String name, String dep) throws RemoteException;

    /**
     * Pings the node to check if it's alive.
     * @return true if the node is responsive.
     * @throws RemoteException if a remote communication error occurs (which might indicate it's not alive).
     */
    boolean ping() throws RemoteException;
}