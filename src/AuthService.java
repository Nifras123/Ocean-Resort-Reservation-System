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
    private final Map<String, Session> sessions = new HashMap<>();

    public enum Role {
        ADMIN,
        CUSTOMER
    }

    public static final class Session {
        public final String username;
        public final Role role;

        public Session(String username, Role role) {
            this.username = username;
            this.role = role;
        }
    }

    public AuthService(Path usersFile) {
        this.usersFile = usersFile;
    }

    public synchronized void ensureUsersFile() throws IOException {
        if (usersFile.getParent() != null) Files.createDirectories(usersFile.getParent());
        if (!Files.exists(usersFile)) {
            Files.createFile(usersFile);
        }

        boolean hasAdmin = false;
        boolean hasCustomer = false;
        try (BufferedReader r = Files.newBufferedReader(usersFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split(":", 3);
                if (parts.length < 2) continue;
                String u = parts[0].trim();
                if ("admin".equalsIgnoreCase(u)) hasAdmin = true;
                if ("customer".equalsIgnoreCase(u)) hasCustomer = true;
            }
        }

        if (hasAdmin && hasCustomer) return;

        try (BufferedWriter w = Files.newBufferedWriter(
                usersFile,
                StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.APPEND
        )) {
            if (!hasAdmin) {
                w.write("admin:admin:ADMIN");
                w.newLine();
            }
            if (!hasCustomer) {
                w.write("customer:customer:CUSTOMER");
                w.newLine();
            }
        }
    }

    public synchronized String login(String username, String password) throws IOException {
        ensureUsersFile();
        Session user = findUser(username, password);
        if (user == null) {
            throw new IllegalArgumentException("Invalid username or password");
        }
        String token = newToken();
        sessions.put(token, user);
        return token;
    }

    public synchronized void logout(String token) {
        if (token == null) return;
        sessions.remove(token);
    }

    public synchronized Session requireSession(String token) {
        if (token == null) throw new IllegalArgumentException("Not logged in");
        Session s = sessions.get(token);
        if (s == null) throw new IllegalArgumentException("Session expired. Please login again.");
        return s;
    }

    public synchronized void requireAdmin(String token) {
        Session s = requireSession(token);
        if (s.role != Role.ADMIN) throw new IllegalArgumentException("Forbidden");
    }

    private Session findUser(String username, String password) throws IOException {
        try (BufferedReader r = Files.newBufferedReader(usersFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split(":", 3);
                if (parts.length < 2) continue;

                String u = parts[0];
                String p = parts[1];
                if (!u.equals(username) || !p.equals(password)) continue;

                Role role = Role.CUSTOMER;
                if (parts.length >= 3) {
                    try {
                        role = Role.valueOf(parts[2].trim().toUpperCase());
                    } catch (IllegalArgumentException ignored) {
                        role = Role.CUSTOMER;
                    }
                } else {
                    if ("admin".equalsIgnoreCase(u)) role = Role.ADMIN;
                }
                return new Session(u, role);
            }
        }
        return null;
    }

    private String newToken() {
        byte[] buf = new byte[24];
        random.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
}
