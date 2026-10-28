package multe.login;

import java.util.Optional;

/**
 * Unico punto che parla con DataBasePassword: verifica la password e restituisce
 * l'identità (id e ruolo) di chi si è autenticato.
 */
public class GatewayLogin {

    /** Usato quando l'utente non esiste, per far durare il controllo quanto uno vero. */
    private static final byte[] SALT_FITTIZIO = PasswordHasher.nuovoSalt();

    private final DataBasePassword dataBasePassword;

    public GatewayLogin(DataBasePassword dataBasePassword) {
        this.dataBasePassword = dataBasePassword;
    }

    /** @return l'identità se le credenziali sono corrette, altrimenti vuoto */
    public Optional<Identita> autentica(String username, char[] password) {
        Optional<DataBasePassword.Voce> trovata = dataBasePassword.trova(username);

        if (!trovata.isPresent()) {
            PasswordHasher.hash(password, SALT_FITTIZIO);
            return Optional.empty();
        }

        DataBasePassword.Voce voce = trovata.get();
        if (!PasswordHasher.verifica(password, voce.getSalt(), voce.getHash())) {
            return Optional.empty();
        }
        return Optional.of(new Identita(voce.getIdUtente(), voce.getUsername(), voce.getRuolo()));
    }
}
