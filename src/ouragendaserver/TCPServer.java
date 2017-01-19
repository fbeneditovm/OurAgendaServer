package ouragendaserver;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 *
 * @author fbeneditovm
 */
public class TCPServer {
    private int port;//Ther server Port
    
    public TCPServer(int aPort){
        port = aPort;
    }
    public boolean startServer(){//Start the server
        boolean done = true;
        try{
            ServerSocket listenSocket = new ServerSocket(port);
            System.out.println("Server created on port: "+port);
            while(true){
                //Aceita a conecção de um novo cliente
                Socket clientSocket = listenSocket.accept();
                new Connection(clientSocket);
                System.out.println("new client connected");
            }
            
        }catch(IOException e){System.out.println("Listen: "+e.getMessage()); done = false;}
        return done;
    }        
}