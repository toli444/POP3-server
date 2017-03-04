import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.io.*;
import java.sql.*;
import java.util.*;


public class DatabaseService implements DatabaseServiceInterface {
    private static int uniqueId = 1;
    private Statement database;

    public DatabaseService() {
        this.loadDatabase();
        this.initDatabase();
    }

    private void loadDatabase() {
        try {
            Class.forName("com.mysql.jdbc.Driver").newInstance();
            Connection con = DriverManager.getConnection("jdbc:mysql://localhost:3306/aiposL1", "root", "111ono");
            this.database = (Statement) con.createStatement();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        }
    }

    private void initDatabase() {
        try {
            this.database.executeUpdate("DROP TABLE users");
            this.database.executeUpdate("DROP TABLE messages");
        } catch (SQLException e) {
            System.out.println("Can't drop because DB does'nt exit. But it's ok. I'll create new one");
        } finally {
            this.fillUsers();
            this.fillMails();
        }
    }

    private void fillUsers() {
        try {
            this.database.executeUpdate(
                    "CREATE TABLE users( " +
                            "username VARCHAR(255), " +
                            "password VARCHAR(255), " +
                            "PRIMARY KEY( username )" +
                            ");");
            this.database.executeUpdate(
                    "INSERT INTO users VALUES\n" +
                            "  ('tolik', '12345'),\n" +
                            "  ('admin', 'admin'),\n" +
                            "  ('nikitka', 'pidor'),\n" +
                            "  ('one', 'more');");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void fillMails() {
        try {
            this.database.executeUpdate(
                    "CREATE TABLE messages( " +
                            "message VARCHAR(255), " +
                            "username VARCHAR(255), " +
                            "PRIMARY KEY( message )" +
                            ");");
        } catch (SQLException e) {
            e.printStackTrace();
        }

        fillDatabaseWithGmailsEmails();
    }

    private void  fillDatabaseWithGmailsEmails() {
        Properties props = System.getProperties();
        props.setProperty("mail.store.protocol", "imaps");
        try {
            Session session = Session.getDefaultInstance(props, null);
            javax.mail.Store store = session.getStore("imaps");
            store.connect("imap.gmail.com", "toli44777@gmail.com", "to1110no");
            Folder folder = store.getFolder("INBOX");
            folder.open(Folder.READ_ONLY);

            for (int i=1; i <= 5; i++) {
                String pathToFile = this.saveUsersMessageLocally(folder.getMessage(i), "tolik");
                this.savePathToUsersMessageToDatabase(pathToFile, "tolik");
            }

            for (int i=6; i <= 10; i++) {
                String pathToFile = this.saveUsersMessageLocally(folder.getMessage(i), "tolik");
                this.savePathToUsersMessageToDatabase(pathToFile, "admin");
            }

            folder.close(true);

            System.out.println("mails creation finished");
        } catch (MessagingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String saveUsersMessageLocally(Message msg, String username) throws MessagingException, IOException {
        String whereToSave = "message" + this.uniqueId++ + ".eml";

        File fileToSave = new File(whereToSave);
        OutputStream out = new FileOutputStream(fileToSave, false);
        try {
            msg.writeTo(out);
        }
        finally {
            if (out != null) {
                out.flush();
                out.close();
            }
        }

        return fileToSave.getAbsolutePath();
    }

    private static String sanitizeFilename(String name) {
        return name.replaceAll("[:\\\\/*?|<> \"]", "_");
    }

    private void savePathToUsersMessageToDatabase(String pathToMessage, String username) {
        System.out.println(pathToMessage);

        if (this.isUserExist(username)) {
            try {
                this.database.executeUpdate(
                        "INSERT INTO messages VALUES\n" +
                                "  ('" + pathToMessage + "', '" + username + "' );");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public boolean isUserExist(String username) {
        String query;
        String dbUsername;

        try {
            query = "SELECT username FROM users;";
            this.database.executeQuery(query);
            ResultSet rs = this.database.getResultSet();

            while(rs.next()) {
                dbUsername = rs.getString("username");

                if(dbUsername.equals(username)) {
                    return true;
                }

            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return false;
    }

    public boolean authenticateUser(String username, String password) {
        String query;

        try {
            query = "SELECT * FROM users WHERE username=\"" + username + "\" AND password=\"" + password + "\";";
            this.database.executeQuery(query);
            ResultSet rs = this.database.getResultSet();

            if(rs.next()) {
                return true;
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return false;
    }

    public ArrayList<String> getUsersMessages(String username) {
        String query;
        String message;
        ArrayList<String> messages = new ArrayList<String>();

        try {
            query = "SELECT message FROM messages WHERE username=\"" + username + "\";";
            this.database.executeQuery(query);
            ResultSet rs = this.database.getResultSet();

            while(rs.next()) {
                message = rs.getString("message");
                messages.add(message);
            }

        } catch (SQLException e) {

        }

        return messages;
    }

    private String createMessage(String to, String from, String subject, String body, List<File> attachments) {
        try {
            Message message = new MimeMessage(Session.getInstance(System.getProperties()));
            message.setFrom(new InternetAddress(from));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject(subject);
            // create the message part
            MimeBodyPart content = new MimeBodyPart();
            // fill message
            content.setText(body);
            Multipart multipart = new MimeMultipart();
            multipart.addBodyPart(content);
            // add attachments
            for(File file : attachments) {
                MimeBodyPart attachment = new MimeBodyPart();
                DataSource source = new FileDataSource(file);
                attachment.setDataHandler(new DataHandler(source));
                attachment.setFileName(file.getName());
                multipart.addBodyPart(attachment);
            }
            // integration
            message.setContent(multipart);
            // store file

            File file = new File("message" + this.uniqueId + ".eml");
            message.writeTo(new FileOutputStream(file));

            return file.getAbsolutePath();

        } catch (MessagingException ex) {

        } catch (IOException ex) {

        }

        return null;
    }
}



/*
private void displayAllUsersMessages(String username) {
        String query;

        if (this.isUserExist(username)) {
            try {
                String pathToMessage;

                query = "select message from messages where username='" + username + "';\n";
                this.database.executeQuery(query);
                ResultSet rs = this.database.getResultSet();

                while(rs.next()) {
                    pathToMessage = rs.getString("message");
                    display(new File(pathToMessage));
                }

            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private void display(File emlFile) {
        Properties props = System.getProperties();
        props.put("mail.host", "smtp.tolik.com");
        props.put("mail.transport.protocol", "smtp");

        Session mailSession = Session.getDefaultInstance(props, null);

        try {
            InputStream source = new FileInputStream(emlFile);
            MimeMessage message = new MimeMessage(mailSession, source);

            System.out.println("Subject : " + message.getSubject());
            System.out.println("From : " + message.getFrom()[0]);
            System.out.println("--------------");
            System.out.println("Body : " +  message.getContent());
            System.out.println();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (MessagingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
 */