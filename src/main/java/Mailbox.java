import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.util.ArrayList;

/**
 * Created by toli444 on 3.3.17.
 */
public class Mailbox {
    ArrayList<MimeMessage> messages;

    public Mailbox(ArrayList<MimeMessage> messages) {
        this.messages = messages;
    }

    public int getAmountOfAllMessages() {
        return this.messages.size();
    }

    public int countTotalSizeOfMessages() throws MessagingException {
        int totalSize = 0;

        for (MimeMessage message : messages) {
            totalSize += message.getSize();
        }

        return totalSize;
    }

    public int getSizeOfMessage(int index) throws MessagingException {
        return messages.get(index).getSize();
    }
}
