package fines.login;

import java.util.Optional;

/**
 * Unico punto che parla con PasswordDatabase: verifica la password e restituisce
 * l'identità (id e ruolo) di chi si è autenticato.
 */
public class LoginGateway {

    /** Usato quando l'utente non esiste, per far durare il controllo quanto uno vero. */
    private static final byte[] DUMMY_SALT = PasswordHasher.newSalt();

    private final PasswordDatabase passwordDatabase;

    public LoginGateway(PasswordDatabase passwordDatabase) {
        this.passwordDatabase = passwordDatabase;
    }

    /** @return l'identità se le credenziali sono corrette, altrimenti vuoto */
    public Optional<Identity> authenticate(String username, char[] password) {
        Optional<PasswordDatabase.Entry> found = passwordDatabase.find(username);

        if (!found.isPresent()) {
            PasswordHasher.hash(password, DUMMY_SALT);
            return Optional.empty();
        }

        PasswordDatabase.Entry entry = found.get();
        if (!PasswordHasher.verify(password, entry.getSalt(), entry.getHash())) {
            return Optional.empty();
        }
        return Optional.of(new Identity(entry.getUserId(), entry.getUsername(), entry.getRole()));
    }
}
