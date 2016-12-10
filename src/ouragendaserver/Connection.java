package ouragendaserver;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;

/**
 *
 * @author fbeneditovm
 */
public class Connection extends Thread{//Gerencia a conecção com um cliente específico
    DataInputStream in;
    DataOutputStream out;
    Socket clientSocket;//O socket de cliente do cliente
    int port;//A porta de servidor do cliente conectado
    
    public Connection(Socket aClientSocket){
        try{
            clientSocket = aClientSocket;
            in = new DataInputStream(clientSocket.getInputStream());//Recebe de dados do cliente
            out = new DataOutputStream(clientSocket.getOutputStream());//Envia dados ao cliente
            this.start();
        }catch(IOException e){System.out.println("Connection: "+e.getMessage());}
    }
    
    public void run(){
        try{    
            String buffer;
            
            //Esse laço fica ativo enquanto o cliente está online
            while(!clientSocket.isClosed()){
                buffer = in.readUTF();
                if(buffer.equalsIgnoreCase("exit"))
                    break;
                out.writeUTF(buffer);
            }
            
        }catch(EOFException e){System.out.println("EOF: "+e.getMessage());
        }catch(IOException e){System.out.println("IO: "+e.getMessage());}
    }
    
}