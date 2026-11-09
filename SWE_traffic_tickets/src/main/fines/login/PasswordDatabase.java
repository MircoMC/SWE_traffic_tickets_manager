package fines.login;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Archivio delle credenziali. Una persona può avere:
 * <ul>
 *   <li>un ACCOUNT PRIVATO (codice fiscale + password): dà sempre il ruolo di cittadino;</li>
 *   <li>zero, uno o più ACCOUNT DI CARICA (codice fiscale + codice identificativo + password):
 *       danno il ruolo di giudice, carabiniere o amministratore.</li>
 * </ul>
 * Ogni credenziale ha il proprio salt e il proprio hash. Le password in chiaro non vengono mai conservate.
 * Versione in memoria; in seguito si può sostituire con una tabella del database.
 *
 * IPOTESI DI PROGETTO: al primo accesso si chiede un documento d'identità e il codice fiscale viene letto dal
 * documento, non digitato dall'utente. Quindi il codice fiscale è unico e corrisponde a una sola persona.
 * I metodi di registrazione devono essere chiamati solo dal processo di iscrizione, mai da un client.
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

    private final Map<String, Entry> privateAccounts = new ConcurrentHashMap<>();
    private final Map<List<String>, Entry> offices = new ConcurrentHashMap<>();

    /** Registra l'account privato (ruolo cittadino): la password viene trasformata in hash e l'array ricevuto azzerato. */
    public void register(String username, char[] password, int userId) {
        Objects.requireNonNull(username);
        Objects.requireNonNull(password);
        try {
            byte[] salt = PasswordHasher.newSalt();
            Entry fresh = new Entry(username, salt, PasswordHasher.hash(password, salt), userId, Role.CITIZEN);
            if (privateAccounts.putIfAbsent(username, fresh) != null) {
                throw new IllegalStateException("Utente già registrato");
            }
        } finally {
            Arrays.fill(password, '\0');   // la password in chiaro non resta nell'array del chiamante
        }
    }

    /**
     * Registra un account di carica per una persona che ha già l'account privato. L'idUtente è quello
     * dell'account privato: ruolo e persona restano la stessa cosa (un codice fiscale, un id).
     *
     * ATTENZIONE: la password della carica PUÒ essere uguale a quella privata (scelta di progetto). Chi conosce
     * una delle due ha allora già due terzi delle credenziali del ruolo, e il codice identificativo non è un
     * segreto forte (come un numero di matricola). Il ruolo può annullare multe e leggere quelle di tutti: se in
     * futuro serve più sicurezza, qui si può imporre una password diversa o un secondo fattore.
     *
     * @throws IllegalStateException    se manca l'account privato o la carica è già registrata
     * @throws IllegalArgumentException se il ruolo è CITTADINO o il codice identificativo è vuoto
     */
    public void registerOffice(String username, String officeCode, char[] password, Role role) {
        Objects.requireNonNull(username);
        Objects.requireNonNull(role);
        Objects.requireNonNull(password);
        try {
            if (role == Role.CITIZEN) {
                throw new IllegalArgumentException("Il cittadino non è una carica");
            }
            if (officeCode == null || officeCode.trim().isEmpty()) {
                throw new IllegalArgumentException("Codice identificativo mancante");
            }
            Entry privateEntry = privateAccounts.get(username);
            if (privateEntry == null) {
                throw new IllegalStateException("Serve prima l'account privato");
            }
            byte[] salt = PasswordHasher.newSalt();
            Entry fresh = new Entry(username, salt, PasswordHasher.hash(password, salt), privateEntry.getUserId(), role);
            if (offices.putIfAbsent(key(username, officeCode), fresh) != null) {
                throw new IllegalStateException("Carica già registrata");
            }
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    public Optional<Entry> find(String username) {
        return username == null ? Optional.<Entry>empty() : Optional.ofNullable(privateAccounts.get(username));
    }

    public Optional<Entry> findOffice(String username, String officeCode) {
        if (username == null || officeCode == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(offices.get(key(username, officeCode)));
    }

    private static List<String> key(String username, String officeCode) {
        return Arrays.asList(username, officeCode);
    }
}
