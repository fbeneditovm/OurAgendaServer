/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ouragendaserver;

/**
 *
 * @author fbeneditovm
 */
public interface Server {
    public void userLogin(long userid, Connection connection);
    
    
    public void userLogout(long userid);
    
    
    public boolean isUserOnline(long userid);
    
    
    public void notifyUser(long userid);
    
}
