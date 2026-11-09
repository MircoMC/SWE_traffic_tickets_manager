package fines.login;

import java.util.Optional;

/**
 * Unico punto che parla con PasswordDatabase: verifica la password e restituisce
 * l'identità (id e ruolo) di chi si è autenticato. Due porte distinte: l'account privato
 * (cittadino) e l'account di carica (giudice, carabiniere, amministratore).
 * Tutti i fallimenti sono uguali tra loro e hanno la stessa durata.
 */
public class LoginGateway {

    /** Usato quando l'account non esiste, per far durare il controllo quanto uno vero. */
    private static final byte[] DUMMY_SALT = PasswordHasher.newSalt();

    private final PasswordDatabase passwordDatabase;

    public LoginGateway(PasswordDatabase passwordDatabase) {
        this.passwordDatabase = passwordDatabase;
    }

    /** @return l'identità di cittadino se codice fiscale e password sono corretti, altrimenti vuoto */
    public Optional<Identity> authenticate(String username, char[] password) {
        return verify(passwordDatabase.find(username), password);
    }

    /** @return l'identità della carica se codice fiscale, codice identificativo e password sono corretti, altrimenti vuoto */
    public Optional<Identity> authenticateOffice(String username, String officeCode, char[] password) {
        return verify(passwordDatabase.findOffice(username, officeCode), password);
    }

    private Optional<Identity> verify(Optional<PasswordDatabase.Entry> found, char[] password) {
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
