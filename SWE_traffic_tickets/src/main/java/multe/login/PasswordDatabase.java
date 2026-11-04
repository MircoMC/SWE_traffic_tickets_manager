package fines.login;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Archivio delle credenziali in memoria. Implementa CredentialsStore.
 */
public class PasswordDatabase implements CredentialsStore {

    private final Map<String, CredentialsStore.Entry> entries = new ConcurrentHashMap<>();

    public void register(String username, char[] password, int userId, Role role) {
        Objects.requireNonNull(username);
        Objects.requireNonNull(role);
        byte[] salt = PasswordHasher.newSalt();
        CredentialsStore.Entry newEntry = new CredentialsStore.Entry(
            username, salt, PasswordHasher.hash(password, salt), userId, role
        );
        if (entries.putIfAbsent(username, newEntry) != null) {
            throw new IllegalStateException("Utente già registrato: " + username);
        }
    }

    @Override
    public Optional<CredentialsStore.Entry> find(String username) {
        return username == null ? Optional.empty() : Optional.ofNullable(entries.get(username));
    }
}
