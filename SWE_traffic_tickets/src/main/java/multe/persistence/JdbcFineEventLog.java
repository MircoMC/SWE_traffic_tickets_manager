package fines.persistence;

import fines.domain.CancellationRuling;
import fines.domain.Fine;
import fines.domain.FineEvent;
import fines.domain.FineObserver;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * Observer that writes every fine event (issued, paid, expired, cancelled) in table "fine_events".
 * For a cancellation the author is the judge; the other events have no author.
 * A failure here is isolated by the domain: it never stops the operation on the fine.
 */
public class JdbcFineEventLog extends FineObserver {

    private final ConnectionProvider connections;
    private final Clock clock;

    public JdbcFineEventLog(ConnectionProvider connections, Clock clock) {
        this.connections = Objects.requireNonNull(connections);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    protected void onEvent(Fine fine, FineEvent event) {
        Integer authorId = event == FineEvent.CANCELLED
            ? fine.getRuling().map(CancellationRuling::getJudgeId).orElse(null) : null;
        String sql = "INSERT INTO fine_events (fine_id, event, occurred_at, author_id) VALUES (?, ?, ?, ?)";
        try (Connection c = connections.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, fine.getId()); // UUID gestito come Object per JDBC
            ps.setString(2, event.name());
            ps.setObject(3, OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
            ps.setObject(4, authorId, Types.INTEGER);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Cannot log event " + event + " of fine " + fine.getId(), e);
        }
    }
}
