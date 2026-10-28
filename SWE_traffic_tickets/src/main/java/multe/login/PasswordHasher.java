package multe.login;

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

    private static final String ALGORITMO = "PBKDF2WithHmacSHA256";
    private static final int ITERAZIONI = 600_000;
    private static final int LUNGHEZZA_HASH_BIT = 256;
    private static final int LUNGHEZZA_SALT_BYTE = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {}

    public static byte[] nuovoSalt() {
        byte[] salt = new byte[LUNGHEZZA_SALT_BYTE];
        RANDOM.nextBytes(salt);
        return salt;
    }

    public static byte[] hash(char[] password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERAZIONI, LUNGHEZZA_HASH_BIT);
        try {
            return SecretKeyFactory.getInstance(ALGORITMO).generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Hashing delle password non disponibile", e);
        } finally {
            spec.clearPassword();
        }
    }

    /** Confronto a tempo costante: il tempo non rivela quanti byte coincidono. */
    public static boolean verifica(char[] password, byte[] salt, byte[] hashAtteso) {
        return MessageDigest.isEqual(hash(password, salt), hashAtteso);
    }
}
