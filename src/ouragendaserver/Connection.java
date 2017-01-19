package ouragendaserver;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
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
public class Connection extends Thread{//A connection with one client
    DataInputStream in;
    DataOutputStream out;
    Socket clientSocket;//The socket to communicate with the client
    long user_id;
    int port;//The client's server port
    DBConnection postgresql;
    
    public Connection(Socket aClientSocket){
        postgresql = new DBConnection("OurAgenda", "fbeneditovm", "");
        try{
            clientSocket = aClientSocket;
            in = new DataInputStream(clientSocket.getInputStream());//Receive data from the client
            out = new DataOutputStream(clientSocket.getOutputStream());//Sends data to the client
            this.start();
        }catch(IOException e){System.out.println("Connection: "+e.getMessage());}
    }
    
    public void run(){
        String buffer;
        int i;
        boolean requestResult = false;
        boolean loginSuccess = false;
        /*          //NAO EXCLUIR
        for(i=0; i<5;i++){
            //Tries to execute the login 5 times
            if(!login()){//Login Fail
                try {
                    out.writeUTF("LOGIN_FB&-status=FAIL");
                } catch (IOException ex) {
                    Logger.getLogger(Connection.class.getName()).log(Level.SEVERE, null, ex);
                }
                continue;
            }
            loginSuccess = true;
            try {//Login Success
                out.writeUTF("LOGIN_FB&-status=SUCCESS");
                break;
            } catch (IOException ex) {
                Logger.getLogger(Connection.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
        if(!loginSuccess)
            return;
        */
        try{//Esse laço fica ativo enquanto o cliente está online
            while(!clientSocket.isClosed()){
                buffer = in.readUTF();
                if(buffer.equalsIgnoreCase("exit"))
                    break;
                //out.writeUTF(buffer);
                String[] section = buffer.split("&");
                if(section.length<2)
                    out.writeUTF("ERROR_FB&-rcvd="+buffer);
                switch(section[0]){
                    default:
                        out.writeUTF("ERROR_FB&-rcvd="+buffer);
                        break;
                    case "CREATE_USER":
                        try {
                            requestResult = createUser(buffer);
                        } catch (PasswordStorage.CannotPerformOperationException ex) {
                            Logger.getLogger(Connection.class.getName()).log(Level.SEVERE, null, ex);
                        }
                        out.writeUTF(
                                ((requestResult) ? "CREATE_USER_FB&-status=SUCCESS" 
                                : "CREATE_USER_FB&-status=FAIL"));
                        break;
                    case "CREATE_EVENT":
                        requestResult = createEvent(buffer);
                        out.writeUTF(
                                ((requestResult) ? "CREATE_EVENT_FB&-status=SUCCESS" 
                                : "CREATE_EVENT_FB&-status=FAIL"));
                        break;
                    case "SHOW_EVENTS":
                        showEvent();
                        break;
                }
                
            }
        }catch(EOFException e){System.out.println("EOF: "+e.getMessage());
        }catch(IOException e){System.out.println("IO: "+e.getMessage());}
    }
    
    private boolean createUser(String message) throws PasswordStorage.CannotPerformOperationException, IOException{
        String username = null;
        String password = null;
        
        String[] section = message.split("&");
        if(section.length!=3)
            return false;
        if(!section[1].substring(0, 10).equalsIgnoreCase("-username=")){
            System.out.println("param '-username=' not found!");
            return false;
        }
        else 
            username = section[1].substring(10);
        if(!section[2].substring(0, 10).equalsIgnoreCase("-password=")){
            System.out.println("param '-password=' not found!");
            return false;
        }
        else
            password = section[2].substring(10);
        
        String strurl = "http://ouragenda.000webhostapp.com/insertuser.php?username="+username+"&password="+password;
        
        return insertToWeDB(strurl);
    }
    
    private boolean insertToWeDB(String strurl) throws PasswordStorage.CannotPerformOperationException, MalformedURLException, IOException {
        URL url = new URL(strurl);
        
        String htmlresponse = null;
        String line;
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        if( conn.getResponseCode() == HttpURLConnection.HTTP_OK ){
            InputStream is = conn.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            while ((line = reader.readLine()) != null){
                htmlresponse += line;
            }

            reader.close();
        }else{
            System.out.println("Error");
            InputStream is = conn.getErrorStream();
            if(is!=null){
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                while ((line = reader.readLine()) != null){
                    htmlresponse += line;
                }
            }
        } 
        System.out.println("\n\n\nFeedback:");
        String fb = htmlresponse.substring(htmlresponse.indexOf("BEGINFB")+7, htmlresponse.indexOf("ENDFB"));
        System.out.println(fb);
        return fb.equalsIgnoreCase("true");
    }
    
