import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

public class ReservationStore {
    private final Path filePath;

    public ReservationStore(Path filePath) {
        this.filePath = filePath;
    }

    public synchronized void ensureExists() throws IOException {
        if (Files.exists(filePath)) return;
        if (filePath.getParent() != null) Files.createDirectories(filePath.getParent());
        Files.createFile(filePath);
    }

    public synchronized void add(Reservation r) throws IOException {
        ensureExists();
        Map<String, Reservation> all = loadAll();
        if (all.containsKey(r.reservationNumber)) {
            throw new IllegalArgumentException("Reservation number already exists: " + r.reservationNumber);
        }

        try (BufferedWriter w = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND)) {
            w.write(serialize(r));
            w.newLine();
        }
    }

    public synchronized Reservation find(String reservationNumber) throws IOException {
        ensureExists();
        try (BufferedReader r = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                Reservation rr = deserialize(line);
                if (rr != null && rr.reservationNumber.equals(reservationNumber)) return rr;
            }
        }
        return null;
    }

    private Map<String, Reservation> loadAll() throws IOException {
        Map<String, Reservation> map = new HashMap<>();
        try (BufferedReader r = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                Reservation rr = deserialize(line);
                if (rr != null) map.put(rr.reservationNumber, rr);
            }
        }
        return map;
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("|", "\\|").replace("\n", "\\n");
    }

    private static String unesc(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder();
        boolean escaping = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!escaping) {
                if (c == '\\') {
                    escaping = true;
                } else {
                    out.append(c);
                }
            } else {
                if (c == 'n') out.append('\n');
                else out.append(c);
                escaping = false;
            }
        }
        if (escaping) out.append('\\');
        return out.toString();
    }

    public static String serialize(Reservation r) {
        return esc(r.reservationNumber) + "|" +
                esc(r.guestName) + "|" +
                esc(r.address) + "|" +
                esc(r.contactNumber) + "|" +
                esc(r.roomType) + "|" +
                esc(r.checkIn.toString()) + "|" +
                esc(r.checkOut.toString());
    }

    public static Reservation deserialize(String line) {
        String[] parts = splitEscaped(line);
        if (parts.length < 7) return null;
        String reservationNumber = unesc(parts[0]);
        String guestName = unesc(parts[1]);
        String address = unesc(parts[2]);
        String contactNumber = unesc(parts[3]);
        String roomType = unesc(parts[4]);
        LocalDate checkIn = LocalDate.parse(unesc(parts[5]));
        LocalDate checkOut = LocalDate.parse(unesc(parts[6]));
        return new Reservation(reservationNumber, guestName, address, contactNumber, roomType, checkIn, checkOut);
    }

    private static String[] splitEscaped(String s) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean escaping = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!escaping) {
                if (c == '\\') {
                    escaping = true;
                    cur.append(c);
                } else if (c == '|') {
                    parts.add(cur.toString());
                    cur.setLength(0);
                } else {
                    cur.append(c);
                }
            } else {
                cur.append(c);
                escaping = false;
            }
        }
        parts.add(cur.toString());
        return parts.toArray(new String[0]);
    }
}
