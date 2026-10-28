package multe.login;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Archivio delle credenziali: per ogni username tiene salt, hash della password,
 * id dell'utente e ruolo. Le password in chiaro non vengono mai conservate.
 * Versione in memoria; in seguito si può sostituire con una tabella del database.
 */
public class DataBasePassword {

    /** Una riga dell'archivio. */
    public static final class Voce {
        private final String username;
        private final byte[] salt;
        private final byte[] hash;
        private final int idUtente;
        private final Ruolo ruolo;

        private Voce(String username, byte[] salt, byte[] hash, int idUtente, Ruolo ruolo) {
            this.username = username;
            this.salt = salt;
            this.hash = hash;
            this.idUtente = idUtente;
            this.ruolo = ruolo;
        }

        public String getUsername() { return username; }
        public byte[] getSalt()     { return salt.clone(); }
        public byte[] getHash()     { return hash.clone(); }
        public int getIdUtente()    { return idUtente; }
        public Ruolo getRuolo()     { return ruolo; }
    }

    private final Map<String, Voce> voci = new ConcurrentHashMap<>();

    /**
     * Registra un utente: la password viene trasformata in hash prima di essere salvata.
     *
     * IPOTESI DI PROGETTO: al primo accesso si chiede un documento d'identità e il codice fiscale
     * viene letto dal documento, non digitato dall'utente. Quindi lo username (= codice fiscale) è unico
     * e corrisponde a una sola persona, e a ogni codice fiscale spetta un solo idUtente.
     * Per questo registra() NON controlla l'unicità dell'idUtente e deve essere chiamato solo dal processo
     * di iscrizione, mai da un client (oggi nessuna Sessione lo espone).
     */
    public void registra(String username, char[] password, int idUtente, Ruolo ruolo) {
        Objects.requireNonNull(username);
        Objects.requireNonNull(ruolo);
        byte[] salt = PasswordHasher.nuovoSalt();
        Voce nuova = new Voce(username, salt, PasswordHasher.hash(password, salt), idUtente, ruolo);
        if (voci.putIfAbsent(username, nuova) != null) {
            throw new IllegalStateException("Utente già registrato: " + username);
        }
    }

    public Optional<Voce> trova(String username) {
        return username == null ? Optional.<Voce>empty() : Optional.ofNullable(voci.get(username));
    }
}