    private  boolean createEvent(String message){
        String eventName = null;
        String timestamp = null;
        String local = null;
        String desc = null;
        String sql = null;
        String sql2 = null;
        
        String[] section = message.split("&");
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
        timestamp = section[2].substring(11);
        
        if(section.length>3){
            if(section[3].substring(0, 7).equalsIgnoreCase("-local=")){
                local = section[3].substring(7);
                if(section.length>4){
                    if(section[4].substring(0, 6).equalsIgnoreCase("-desc=")){
                        desc = section[4].substring(6);
                        sql = "INSERT INTO \"public\".\"Event\" (event_name, "
                            + "timestamp, owner_id, local, description) "
                            + "VALUES (\'"+eventName+"\', TIMESTAMP WITH TIME ZONE "
                            + "\'"+timestamp+"\', "+user_id+", \'"+local+"\', \'"+desc+"\')";
                    }else//if there are 4 parameters and the 4th is not desc
                        return false;
                }else{//if there are 3 parameters and the 3 is local
                    sql = "INSERT INTO \"public\".\"Event\" (event_name, "
                        + "timestamp, owner_id, local) VALUES "
                        + "(\'"+eventName+"\', TIMESTAMP WITH TIME ZONE \'"
                        +timestamp+"\', "+user_id+", \'"+local+"\')";
                }
            }else{//if there are 3 parameters and the 3rd is not local
                if(section[3].substring(0, 6).equalsIgnoreCase("-desc=")){
                    desc = section[3].substring(6);
                    sql = "INSERT INTO \"public\".\"Event\" (event_name, "
                        + "timestamp, owner_id, description) VALUES "
                        + "(\'"+eventName+"\', TIMESTAMP WITH TIME ZONE \'"
                        +timestamp+"\', "+user_id+",  \'"+desc+"\')";
                }else
                    return false;
            }
        }else{//if there are only 2 parameters (name and timestamp)
            sql = "INSERT INTO \"public\".\"Event\" (event_name, timestamp, "
                + "owner_id) VALUES (\'"+eventName+"\', "
                + "TIMESTAMP WITH TIME ZONE \'"+timestamp+"\', "+ user_id+")";
        }
        
        if(!postgresql.isConnectionActive())
            postgresql.connectToDB();
        long eventId = postgresql.processInsert(sql, "event_id");
        System.out.println("New event created id="+eventId);
        
        sql2 = "INSERT INTO \"public\".\"Event_User\" (event_id, user_id)"
                + "VALUES ("+eventId+", "+user_id+")";
        postgresql.processInsert(sql2, "user_id");
        System.out.println("New Event_User created event_id="+eventId+
                "\n user_id="+user_id);
        return true; 
    }
    
    private boolean login(){
        String buffer = null;
        String userName = null;
        String password = null;
        
        
        try { //Test the login message and get the user information
            buffer = in.readUTF();
            String[] section = buffer.split("&");
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
        LinkedList<Map<String, String>> queryResult = postgresql.processSelectQuery(sql, columnName);
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
    
    private boolean showEvent(){

        if(!postgresql.isConnectionActive())
            postgresql.connectToDB();

        HashSet<String> columnName = new HashSet<>();
        columnName.add("event_name");
        columnName.add("timestamp");
        columnName.add("local");
        columnName.add("description");
        
        String sql = "SELECT evt.\"event_name\", evt.\"timestamp\", evt.\"local\", evt.\"description\""
                + "FROM \"public\".\"Event\" as evt, \"public\".\"Event_User\" as eu "
                + "WHERE evt.\"event_id\" = eu.\"event_id\" AND eu.\"user_id\" = '"+user_id+"'";

        LinkedList<Map<String, String>> queryResult = postgresql.processSelectQuery(sql, columnName);

        if(queryResult.size()<=0){
            System.out.println("Event not found!");
            return false;
        }   

        try {
            out.writeUTF(queryResult.toString());
        } catch (IOException ex) {
            Logger.getLogger(Connection.class.getName()).log(Level.SEVERE, null, ex);
        }

        return true;
    }
    /*
    private boolean checkAvailability(long user, String timestamp){
        
        int maxMinutes, minMinutes;
        
        if(!postgresql.isConnectionActive())
            postgresql.connectToDB();
        
        String[] section = timestamp.split(":");
        maxMinutes = Integer.parseInt(section[1])+10;
        minMinutes = maxMinutes-20;//-----precisa levar em conta que os minutos variam entre 0-60--------
        
        HashSet<String> columnName = new HashSet<>();
        
        columnName.add("timestamp");
        
        String sql = "SELECT evt.\"timestamp\" "
                + "FROM \"public\".\"Event\" as evt, \"public\".\"Event_User\" as eu "
                + "WHERE evt.\"event_id\" = eu.\"event_id\" AND eu.\"user_id\" = '"+user+"' "
                + "AND ";
    }
    */
}