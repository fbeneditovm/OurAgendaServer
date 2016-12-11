package ouragendaserver;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import util.DBConnection;
import util.PasswordStorage;

/**
 *
 * @author fbeneditovm
 */
public class Connection extends Thread{//Gerencia a conecção com um cliente específico
    DataInputStream in;
    DataOutputStream out;
    Socket clientSocket;//O socket de cliente do cliente
    long user_id;
    int port;//A porta de servidor do cliente conectado
    DBConnection postgresql;
    
    public Connection(Socket aClientSocket){
        postgresql = new DBConnection("OurAgenda", "fbeneditovm", "");
        try{
            clientSocket = aClientSocket;
            in = new DataInputStream(clientSocket.getInputStream());//Recebe de dados do cliente
            out = new DataOutputStream(clientSocket.getOutputStream());//Envia dados ao cliente
            this.start();
        }catch(IOException e){System.out.println("Connection: "+e.getMessage());}
    }
    
    public void run(){
        String buffer;
        int i;
        boolean requestResult = false;
        boolean loginSuccess = false;
        
        for(i=0; i<5;i++){
            //Tries to execute the login 5 times
            if(!login()){//Login Fail
                try {
                    out.writeUTF("LOGIN_FB|-status=FAIL");
                } catch (IOException ex) {
                    Logger.getLogger(Connection.class.getName()).log(Level.SEVERE, null, ex);
                }
                continue;
            }
            loginSuccess = true;
            try {//Login Success
                out.writeUTF("LOGIN_FB|-status=SUCCESS");
                break;
            } catch (IOException ex) {
                Logger.getLogger(Connection.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
        if(!loginSuccess)
            return;
        
        try{//Esse laço fica ativo enquanto o cliente está online
            while(!clientSocket.isClosed()){
                buffer = in.readUTF();
                if(buffer.equalsIgnoreCase("exit"))
                    break;
                //out.writeUTF(buffer);
                String[] section = buffer.split("|");
                if(section.length<2)
                    out.writeUTF("ERROR_FB|-rcvd="+buffer);
                switch(section[0]){
                    default:
                        out.writeUTF("ERROR_FB|-rcvd="+buffer);
                        break;
                    case "CREATE_EVENT":
                        requestResult = createEvent(buffer);
                        out.writeUTF(
                                ((requestResult) ? "CREATE_EVENT_FB|-status=SUCCESS" 
                                : "CREATE_EVENT_FB|-status=FAIL"));
                        break;
                        
                }
                
            }
        }catch(EOFException e){System.out.println("EOF: "+e.getMessage());
        }catch(IOException e){System.out.println("IO: "+e.getMessage());}
    }
    
    private  boolean createEvent(String message){
        String eventName = null;
        String timestamp = null;
        String local = null;
        String desc = null;
        String sql = null;
        
        String[] section = message.split("|");
        if(section.length<3 || section.length>5)
            return false;
        if(!section[1].substring(0, 6).equalsIgnoreCase("-name=")){
            System.out.println("param '-name=' not found!");
            return false;
        }
        eventName = section[1].substring(6);
        if(!section[2].substring(0, 11).equalsIgnoreCase("-timestamp=")){
            System.out.println("param '-timestamp=' not found!");
            return false;
        }
        eventName = section[2].substring(11);
        
        if(section.length>3){
            if(!section[3].substring(0, 7).equalsIgnoreCase("-local=")){
                System.out.println("param '-local=' not found!");
                return false;
            }
            local = section[3].substring(7);
            if(section.length>4){
                if(!section[4].substring(0, 6).equalsIgnoreCase("-desc=")){
                    System.out.println("param '-desc=' not found!");
                    return false;
                }
                local = section[4].substring(6);
            }
        }
        switch(section.length){
            case 3:
                sql = "INSERT INTO Event (event_name, timestamp)"
                        + "VALUES (\""+eventName+"\", \""+timestamp+"\")";
                break;
            case 4:
                sql = "INSERT INTO Event (event_name, timestamp)"
                        + "VALUES (\""+eventName+"\", \""+timestamp+"\", \""
                        +local+"\")";
                break;
            case 5:
                sql = "INSERT INTO Event (event_name, timestamp)"
                        + "VALUES (\""+eventName+"\", \""+timestamp+"\", \""
                        +local+"\", \""+desc+"\")";
                break;
        }
        if(!postgresql.isConnectionActive())
            postgresql.connectToDB();
        long eventId = postgresql.processInsert(sql, "event_id");
        return true; 
    }
    
    private boolean login(){
        String buffer = null;
        String userName = null;
        String password = null;
        
        
        try { //Test the login message and get the user information
            buffer = in.readUTF();
            String[] section = buffer.split(":");
            if(!(section[0].equalsIgnoreCase("login"))){
                System.out.println("Not a login operation!");
                return false;
            }
            if(!(section.length==3)){
                System.out.println("Invalid number of arguments!");
                return false;
            }
            if(!section[1].substring(0, 3).equalsIgnoreCase("-u=")){
                System.out.println("param '-u=' not found!");
                return false;
            }
            userName = section[1].substring(3);
            if(!section[2].substring(0, 3).equalsIgnoreCase("-p=")){
                System.out.println("param '-p=' not found!");
                return false;
            }
            password = section[2].substring(3);
        } catch (IOException ex) {
            Logger.getLogger(Connection.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
        
        //Get the user information at the database
        if(!postgresql.isConnectionActive())
            postgresql.connectToDB();
        HashSet<String> columnName = new HashSet<>();
        columnName.add("user_id");
        columnName.add("user_password_hash");
        
        String sql = "SELECT u.\"user_id\", u.\"user_password_hash\" "
                + "FROM \"public\".\"User\" as u "
                + "WHERE u.\"user_name\" = '"+userName+"'";
        LinkedList<Map<String, String>> queryResult = postgresql.processSelectQuerry(sql, columnName);
        if(queryResult.size()<=0){
            System.out.println("User not found!");
            return false;
        }
        
        //Test the user password
        for(Map<String, String> queryResulti : queryResult){
            String str_user_id = queryResulti.get("user_id");
            System.out.println(str_user_id);
            String user_password_hash = queryResulti.get("user_password_hash");
            try {
                if(PasswordStorage.verifyPassword(password, user_password_hash)){
                    user_id = Long.parseLong(str_user_id);
                    return true;
                }
            } catch (PasswordStorage.CannotPerformOperationException ex) {
                Logger.getLogger(Connection.class.getName()).log(Level.SEVERE, null, ex);
            } catch (PasswordStorage.InvalidHashException ex) {
                Logger.getLogger(Connection.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        System.out.println("Invalid password for given user "+userName+" !");
        return false;
    }
}