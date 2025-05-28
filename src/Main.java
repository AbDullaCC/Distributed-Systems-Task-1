import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.rmi.RemoteException;

public class Main {

    public static void main(String[] args) {
        System.out.println("hello world");

// ----------------receiving file as a node from client------------------

//        try(Socket clientSocket = new Socket("localhost", 8000);
//            InputStream clientIn = clientSocket.getInputStream();
//            FileOutputStream fileOut = new FileOutputStream("received.txt");){
//
//            clientIn.transferTo(fileOut);
//
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }

//----------------sending file from node to client-----------------------

//        try (Socket clientSocket = new Socket("localhost", 8000);
//             FileInputStream fileIn = new FileInputStream("test.txt");
//             OutputStream clientOut = clientSocket.getOutputStream()) {
//
//            // Stream file directly to client
//            fileIn.transferTo(clientOut);  // Java 9+
//
//        } catch (IOException e) {
//            System.out.println(e.getMessage());
//        }
    }
}