import com.sun.org.apache.bcel.internal.generic.GOTO;

import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;




/*
 * The server that can be run both as a console application or a GUI
 */
public class Server {
    private ServerSocket serverSocket;
    private static int clientUniqueId;
    private ArrayList<ClientSession> clients;
    private SimpleDateFormat time;

    private DatabaseService databaseService = new DatabaseService();

    // the port number to listen for connection
    private int port;
    // the boolean that will be turned of to stop the server
    private boolean keepGoing;


    public Server(int port) {
        this.port = port;
        this.time = new SimpleDateFormat("HH:mm:ss");
        this.clients = new ArrayList<ClientSession>();
    }

    public void start() {
        this.keepGoing = true;
		/* create socket server and wait for connection requests */
        try {
            // the socket used by the server
            this.serverSocket = new ServerSocket(port);

            // infinite loop to wait for connections
            while(keepGoing) {
                log("Server waiting for Clients on port " + port + ".");
                Socket socket = this.serverSocket.accept(); // accept connection
                // if I was asked to stop
                if(!keepGoing) {
                    this.stop();
                    break;
                }

                this.acceptClient(socket);
            }
        }
        // something went bad
        catch (IOException e) {
            log("Exception on new ServerSocket: " + e);
        }
    }

    private void stop() {
        // I was asked to stop
        try {
            this.serverSocket.close();
            for(int i = 0; i < this.clients.size(); ++i) {
                ClientSession clientSession = this.clients.get(i);
                clientSession.stopSession();
            }
        }
        catch(Exception e) {
            log("Exception closing the server and clients: " + e);
        }
    }

    private void log(String msg) {
        String messageWithTime = time.format(new Date()) + ": " + msg + "\n";
        System.out.print(messageWithTime);
    }

    private void acceptClient(Socket socket) {
        ClientSession clientSession = new ClientSession(socket);  // make a thread of it
        this.clients.add(clientSession);									// save it in the ArrayList
        clientSession.start();
    }

    // for a client who logoff using the LOGOUT message
    synchronized void removeClientById(int id) {
        // scan the array list until we found the Id
        for(int i = 0, length = clients.size(); i < length; i++) {
            ClientSession clientSession = clients.get(i);
            // found it
            if(clientSession.id == id) {
                clients.remove(i);
                return;
            }
        }
    }


    public static void main(String[] args) {
        // start server on port 1500 unless a PortNumber is specified
        int portNumber = 1500;

        // create a server object and start it
        Server server = new Server(portNumber);
        server.start();
    }



    /* One instance of this thread will run for each client */
    class ClientSession extends Thread {
        Socket socket;
        int id;
        PrintWriter toClient;
        BufferedReader fromClient;
        boolean isAuthenticated;
        boolean keepGoing = true;

        Pattern quitRegExp = Pattern.compile("QUIT");
        Pattern usernameRegExp = Pattern.compile("USERNAME (.{1,200})");
        Pattern passwordRegExp = Pattern.compile("PASS (.{1,200})");

        ClientSession(Socket socket) {
            id = ++clientUniqueId;
            this.socket = socket;

            try {
                toClient = new PrintWriter(this.socket.getOutputStream(), true);
                fromClient = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));
                log("Registered new client. His id: " + this.id);
            }
            catch (IOException e) {
                log("Exception creating new Input/output Streams: " + e);
                return;
            }
        }

        public void run() {
            Matcher matcher;
            String clientRespone;
            String username = null;

            try {
                toClient.println("+OK POP3 Tolyan and Fedorovich server ready. Who are you?");

                while (this.keepGoing) {
                    clientRespone = fromClient.readLine();
                    log("Client " + id + " sent us: " + clientRespone);

                    //Handle QUIT response
                    if (quitRegExp.matcher(clientRespone).find()) {
                        this.keepGoing = false;
                    }

                    if (!this.isAuthenticated) {
                        if (username == null) {
                            matcher = usernameRegExp.matcher(clientRespone);
                            if (matcher.find()) {
                                String user = matcher.group(1);

                                if (databaseService.isUserExist(user)) {
                                    username = user;
                                    toClient.println("+OK Wait for your password");
                                } else {
                                    toClient.println("-ERR I don't know anyone like this. Try again");
                                }

                                continue;
                            }
                        } else {
                            matcher = passwordRegExp.matcher(clientRespone);
                            if (matcher.find()) {
                                String password = matcher.group(1);

                                if (databaseService.authenticateUser(username, password)) {
                                    this.isAuthenticated = true;
                                    toClient.println("+OK You were authenticated");
                                } else {
                                    toClient.println("-ERR You're lier");
                                }

                                continue;
                            }
                        }
                    } else {

                    }

                    toClient.println("-ERR hmm, wrong command. Try another one");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            this.stopSession();
        }

        public void stopSession() {
            try {
                this.toClient.close();
                this.fromClient.close();
                this.socket.close();
                removeClientById(id);
            }
            catch(IOException e) {
                e.printStackTrace();
            }
        }

        private String getPassword() throws IOException {
            String str = fromClient.readLine();
            Matcher m = passwordRegExp.matcher(str);

            if (m.find()) {
                String password = m.group(1);
                return password;
            }

            return null;
        }
    }
}
