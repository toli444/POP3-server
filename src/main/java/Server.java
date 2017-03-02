import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
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

    static Logger logger = Logger.getLogger(Server.class);


    public Server(int port) {
        this.port = port;
        this.time = new SimpleDateFormat("HH:mm:ss");
        this.clients = new ArrayList<ClientSession>();
    }

    public void start() {
        this.keepGoing = true;
        log("Server started");
		/* create socket server and wait for connection requests */
        try {
            // the socket used by the server
            this.serverSocket = new ServerSocket(port);

            // infinite loop to wait for connections
            while(keepGoing) {
                System.out.println("Server waiting for new Clients on port " + port + ".");
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
        } catch(Exception e) {
            log("Exception closing the server and clients: " + e);
        } finally {
            log("Server stopped");
        }
    }

    private void log(String msg) {
        String messageWithTime = time.format(new Date()) + ": " + msg + "\n";
        logger.info(messageWithTime);
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
        //init logger
        PropertyConfigurator.configure("log4j.properties");


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

        Pattern quitRegExp = Pattern.compile("^QUIT$");
        Pattern usernameRegExp = Pattern.compile("^USERNAME (.{1,200})$");
        Pattern passwordRegExp = Pattern.compile("^PASS (.{1,200})$");
        Pattern statRegExp = Pattern.compile("^STAT$");

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
                    clientRespone = this.readFromClient();

                    //Handle QUIT response
                    if (quitRegExp.matcher(clientRespone).find()) {
                        this.keepGoing = false;
                        this.writeToClient("+OK dewey POP3 server signing off");
                        continue;
                    }

                    if (!this.isAuthenticated) {
                        if (username == null) {
                            matcher = usernameRegExp.matcher(clientRespone);
                            if (matcher.find()) {
                                String user = matcher.group(1);

                                if (databaseService.isUserExist(user)) {
                                    username = user;
                                    this.writeToClient("+OK Wait for your password");
                                } else {
                                    this.writeToClient("-ERR I don't know anyone like this. Try again");
                                }

                                continue;
                            }
                        } else {
                            matcher = passwordRegExp.matcher(clientRespone);
                            if (matcher.find()) {
                                String password = matcher.group(1);

                                if (databaseService.authenticateUser(username, password)) {
                                    this.isAuthenticated = true;
                                    this.writeToClient("+OK You were authenticated");
                                } else {
                                    this.writeToClient("-ERR You bad boy");
                                }

                                continue;
                            }
                        }
                    } else {
                        if (statRegExp.matcher(clientRespone).find()) {
                            ArrayList<MimeMessage> messages = databaseService.getUsersMessages(username);
                            int amountOfMessages = messages.size();
                            int totalSize = this.countTotalSizeOfMimeMessages(messages);

                            this.writeToClient("+OK " + amountOfMessages + " " + totalSize);

                            continue;
                        }
                    }

                    this.writeToClient("-ERR hmm, wrong command. Try another one");
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (MessagingException e) {
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

        private void writeToClient(String message) {
            toClient.println(message);
            log("To client (" + id + "): " + message);
        }

        private String readFromClient() throws IOException {
            String clientRespone = fromClient.readLine();
            log("From client (" + id + "): " + clientRespone);

            return clientRespone;
        }

        private int countTotalSizeOfMimeMessages(ArrayList<MimeMessage> messages) throws MessagingException {
            int totalSize = 0;

            for (MimeMessage message : messages) {
                totalSize += message.getSize();
            }

            return totalSize;
        }
    }
}
