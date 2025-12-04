package app;

import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class Utils {
    private static final SecureRandom RAND = new SecureRandom();
    public static final String PEPPER = "ChangeThisPepperForProd";

    public static String randomSaltBase64(int bytes) {
        byte[] b = new byte[bytes];
        RAND.nextBytes(b);
        return Base64.getEncoder().encodeToString(b);
    }

    public static String hashPassword(String password, String saltBase64) throws Exception {
        byte[] salt = Base64.getDecoder().decode(saltBase64);
        char[] chars = (password + PEPPER).toCharArray();
        PBEKeySpec spec = new PBEKeySpec(chars, salt, 65536, 256);
        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] hash = skf.generateSecret(spec).getEncoded();
        return Base64.getEncoder().encodeToString(hash);
    }
}
