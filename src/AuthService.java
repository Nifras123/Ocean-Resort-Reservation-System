import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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
                if ("nippu".equalsIgnoreCase(u)) hasCustomer = true;
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
                w.write("Nippu:Nippu:CUSTOMER");
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

    public static final class UserRecord {
        public final String username;
        public final Role role;

        public UserRecord(String username, Role role) {
            this.username = username;
            this.role = role;
        }
    }

    public synchronized List<UserRecord> listUsers() throws IOException {
        ensureUsersFile();
        List<UserRecord> out = new java.util.ArrayList<>();
        for (Map.Entry<String, StoredUser> e : loadUsers().entrySet()) {
            out.add(new UserRecord(e.getKey(), e.getValue().role));
        }
        return out;
    }

    public synchronized void upsertUser(String username, String password, Role role) throws IOException {
        ensureUsersFile();
        if (username == null || username.trim().isEmpty()) throw new IllegalArgumentException("Username is required");
        if (password == null || password.trim().isEmpty()) throw new IllegalArgumentException("Password is required");
        if (role == null) role = Role.CUSTOMER;

        Map<String, StoredUser> all = loadUsers();
        all.put(username.trim(), new StoredUser(password, role));
        writeUsers(all);
    }

    public synchronized void deleteUser(String username) throws IOException {
        ensureUsersFile();
        if (username == null || username.trim().isEmpty()) throw new IllegalArgumentException("Username is required");
        Map<String, StoredUser> all = loadUsers();
        if (all.remove(username.trim()) == null) throw new IllegalArgumentException("User not found");
        writeUsers(all);

        List<String> tokensToRemove = new java.util.ArrayList<>();
        for (Map.Entry<String, Session> e : sessions.entrySet()) {
            if (e.getValue() != null && username.trim().equals(e.getValue().username)) {
                tokensToRemove.add(e.getKey());
            }
        }
        for (String t : tokensToRemove) sessions.remove(t);
    }

    private static final class StoredUser {
        private final String password;
        private final Role role;

        private StoredUser(String password, Role role) {
            this.password = password;
            this.role = role;
        }
    }

    private Map<String, StoredUser> loadUsers() throws IOException {
        Map<String, StoredUser> map = new LinkedHashMap<>();
        try (BufferedReader r = Files.newBufferedReader(usersFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split(":", 3);
                if (parts.length < 2) continue;
                String u = parts[0].trim();
                String p = parts[1];
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
                if (!u.isEmpty()) map.put(u, new StoredUser(p, role));
            }
        }
        return map;
    }

    private void writeUsers(Map<String, StoredUser> users) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(usersFile, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, StoredUser> e : users.entrySet()) {
                String u = e.getKey();
                StoredUser su = e.getValue();
                if (u == null || u.trim().isEmpty() || su == null) continue;
                w.write(u.trim() + ":" + (su.password == null ? "" : su.password) + ":" + (su.role == null ? Role.CUSTOMER.name() : su.role.name()));
                w.newLine();
            }
        }
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
