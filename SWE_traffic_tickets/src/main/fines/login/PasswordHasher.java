package fines.login;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Hashing delle password con PBKDF2 (incluso nel JDK): salt casuale per ogni utente
 * e molte iterazioni, per rendere lenti i tentativi di forza bruta.
 * Un semplice SHA-256 senza salt NON è adatto alle password.
 */
public final class PasswordHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 600_000;
    private static final int HASH_LENGTH_BITS = 256;
    private static final int SALT_LENGTH_BYTES = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {}

    public static byte[] newSalt() {
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        RANDOM.nextBytes(salt);
        return salt;
    }

    public static byte[] hash(char[] password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, HASH_LENGTH_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Hashing delle password non disponibile", e);
        } finally {
            spec.clearPassword();
        }
    }

    /** Confronto a tempo costante: il tempo non rivela quanti byte coincidono. */
    public static boolean verify(char[] password, byte[] salt, byte[] expectedHash) {
        return MessageDigest.isEqual(hash(password, salt), expectedHash);
    }
}
