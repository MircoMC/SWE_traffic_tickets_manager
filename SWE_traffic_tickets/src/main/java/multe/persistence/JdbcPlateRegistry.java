package fines.persistence;

import fines.plates.PlateRegistry;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/** PostgreSQL implementation of PlateRegistry: table "plate_ownerships" (plate, owner_id, since). */
public class JdbcPlateRegistry implements PlateRegistry {

    private final ConnectionProvider connections;
    private final Clock clock;

    public JdbcPlateRegistry(ConnectionProvider connections, Clock clock) {
        this.connections = Objects.requireNonNull(connections);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public void assign(String plate, int ownerId) {
        assign(plate, ownerId, clock.instant());
    }

    @Override
    public void assign(String plate, int ownerId, Instant since) {
        Objects.requireNonNull(since);
        String sql = "INSERT INTO plate_ownerships (plate, owner_id, since) VALUES (?, ?, ?) "
            + "ON CONFLICT (plate, since) DO UPDATE SET owner_id = EXCLUDED.owner_id";
        try (Connection c = connections.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, PlateRegistry.normalize(plate));
            ps.setInt(2, ownerId);
            ps.setObject(3, OffsetDateTime.ofInstant(since, ZoneOffset.UTC));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Cannot assign plate " + plate, e);
        }
    }

    @Override
    public Optional<Integer> ownerAt(String plate, Instant instant) {
        String sql = "SELECT owner_id FROM plate_ownerships WHERE plate = ? AND since <= ? "
            + "ORDER BY since DESC LIMIT 1";
        try (Connection c = connections.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, PlateRegistry.normalize(plate));
            ps.setObject(2, OffsetDateTime.ofInstant(instant, ZoneOffset.UTC));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getInt(1)) : Optional.<Integer>empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Cannot read the owner of plate " + plate, e);
        }
    }

    @Override
    public Optional<Integer> currentOwner(String plate) {
        return ownerAt(plate, clock.instant());
    }

    @Override
    public Set<String> platesOf(int ownerId) {
        String sql = "SELECT po.plate FROM plate_ownerships po WHERE po.owner_id = ? "
            + "AND po.since = (SELECT MAX(x.since) FROM plate_ownerships x "
            + "WHERE x.plate = po.plate AND x.since <= ?)";
        try (Connection c = connections.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, ownerId);
            ps.setObject(2, OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
            try (ResultSet rs = ps.executeQuery()) {
                Set<String> result = new TreeSet<>();
                while (rs.next()) {
                    result.add(rs.getString(1));
                }
                return Collections.unmodifiableSet(result);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Cannot read the plates of user " + ownerId, e);
        }
    }
}
