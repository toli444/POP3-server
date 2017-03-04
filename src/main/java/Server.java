import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import javax.mail.MessagingException;
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
        boolean keepGoing = true;

        String username = null;
        boolean isAuthenticated;

        Mailbox mailbox;

        Pattern quitRegExp = Pattern.compile("^QUIT$");
        Pattern usernameRegExp = Pattern.compile("^USERNAME (.{1,200})$");
        Pattern passwordRegExp = Pattern.compile("^PASS (.{1,200})$");
        Pattern statRegExp = Pattern.compile("^STAT$");
        Pattern listRegExp = Pattern.compile("^LIST$");
        Pattern retrRegExp = Pattern.compile("^RETR ([0-9]{1,200})");
        Pattern topRegExp = Pattern.compile("^TOP ([0-9]{1,200}) ([0-9]{1,200})");

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

            try {
                toClient.println("+OK POP3 Tolyan and Fedorovich server ready. Who are you?");

                while (this.keepGoing) {
                    clientRespone = this.readFromClient();

                    if (clientRespone == null) {
                        this.keepGoing = false;
                        break;
                    }

                    //Handle QUIT response
                    if (quitRegExp.matcher(clientRespone).find()) {
                        this.keepGoing = false;
                        this.writeToClient("+OK dewey POP3 server signing off");
                        break;
                    }

                    if (!this.isAuthenticated) {
                        if (username == null) {
                            matcher = usernameRegExp.matcher(clientRespone);
                            if (matcher.find()) {
                                handleUsername(matcher.group(1));
                                continue;
                            }
                        } else {
                            matcher = passwordRegExp.matcher(clientRespone);
                            if (matcher.find()) {
                                handlePassword(matcher.group(1));

                                //Now users session is open and we
                                // could initialize his mailbox to work with it later
                                this.mailbox = new Mailbox(username, databaseService);
                                continue;
                            }
                        }
                    } else {
                        if (statRegExp.matcher(clientRespone).find()) {
                            statCommand();
                            continue;
                        }

                        if (listRegExp.matcher(clientRespone).find()) {
                            listCommand();
                            continue;
                        }

                        matcher = retrRegExp.matcher(clientRespone);
                        if (matcher.find()) {
                            String i = matcher.group(1);
                            int index = Integer.parseInt(i);
                            retrCommand(index);

                            continue;
                        }

                        matcher = topRegExp.matcher(clientRespone);
                        if (matcher.find()) {
                            String i = matcher.group(1);
                            String sz = matcher.group(2);
                            int index = Integer.parseInt(i) - 1;
                            int amountOfLines = Integer.parseInt(sz);

                            topCommand(index, amountOfLines);

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

        private void handleUsername(String username) {
            if (databaseService.isUserExist(username)) {
                this.writeToClient("+OK Wait for your password");
                this.username = username;
            } else {
                this.writeToClient("-ERR I don't know anyone like this. Try again");
            }
        }

        private void handlePassword(String password) {
            if (databaseService.authenticateUser(this.username, password)) {
                this.isAuthenticated = true;
                this.writeToClient("+OK You were authenticated");
            } else {
                this.writeToClient("-ERR You bad boy");
            }
        }

        private void statCommand() throws MessagingException {
            int amountOfMessages = this.mailbox.getAmountOfAllMessages();
            int totalSize = this.mailbox.countTotalSizeOfMessages();

            this.writeToClient("+OK " + amountOfMessages + " " + totalSize);
        }

        private void listCommand() throws MessagingException {
            String response = "+OK Mailbox scan listing follows\n";

            int amountOfMessages = this.mailbox.getAmountOfAllMessages();

            for (int i = 0; i < amountOfMessages; i++) {
                response += (i + 1) + " " + this.mailbox.getSizeOfMessage(i) + "\n";
            }

            response += ".";

            this.writeToClient(response);
        }

        private void retrCommand(int indexOfMessage) throws MessagingException {
            String response = "+OK " + this.mailbox.getSizeOfMessage(indexOfMessage) + "\n";
            response += this.mailbox.getWholeMessage(indexOfMessage);

            this.writeToClient(response);
        }

        private void topCommand(int index, int amountOfLines) throws MessagingException {
            String wholeMessage = this.mailbox.getWholeMessage(index);

            if (wholeMessage == null) {
                this.writeToClient("-ERR Wrong message index");
            }

            String lines[] = wholeMessage.split("\\r?\\n");

            String response = "+OK " + this.mailbox.getSizeOfMessage(index) + "\n";
            if (amountOfLines <= lines.length) {
                for (int x = 0; x < amountOfLines; x++) {
                    response += lines[x] + "\n";
                }

                this.writeToClient(response);
            } else {
                this.writeToClient("-ERR This message doesn't have specified amount of lines");
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
    }
}
