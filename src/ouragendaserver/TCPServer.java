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
    public boolean startServer(){//Inicia o server
        boolean done = true;
        try{
            ServerSocket listenSocket = new ServerSocket(port);
            System.out.println("Server Conectado na porta: "+port);
            while(true){
                //Aceita a conecção de um novo cliente
                Socket clientSocket = listenSocket.accept();
                System.out.println("Client socket recebido");
                new Connection(clientSocket);
                System.out.println("connection criado com sucesso");
            }
            
        }catch(IOException e){System.out.println("Listen: "+e.getMessage()); done = false;}
        return done;
    }        
}