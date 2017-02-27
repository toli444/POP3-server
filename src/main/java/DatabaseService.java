import java.sql.*;

public class DatabaseService {
    private Statement database;

    public DatabaseService() {
        this.loadDatabase();
        this.initDatabase();
    }

    public void loadDatabase() {
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

    public void initDatabase() {
            try {
                this.database.executeUpdate("DROP TABLE users");
                this.fillDatabase();
            } catch (SQLException e) {
                this.fillDatabase();
            }

    }

    private void fillDatabase() {
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

    public boolean isUserExist(String username){
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
}