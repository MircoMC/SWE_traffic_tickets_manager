package fines.login;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Archivio delle credenziali: per ogni username tiene salt, hash della password,
 * id dell'utente e ruolo. Le password in chiaro non vengono mai conservate.
 * Versione in memoria; in seguito si può sostituire con una tabella del database.
 */
public class PasswordDatabase {

    /** Una riga dell'archivio. */
    public static final class Entry {
        private final String username;
        private final byte[] salt;
        private final byte[] hash;
        private final int userId;
        private final Role role;

        private Entry(String username, byte[] salt, byte[] hash, int userId, Role role) {
            this.username = username;
            this.salt = salt;
            this.hash = hash;
            this.userId = userId;
            this.role = role;
        }

        public String getUsername() { return username; }
        public byte[] getSalt()     { return salt.clone(); }
        public byte[] getHash()     { return hash.clone(); }
        public int getUserId()    { return userId; }
        public Role getRole()     { return role; }
    }

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    /**
     * Registra un utente: la password viene trasformata in hash prima di essere salvata.
     *
     * IPOTESI DI PROGETTO: al primo accesso si chiede un documento d'identità e il codice fiscale
     * viene letto dal documento, non digitato dall'utente. Quindi lo username (= codice fiscale) è unico
     * e corrisponde a una sola persona, e a ogni codice fiscale spetta un solo userId.
     * Per questo registra() NON controlla l'unicità dell'userId e deve essere chiamato solo dal processo
     * di iscrizione, mai da un client (oggi nessuna Sessione lo espone).
     */
    public void register(String username, char[] password, int userId, Role role) {
        Objects.requireNonNull(username);
        Objects.requireNonNull(role);
        byte[] salt = PasswordHasher.newSalt();
        Entry newEntry = new Entry(username, salt, PasswordHasher.hash(password, salt), userId, role);
        if (entries.putIfAbsent(username, newEntry) != null) {
            throw new IllegalStateException("Utente già registrato: " + username);
        }
    }

    public Optional<Entry> find(String username) {
        return username == null ? Optional.<Entry>empty() : Optional.ofNullable(entries.get(username));
    }
}
