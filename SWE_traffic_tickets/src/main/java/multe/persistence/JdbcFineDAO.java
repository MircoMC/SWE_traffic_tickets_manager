package fines.persistence;

import fines.domain.*;
import java.sql.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

/** PostgreSQL implementation of FineDAO (table "fines", see db/schema.sql). */
public class JdbcFineDAO implements FineDAO {

    private static final String COLUMNS = "id, owner_id, amount_cents, due_date, state, camera_id, plate, "
            + "detected_speed, speed_limit, detected_at, judge_id, ruling_reason, ruling_date";

    private final ConnectionProvider connections;

    public JdbcFineDAO(ConnectionProvider connections) {
        this.connections = Objects.requireNonNull(connections);
    }

    @Override
    public void save(Fine fine) {
        String sql = "INSERT INTO fines (" + COLUMNS + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection c = connections.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            Detection d = fine.getDetection();
            Optional<CancellationRuling> ruling = fine.getRuling();

            ps.setObject(1, fine.getId()); // UUID gestito da JDBC
            ps.setInt(2, fine.getOwnerId());
            ps.setLong(3, fine.getAmountCents());
            ps.setObject(4, fine.getDueDate(), Types.DATE);
            ps.setString(5, fine.getState().getState());
            ps.setString(6, d.getCameraId());
            ps.setString(7, d.getPlate());
            ps.setInt(8, d.getSpeed());
            ps.setInt(9, d.getLimit());
            ps.setObject(10, OffsetDateTime.ofInstant(d.getDetectedAt(), ZoneOffset.UTC));
            ps.setObject(11, ruling.map(CancellationRuling::getJudgeId).orElse(null), Types.INTEGER);
            ps.setString(12, ruling.map(CancellationRuling::getReason).orElse(null));
            ps.setObject(13, ruling.map(CancellationRuling::getDate).orElse(null), Types.DATE);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Cannot save fine " + fine.getId(), e);
        }
    }

    @Override
    public void update(Fine fine) {
        String sql = "UPDATE fines SET state = ?, judge_id = ?, ruling_reason = ?, ruling_date = ? WHERE id = ?";
        try (Connection c = connections.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            Optional<CancellationRuling> ruling = fine.getRuling();
            ps.setString(1, fine.getState().getState());
            ps.setObject(2, ruling.map(CancellationRuling::getJudgeId).orElse(null), Types.INTEGER);
            ps.setString(3, ruling.map(CancellationRuling::getReason).orElse(null));
            ps.setObject(4, ruling.map(CancellationRuling::getDate).orElse(null), Types.DATE);
            ps.setObject(5, fine.getId()); // UUID
            if (ps.executeUpdate() == 0) {
                throw new IllegalStateException("Fine not found: " + fine.getId());
            }
        } catch (SQLException e) {
            throw new DataAccessException("Cannot update fine " + fine.getId(), e);
        }
    }

    @Override
    public Optional<Fine> findById(UUID id) {
        String sql = "SELECT " + COLUMNS + " FROM fines WHERE id = ?";
        try (Connection c = connections.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(read(rs)) : Optional.<Fine>empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Cannot read fine " + id, e);
        }
    }

    @Override
    public List<Fine> findByOwner(int userId) {
        String sql = "SELECT " + COLUMNS + " FROM fines WHERE owner_id = ? ORDER BY id";
        try (Connection c = connections.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Fine> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(read(rs));
                }
                return result;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Cannot read the fines of user " + userId, e);
        }
    }

    private static Fine read(ResultSet rs) throws SQLException {
        Detection detection = new Detection(rs.getString("camera_id"), rs.getString("plate"),
                rs.getInt("detected_speed"), rs.getInt("speed_limit"),
                rs.getObject("detected_at", OffsetDateTime.class).toInstant());
        int judge = rs.getInt("judge_id");
        Integer judgeId = rs.wasNull() ? null : judge;

        // Attenzione: Fine.restore deve essere aggiornato per accettare UUID al posto di int
        return Fine.restore((UUID) rs.getObject("id"), rs.getLong("amount_cents"), rs.getInt("owner_id"),
                rs.getObject("due_date", LocalDate.class), detection, rs.getString("state"),
                judgeId, rs.getString("ruling_reason"), rs.getObject("ruling_date", LocalDate.class));
    }
}