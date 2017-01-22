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
        try{//Esse laço fica ativo enquanto o cliente está online
            while(!clientSocket.isClosed()){
                buffer = in.readUTF();
                if(buffer.equalsIgnoreCase("exit"))
                    break;
                //out.writeUTF(buffer);
                String[] section = buffer.split("&");
                if(section.length<2)
                    out.writeUTF("ERROR_FB&-rcvd="+buffer);
                switch(section[0].toUpperCase()){
                    default:
                        out.writeUTF("ERROR_FB&-rcvd="+buffer);
                        break;
                    case "CREATE_USER":
                        try {
                            requestResult = createUser(buffer);
                        } catch (PasswordStorage.CannotPerformOperationException ex) {
                            Logger.getLogger(Connection.class.getName()).log(Level.SEVERE, null, ex);
                        }
                        out.writeUTF((requestResult) ? "CREATE_USER_FB&-status=SUCCESS" 
                                : "CREATE_USER_FB&-status=FAIL");
                        break;
                    case "CREATE_EVENT":
                        requestResult = createEvent(buffer);
                        out.writeUTF((requestResult) ? "CREATE_EVENT_FB&-status=SUCCESS" 
                                : "CREATE_EVENT_FB&-status=FAIL");
                        break;
                    case "SHOW_EVENTS":
                        LinkedList<String> listResult = showEvents(buffer);
                        if(listResult==null)
                            out.writeUTF("SHOW_EVENTS_FB&-status=FAIL");
                        else{
                            out.writeUTF("SHOW_EVENTS_FB&-status=SUCCESS&-n_events="+listResult.size());
                            for(String resulti:listResult)
                                out.writeUTF(resulti);
                        }
                        break;
                    case "CHECK_AVAILABLE":
                        out.writeUTF("CHECK_AVAILABLE_FB"+
                                checkAvailable(buffer));
                        break;
                    case "GET_BUSY_TIMES":
                        out.writeUTF("GET_BUSY_TIMES_FB"
                                +getBusyTimes(buffer));
                        break;
                }
                
            }
        }catch(EOFException e){System.out.println("EOF: "+e.getMessage());
        }catch(IOException e){System.out.println("IO: "+e.getMessage());}
    }
    
    private boolean createUser(String message) throws PasswordStorage.CannotPerformOperationException{
        String username = null;
        String password = null;
        
        String[] section = message.split("&");
        if(section.length!=3)
            return false;
        if(!section[1].substring(0, 3).equalsIgnoreCase("-u=")){
            System.out.println("param '-u=' not found!");
            return false;
        }
        else 
            username = section[1].substring(3);
        if(!section[2].substring(0, 3).equalsIgnoreCase("-p=")){
            System.out.println("param '-p=' not found!");
            return false;
        }
        else
            password = section[2].substring(3);
        
        //String strurl = "http://ouragenda.000webhostapp.com/insertuser.php?username="+username+"&password="+password;
        //return insertToWeDB(strurl);
        
        String sql = "INSERT INTO \"public\".\"User\" (user_name, user_password_hash) "
                + " VALUES (\'"+username+"\',"
                + "\'"+PasswordStorage.createHash(password)+"\')";
        if(!postgresql.isConnectionActive())
            postgresql.connectToDB();
        long userId = postgresql.processInsert(sql, "user_id");
        System.out.println("New user created id="+userId);
        return true;
    }
    
    private String checkAvailable(String message){
        String[] section = message.split("&");
        String guest, begin, end;
        if(section.length!=4){
            System.out.println("Invalid number of arguments");
            return "&-status=FAIL";
        }
        if(!section[1].substring(0, 7).equalsIgnoreCase("-guest=")){
            System.out.println("param '-guest=' not found!");
            return "&-status=FAIL";
        }
        guest = section[1].substring(7);
        
        if(!section[2].substring(0, 7).equalsIgnoreCase("-begin=")){
            System.out.println("param '-begin=' not found!");
            return "&-status=FAIL";
        }
        begin = section[2].substring(7);
        
        if(!section[3].substring(0, 5).equalsIgnoreCase("-end=")){
            System.out.println("param '-end=' not found!");
            return "&-status=FAIL";
        }
        end = section[3].substring(5);
        
        String toReturn = "&-status=SUCCESS";
        
        toReturn += (checkAvailability(guest, begin, end)) ? "&-available=TRUE"
                : "&-available=FALSE";
        
        return toReturn;
    }
    
    private String getBusyTimes(String message){
        String[] section = message.split("&");
        String guest, date;
        String toReturn;
        if(section.length!=3){
            System.out.println("Invalid number of arguments");
            toReturn = "&-status=FAIL";
            return toReturn;
        }
        if(!section[1].substring(0, 7).equalsIgnoreCase("-guest=")){
            System.out.println("param '-guest=' not found!");
            toReturn = "&-status=FAIL";
            return toReturn;
        }
        guest = section[1].substring(7);
        
        if(!section[2].substring(0, 6).equalsIgnoreCase("-date=")){
            System.out.println("param '-begin=' not found!");
            toReturn = "&-status=FAIL";
            return toReturn;
        }
        date = section[2].substring(6);
        
        toReturn = "&-status=SUCCESS&-events=";
        LinkedList<String> busyTimes = getUserBusyTimes(guest, date);
        
        if(busyTimes.size()<=0){
            toReturn += "NO_EVENTS";
            return toReturn;
        }
        
        for(int i=0; i<busyTimes.size(); i++){
            toReturn += (i+1)+"("+busyTimes.get(i)+"),";
        }
        toReturn = toReturn.substring(0, toReturn.length()-1);//Removes the last coma
        return toReturn;
    }
    
    private  boolean createEvent(String message){
        String eventName = null;
        String begin = null;
        String end = null;
        String local = null;
        String desc = null;
        String sql = null;
        String sql2 = null;
        
        String[] section = message.split("&");
        if(section.length<4 || section.length>6){
            System.out.println("Invalid number of arguments");
            return false;
        }
        
        if(!section[1].substring(0, 6).equalsIgnoreCase("-name=")){
            System.out.println("param '-name=' not found!");
            return false;
        }
        eventName = section[1].substring(6);
        
        if(!section[2].substring(0, 7).equalsIgnoreCase("-begin=")){
            System.out.println("param '-begin=' not found!");
            return false;
        }
        begin = section[2].substring(7);
        
        if(!section[3].substring(0, 5).equalsIgnoreCase("-end=")){
            System.out.println("param '-end' not found!");
            return false;
        }
        end = section[3].substring(5);
        
        if(section.length>4){
            if(section[4].substring(0, 7).equalsIgnoreCase("-local=")){
                local = section[4].substring(7);
                if(section.length>5){
                    if(section[5].substring(0, 6).equalsIgnoreCase("-desc=")){
                        desc = section[5].substring(6);
                        sql = "INSERT INTO \"public\".\"Event\" (event_name, "
                            + "begin_timestamp, end_timestamp, owner_id, local, description) "
                            + "VALUES (\'"+eventName+"\', TIMESTAMP WITH TIME ZONE "
                            + "\'"+begin+"\', TIMESTAMP WITH TIME ZONE "
                            + "\'"+end+"\', "+user_id+", \'"+local+"\', \'"+desc+"\')";
                    }else{//if there are 5 parameters and the 5th is not desc
                        System.out.println("5 param and no '-desc'");
                        return false;
                    }
                }else{//if there are 4 parameters and the 4th is local
                    sql = "INSERT INTO \"public\".\"Event\" (event_name, "
                        + "begin_timestamp, end_timestamp, owner_id, local) VALUES "
                        + "(\'"+eventName+"\', TIMESTAMP WITH TIME ZONE "
                        + "\'"+begin+"\', TIMESTAMP WITH TIME ZONE "
                        + "\'"+end+"\', "+user_id+", \'"+local+"\')";
                }
            }else{//if there are 4 parameters and the 4th is not local
                if(section[4].substring(0, 6).equalsIgnoreCase("-desc=")){
                    desc = section[4].substring(6);
                    sql = "INSERT INTO \"public\".\"Event\" (event_name, "
                        + "begin_timestamp, end_timestamp, owner_id, description) VALUES "
                        + "(\'"+eventName+"\', TIMESTAMP WITH TIME ZONE "
                        + "\'"+begin+"\', TIMESTAMP WITH TIME ZONE "
                        + "\'"+end+"\', "+user_id+",  \'"+desc+"\')";
                }else{
                    System.out.println("4 param and no local or desc");
                    return false;
                }
            }
        }else{//if there are only 3 parameters (name and timestamps)
            sql = "INSERT INTO \"public\".\"Event\" (event_name, begin_timestamp, "
                + "end_timestamp, owner_id) VALUES (\'"+eventName+"\', "
                + "TIMESTAMP WITH TIME ZONE \'"+begin+"\', "
                + "TIMESTAMP WITH TIME ZONE \'"+end+"\', "+ user_id+")";
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
    
    private boolean deleteEvent(String message){
        String[] section = message.split("&");
        if(section.length!=2){
            System.out.println("Invalid number of arguments");
            return false;
        }
        
        if(!section[1].substring(0, 10).equalsIgnoreCase("-event_id=")){
            System.out.println("param '-event_id=' not found!");
            return false;
        }
        String eventid = section[1].substring(10);
        
        //First we must test if there is an event owned by the current user
        //with the given event_id
        String sql = "SELECT evt.\"event_name\""
                + "FROM \"public\".\"Event\" as evt, \"public\".\"Event_User\" as eu "
                + "WHERE evt.\"event_id\" = \'"+eventid+"\' AND "
                + "evt.\"event_id\" = eu.\"event_id\" AND eu.\"user_id\" = \'"+user_id+"\'";
        
        if(!postgresql.isConnectionActive())
            postgresql.connectToDB();

        HashSet<String> columnName = new HashSet<>();
        columnName.add("event_name");
        
        LinkedList<Map<String, String>> queryResult = postgresql.processSelectQuery(sql, columnName);
        
        if(queryResult.size()<=0){
            System.out.println("Event not found!");
            return false;
        }
        
        sql = "DELETE FROM \"public\".\"Event\" as evt"
           + "WHERE evt.\"event_id\" = \'"+eventid+"\'";
        
        int nRowsUpdated = postgresql.processUpdate(sql);
        System.out.println(""+nRowsUpdated+" rows updated!");
        
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
    
    private LinkedList<String> showEvents(String message){
        String date;
        
        String[] section = message.split("&");
        if(section.length!=2){
            System.out.println("Invalid number of arguments");
            return null;
        }
        
        if(!section[1].substring(0, 6).equalsIgnoreCase("-date=")){
            System.out.println("param '-date=' not found!");
            return null;
        }
        date = section[1].substring(6);
        String sql = "";
        if(date.equalsIgnoreCase("ALL")){
            sql = "SELECT evt.\"event_name\", evt.\"begin_timestamp\", "
                + "evt.\"end_timestamp\", evt.\"local\", evt.\"description\""
                + "FROM \"public\".\"Event\" as evt, \"public\".\"Event_User\" as eu "
                + "WHERE evt.\"event_id\" = eu.\"event_id\" AND eu.\"user_id\" = \'"+user_id+"\'";
        }else{
            sql = "SELECT evt.\"event_name\", evt.\"begin_timestamp\", "
                + "evt.\"end_timestamp\", evt.\"local\", evt.\"description\""
                + "FROM \"public\".\"Event\" as evt, \"public\".\"Event_User\" as eu "
                + "WHERE evt.\"event_id\" = eu.\"event_id\" AND "
                + "eu.\"user_id\" = \'"+user_id+"\' AND begin_timestamp::date = \'"+date+"\'";
        }

        if(!postgresql.isConnectionActive())
            postgresql.connectToDB();

        HashSet<String> columnName = new HashSet<>();
        columnName.add("event_name");
        columnName.add("begin_timestamp");
        columnName.add("end_timestamp");
        columnName.add("local");
        columnName.add("description");
        
        LinkedList<Map<String, String>> queryResult = postgresql.processSelectQuery(sql, columnName);
        LinkedList<String> toReturn = new LinkedList<>();
        
        if(queryResult.size()<=0){
            System.out.println("Event not found!");
            return toReturn;
        }
        
        for(int i=0; i<queryResult.size(); i++){
            toReturn.add("EVENT&-eventn="+i
                    + "&-event_name="+queryResult.get(i).get("event_name")
                    + "&-begin_timestamp="+queryResult.get(i).get("begin_timestamp")
                    + "&-end_timestamp="+queryResult.get(i).get("end_timestamp")
                    + "&-local="+queryResult.get(i).get("local")
                    + "&-desc="+queryResult.get(i).get("description"));
        }
        
        return toReturn;
    }
    
    private boolean checkAvailability(String user_name, String begin_timestamp, String end_timestamp){
        if(!postgresql.isConnectionActive())
            postgresql.connectToDB();
        
        HashSet<String> columnName = new HashSet<>();
        
        columnName.add("begin_timestamp");
        
        String sql = "SELECT evt.\"begin_timestamp\" "
                + "FROM \"public\".\"Event\" as evt, "
                + "\"public\".\"Event_User\" as eu, \"public\".\"User\" as u "
                + "WHERE u.\"user_name\" = \'"+user_name+"\' AND eu.\"user_id\" = u.\"user_id\" "
                + "AND evt.\"event_id\" = eu.\"event_id\" AND NOT "                          //Excluding the cases when
                + "(evt.begin_timestamp>TIMESTAMP WITH TIME ZONE \'"+end_timestamp+"\' "     //The event starts after the end_timestamp
                + "OR evt.end_timestamp<=TIMESTAMP WITH TIME ZONE \'"+begin_timestamp+"\')"; //or ends before the begin_timestamp
                
        
        LinkedList<Map<String, String>> queryResult = postgresql.processSelectQuery(sql, columnName);

        if(queryResult.size()<=0)//If there is no event between the two given timestamps
            return true;
        return false;
    }
     /**
     * Gets all busy times for that user on the specified date
     * @param user the user_id for the user to consult
     * @param date a string containing a date in the format: YYYY-MM-DD
     * @return a LinkedList of Strings in the format: begin_time&end_time
     */
    private LinkedList<String> getUserBusyTimes(String user_name, String date){
        if(!postgresql.isConnectionActive())
            postgresql.connectToDB();
        
        LinkedList<String> busyTimes = new LinkedList<>();
        HashSet<String> columnName = new HashSet<>();
        
        columnName.add("begin");
        columnName.add("end");
        
        String sql = "SELECT evt.\"begin_timestamp\"::time as begin, evt.\"end_timestamp\"::time as end "
                + "FROM \"public\".\"Event\" as evt, "
                + "\"public\".\"Event_User\" as eu, \"public\".\"User\" as u "
                + "WHERE u.\"user_name\" = \'"+user_name+"\' AND eu.\"user_id\" = u.\"user_id\" "
                + "AND evt.\"event_id\" = eu.\"event_id\" "
                + "AND evt.\"begin_timestamp\"::date = \'"+date+"\'";
        
        LinkedList<Map<String, String>> queryResult = postgresql.processSelectQuery(sql, columnName);
        
        if(queryResult.size()>0){//If there are events on that date
            for(Map<String, String> queryResulti : queryResult){
                String eventTime = new String();
                eventTime = (String)queryResulti.get("begin")+"TO"
                          + (String)queryResulti.get("end");
                busyTimes.add(eventTime);
            }
        }
        return busyTimes;
    }
    
    /*
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
    */
}