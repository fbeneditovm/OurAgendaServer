package ouragendaserver;

/**
 * 
 * @author fbeneditovm
 */
public class OurAgendaServer {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        TCPServer server = new TCPServer(10000);
        server.startServer();
    }
    
}
