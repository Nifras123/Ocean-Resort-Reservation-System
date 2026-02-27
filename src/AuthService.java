import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class AuthService {
    private final Path usersFile;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, String> sessions = new HashMap<>();

    public AuthService(Path usersFile) {
        this.usersFile = usersFile;
    }

    public synchronized void ensureUsersFile() throws IOException {
        if (Files.exists(usersFile)) return;
        if (usersFile.getParent() != null) Files.createDirectories(usersFile.getParent());
        Files.createFile(usersFile);
        try (BufferedWriter w = Files.newBufferedWriter(usersFile, StandardCharsets.UTF_8)) {
            w.write("admin:admin");
            w.newLine();
        }
    }

    public synchronized String login(String username, String password) throws IOException {
        ensureUsersFile();
        if (!isValidUser(username, password)) {
            throw new IllegalArgumentException("Invalid username or password");
        }
        String token = newToken();
        sessions.put(token, username);
        return token;
    }

    public synchronized void logout(String token) {
        if (token == null) return;
        sessions.remove(token);
    }

    public synchronized String requireUser(String token) {
        if (token == null) throw new IllegalArgumentException("Not logged in");
        String user = sessions.get(token);
        if (user == null) throw new IllegalArgumentException("Session expired. Please login again.");
        return user;
    }

    private boolean isValidUser(String username, String password) throws IOException {
        try (BufferedReader r = Files.newBufferedReader(usersFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                int idx = line.indexOf(':');
                if (idx <= 0) continue;
                String u = line.substring(0, idx);
                String p = line.substring(idx + 1);
                if (u.equals(username) && p.equals(password)) return true;
            }
        }
        return false;
    }

    private String newToken() {
        byte[] buf = new byte[24];
        random.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
}
