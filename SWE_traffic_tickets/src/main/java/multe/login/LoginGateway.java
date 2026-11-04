package fines.login;

import java.util.Optional;

/**
 * The only point that talks to the credentials store: it verifies the password and returns
 * the identity (id and role) of whoever authenticated.
 */
public class LoginGateway {

    /** Used when the user does not exist, so that the check takes as long as a real one. */
    private static final byte[] DUMMY_SALT = PasswordHasher.newSalt();

    private final CredentialsStore credentials;

    public LoginGateway(CredentialsStore credentials) {
        this.credentials = credentials;
    }

    /** @return the identity if the credentials are correct, otherwise empty */
    public Optional<Identity> authenticate(String username, char[] password) {
        Optional<CredentialsStore.Entry> found = credentials.find(username);

        if (!found.isPresent()) {
            PasswordHasher.hash(password, DUMMY_SALT);
            return Optional.empty();
        }

        CredentialsStore.Entry entry = found.get();
        if (!PasswordHasher.verify(password, entry.getSalt(), entry.getHash())) {
            return Optional.empty();
        }
        return Optional.of(new Identity(entry.getUserId(), entry.getUsername(), entry.getRole()));
    }
}
