import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class TokenGenerator {
    private static final SecureRandom secureRandom = new SecureRandom();
    private static final Base64.Encoder base64Encoder = Base64.getUrlEncoder().withoutPadding();

    // Store token -> expiryTime
    private static final Map<String, Long> tokenStore = new HashMap<>();

    // Token valid for 30 minutes (in milliseconds)
    private static final long TOKEN_VALIDITY_DURATION = 30 * 60 * 1000;

    public static String generateToken(String username) {
        byte[] randomBytes = new byte[24];
        secureRandom.nextBytes(randomBytes);
        String randomPart = base64Encoder.encodeToString(randomBytes);
        String token = username + "-" + System.currentTimeMillis() + "-" + randomPart;

        // Store token with expiration time
        tokenStore.put(token, System.currentTimeMillis() + TOKEN_VALIDITY_DURATION);

        return token;
    }

    public static boolean isValidToken(String token) {
        Long expiryTime = tokenStore.get(token);
        if (expiryTime == null) return false;
        return System.currentTimeMillis() <= expiryTime;
    }
}