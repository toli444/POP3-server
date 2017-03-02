import javax.mail.internet.MimeMessage;
import java.util.ArrayList;

/**
 * Created by toli444 on 1.3.17.
 */
public interface DatabaseServiceInterface {
    boolean isUserExist(String username);
    boolean authenticateUser(String username, String password);
    ArrayList<MimeMessage> getUsersMessages(String username);
}
