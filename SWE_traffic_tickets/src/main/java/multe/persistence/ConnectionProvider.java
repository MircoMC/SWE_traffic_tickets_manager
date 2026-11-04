package fines.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Gives a database connection. The JDBC classes open one per operation and close it right after:
 * simple and enough for this project (a real system would use a connection pool).
 */
@FunctionalInterface
public interface ConnectionProvider {

    Connection open() throws SQLException;

    static ConnectionProvider of(String url, String user, String password) {
        return () -> DriverManager.getConnection(url, user, password);
    }
}
