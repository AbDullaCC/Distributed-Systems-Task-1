// NodeClient.java
import java.rmi.Naming;
import java.util.ArrayList; // For creating dummy FileMeta
import java.util.List;

public class NodeClient {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java NodeClient <nodeId_to_connect> <port>");
            System.err.println("Example: java NodeClient node1 1099");
            System.exit(1);
        }

        String nodeId = args[0];
        int port = 1099;
        try {
            port = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            System.err.println("Invalid port: " + args[1] + ". Using default 1099.");
        }

        String rmiUrl = "//localhost:" + port + "/" + nodeId;

        try {
            System.out.println("Looking up Node: " + rmiUrl);
            NodeInt node = (NodeInt) Naming.lookup(rmiUrl);

            // 1. Ping the node
            System.out.println("Pinging node...");
            boolean isAlive = node.ping();
            System.out.println("Node alive: " + isAlive);

            // 2. Get Node ID
            System.out.println("Getting Node ID from remote object...");
            String remoteNodeId = node.getNodeId();
            System.out.println("Remote Node ID: " + remoteNodeId);

            // 3. Create a file
            System.out.println("\nAttempting to create a file...");
            String department = "development";
            String fileName = "testFile.txt";
            String fileContentStr = "Hello from RMI Client!";
            byte[] fileContentBytes = fileContentStr.getBytes();

            List<String> nodeList = new ArrayList<>();
            nodeList.add(remoteNodeId); // The node it's being created on

            FileMeta meta = new FileMeta(fileName, department, nodeList);
            ActualFile newFile = new ActualFile(meta, fileContentBytes);

            boolean created = node.createFile(newFile);
            if (created) {
                System.out.println("File '" + fileName + "' created successfully in department '" + department + "'.");
                // You can check the node_storage/node1_files/development/ directory
            } else {
                System.out.println("Failed to create file '" + fileName + "'. It might already exist.");
            }

            // 4. Get the file
            System.out.println("\nAttempting to retrieve the file...");
            ActualFile retrievedFile = node.getFile(fileName, department);
            if (retrievedFile != null) {
                System.out.println("Retrieved file: " + retrievedFile.meta.name);
                System.out.println("Content: " + new String(retrievedFile.content));
            } else {
                System.out.println("Could not retrieve file '" + fileName + "'.");
            }

            // 5. Try to create it again (should fail or be handled by server)
            System.out.println("\nAttempting to create the same file again...");
            created = node.createFile(newFile);
            System.out.println("Second creation attempt success: " + created);


            // 6. Update the file
            System.out.println("\nAttempting to update the file...");
            String updatedContentStr = "Hello again, this is updated content!";
            byte[] updatedContentBytes = updatedContentStr.getBytes();
            ActualFile updatedFile = new ActualFile(meta, updatedContentBytes); // Re-use meta, new content

            boolean updated = node.updateFile(updatedFile);
            if (updated) {
                System.out.println("File '" + fileName + "' updated successfully.");
                ActualFile fetchedAfterUpdate = node.getFile(fileName, department);
                if (fetchedAfterUpdate != null) {
                    System.out.println("Content after update: " + new String(fetchedAfterUpdate.content));
                }
            } else {
                System.out.println("Failed to update file '" + fileName + "'.");
            }


            // 7. Delete the file
            System.out.println("\nAttempting to delete the file...");
            boolean deleted = node.deleteFile(fileName, department);
            if (deleted) {
                System.out.println("File '" + fileName + "' deleted successfully.");
            } else {
                System.out.println("Failed to delete file '" + fileName + "'.");
            }

            // 8. Try to get the deleted file
            System.out.println("\nAttempting to retrieve the deleted file...");
            ActualFile nonExistentFile = node.getFile(fileName, department);
            if (nonExistentFile == null) {
                System.out.println("File '" + fileName + "' not found after deletion (as expected).");
            } else {
                System.out.println("Error: File found after deletion!");
            }


        } catch (Exception e) {
            System.err.println("NodeClient exception:");
            e.printStackTrace();
        }
    }
}