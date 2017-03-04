import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.io.*;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Scanner;

/**
 * Created by toli444 on 3.3.17.
 */
public class Mailbox {
    DatabaseService databaseService;
    String username;

    ArrayList<String> messages = new ArrayList<String>();
    ArrayList<MimeMessage> mimeMessages = new ArrayList<MimeMessage>();

    private Properties props = System.getProperties();

    public Mailbox(String username, DatabaseService databaseService) {
        this.databaseService = databaseService;
        this.username = username;

        props.put("mail.host", "smtp.dummydomain.com");
        props.put("mail.transport.protocol", "smtp");

        this.messages = databaseService.getUsersMessages(this.username);

        for (String pathToMessage : this.messages) {
            this.mimeMessages.add(this.createMimeMessageFromPath(pathToMessage));
        }
    }

    public int getAmountOfAllMessages() {
        return this.mimeMessages.size();
    }

    public int countTotalSizeOfMessages() throws MessagingException {
        int totalSize = 0;

        for (MimeMessage message : mimeMessages) {
            totalSize += message.getSize();
        }

        return totalSize;
    }

    public String getWholeMessage(int index) {
//        String pathToMessage = this.messages.get(index);
//        String content = "";
//
//        try {
//            content = new Scanner(new File(pathToMessage)).useDelimiter("\\Z").next();
//        } catch (FileNotFoundException e) {
//            e.printStackTrace();
//        }
        String content = "";

        try {
            content = this.convertMimeMessageToPlainText(this.mimeMessages.get(index));
        } catch (IOException e) {
            e.printStackTrace();
        } catch (MessagingException e) {
            e.printStackTrace();
        }

        return content;
    }

    public int getSizeOfMessage(int index) throws MessagingException {
        return mimeMessages.get(index).getSize();
    }

    private MimeMessage createMimeMessageFromPath(String pathToMessage) {
        Session mailSession = Session.getDefaultInstance(props, null);
        InputStream source = null;
        MimeMessage message = null;

        try {
            source = new FileInputStream(pathToMessage);
            message = new MimeMessage(mailSession, source);
        } catch (MessagingException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        return message;
    }

    private String convertMimeMessageToPlainText(MimeMessage mimeMessage) throws IOException, MessagingException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        mimeMessage.writeTo(os);

        String value = os.toString();

        return value;
    }
}
