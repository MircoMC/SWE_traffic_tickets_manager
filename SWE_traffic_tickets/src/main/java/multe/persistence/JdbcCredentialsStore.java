package fines.persistence;

import fines.login.CredentialsStore;
import fines.login.Role;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;

/** PostgreSQL implementation of CredentialsStore: reads table "users" (username = tax_code). */
public class JdbcCredentialsStore implements CredentialsStore {

    private final ConnectionProvider connections;

    public JdbcCredentialsStore(ConnectionProvider connections) {
        this.connections = Objects.requireNonNull(connections);
    }

    @Override
    public Optional<CredentialsStore.Entry> find(String username) {
        if (username == null) {
            return Optional.empty();
        }
        String sql = "SELECT id, tax_code, password_salt, password_hash, role FROM users WHERE tax_code = ?";
        try (Connection c = connections.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new CredentialsStore.Entry(
                    rs.getString("tax_code"),
                    rs.getBytes("password_salt"),
                    rs.getBytes("password_hash"),
                    rs.getInt("id"),
                    Role.valueOf(rs.getString("role"))
                ));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Cannot read the credentials", e);
        }
    }
}
