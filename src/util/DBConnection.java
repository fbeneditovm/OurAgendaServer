/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package util;

import java.sql.*;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author fbeneditovm
 */
public class DBConnection {
    private String dbName;
    private String user;
    private String password;
    private Connection conn;
    
    public DBConnection(String dbName, String user, String password){
        this.dbName = dbName;
        this.user = user;
        this.password =  password;
        this.conn = null;
        connectToDB();
    }
    
    public void connectToDB(){
        String url = "jdbc:postgresql://localhost/"+dbName;
        Properties props = new Properties();
        props.setProperty("user",user);
        props.setProperty("password",password);
        props.setProperty("ssl","false");
        try {
            conn = DriverManager.getConnection(url, props);
        } catch (SQLException ex) {
            Logger.getLogger(DBConnection.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public boolean isConnectionActive(){
        if(conn == null)
            return false;
        try {
            return (!conn.isClosed());
        } catch (SQLException ex) {
            Logger.getLogger(DBConnection.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
    }
    
    public void disconnectToDB(){
        try {
            if(conn == null || conn.isClosed())
                return;
        } catch (SQLException ex) {
            Logger.getLogger(DBConnection.class.getName()).log(Level.SEVERE, null, ex);
        }
        try {
            conn.close();
        } catch (SQLException ex) {
            Logger.getLogger(DBConnection.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    /**
     * Process a select sql and return a set of results
     * @param sql the full SELECT sql
     * @param nColumns the number of Columns selected
     * @return A LinkedList of String arrays where each array is a row result of
     * the sql and each array element is a column of that row.
     */
    public LinkedList<String[]> processSelectQuery(String sql, int nColumns){
        LinkedList<String[]> result = new LinkedList<>();
        Statement st;
        try {
            st = conn.createStatement();
            ResultSet rs = st.executeQuery(sql);
            while (rs.next()){
                String[] columns = new String[nColumns];
                for(int i=0; i<nColumns; i++)
                    columns[i] = rs.getString(i);
                result.add(columns);
            }
            rs.close();
            st.close();
        } catch (SQLException ex) {
            Logger.getLogger(DBConnection.class.getName()).log(Level.SEVERE, null, ex);
        }
        return result;
    }
    
    public LinkedList<Map<String, String>> processSelectQuerry(String sql, Set<String>columnName){
        LinkedList<Map<String, String>> result = new LinkedList<>();
        Statement st;
        try {
            st = conn.createStatement();
            ResultSet rs = st.executeQuery(sql);
            while (rs.next()){
                HashMap<String, String> columnMap = new HashMap<>();
                for(String columnNamei : columnName)
                    columnMap.put(columnNamei, rs.getString(columnNamei));
                result.add(columnMap);
            }
            rs.close();
            st.close();
        } catch (SQLException ex) {
            Logger.getLogger(DBConnection.class.getName()).log(Level.SEVERE, null, ex);
        }
        return result;
    }
    
    /**
     * Process any sql that will update the database
     * You should use the processInsert() method to process a INSERT sql
     * @param sql the full UPDATE OR DELETE sql
     * @return return the number of rows that were updated
     */
    public int processUpdate(String sql){
        Statement st;
        int rowsUpdated = 0;
        try {
            st = conn.createStatement();
            rowsUpdated = st.executeUpdate(sql);
            st.close();
        } catch (SQLException ex) {
            Logger.getLogger(DBConnection.class.getName()).log(Level.SEVERE, null, ex);
        }
        return rowsUpdated;
    }
    
    /**
     * Process a INSERT sql and return the generated key
     * @param sql the full INSERT sql
     * @param keyColumnName the key's column name at the inserted table
     * @return an long integer with the generated key
     */
    public long processInsert(String sql, String keyColumnName){
        Statement st;
        long generatedKey = 0;
        try {
            st = conn.createStatement();
            st.execute(sql, Statement.RETURN_GENERATED_KEYS);
            ResultSet rs = st.getGeneratedKeys();
            rs.next();
            generatedKey = rs.getLong(keyColumnName);
            rs.close();
            st.close();
        } catch (SQLException ex) {
            Logger.getLogger(DBConnection.class.getName()).log(Level.SEVERE, null, ex);
        }
        return generatedKey;
    }
    
    public boolean processReturn() throws SQLException{
        Statement stmt = null;
        String url = "jdbc:postgresql://localhost/"+dbName;
        Properties props = new Properties();
        props.setProperty("user",user);
        props.setProperty("password",password);
        props.setProperty("ssl","false");
        try{
            conn = DriverManager.getConnection(url, props);
            conn.setAutoCommit(false);
            System.out.println("Opened database successfully");
            stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery( "SELECT * FROM Event;" );
             while ( rs.next() ) {
                int id = rs.getInt("event_id");
                String name = rs.getString("event_name");
                System.out.println( "ID = " + id );
                System.out.println( "NAME = " + name );
                System.out.println();
             }
            rs.close();
            stmt.close();
        
        }catch (SQLException ex) {
            Logger.getLogger(DBConnection.class.getName()).log(Level.SEVERE, null, ex);
        }
        return true;
    }
}