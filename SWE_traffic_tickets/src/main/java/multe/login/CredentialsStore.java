package fines.login;

import java.util.Optional;

/** Read access to the stored credentials: salt, password hash, user id and role of each username. */
public interface CredentialsStore {

    Optional<Entry> find(String username);

    /** One stored row. Passwords are never kept in clear text. */
    final class Entry {
        private final String username;
        private final byte[] salt;
        private final byte[] hash;
        private final int userId;
        private final Role role;

        public Entry(String username, byte[] salt, byte[] hash, int userId, Role role) {
            this.username = username;
            this.salt = salt.clone();
            this.hash = hash.clone();
            this.userId = userId;
            this.role = role;
        }

        public String getUsername() { return username; }
        public byte[] getSalt()     { return salt.clone(); }
        public byte[] getHash()     { return hash.clone(); }
        public int getUserId()      { return userId; }
        public Role getRole()       { return role; }
    }
}
