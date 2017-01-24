package ouragendaserver;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Iterator;

/**
 *
 * @author fbeneditovm
 */
public class TCPServer implements Server {
    private int port;//Ther server Port
    private HashMap<Long, Connection> clientList;
    
    public TCPServer(int aPort){
        clientList = new HashMap<>();
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
                new TCPConnection(clientSocket, this);
            }
            
        }catch(IOException e){System.out.println("Listen: "+e.getMessage()); done = false;}
        return done;
    }
    
    @Override
    public void userLogin(long userid, Connection connection){
        clientList.put(userid, connection);
        System.out.println("new client connected, user_id: "+userid);
    }
    
    @Override
    public void userLogout(long userid){
        clientList.remove(userid);
        System.out.println("Client disconnected, user_id: "+userid);
    }
    
    @Override
    public boolean isUserOnline(long userid){
        return clientList.containsKey(userid);
    }
    
    @Override
    public void notifyUser(long userid){
        clientList.get(userid).askToCheckNotifications();
    }
}