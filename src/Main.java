import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

public class Main {
    public static void main(String[] args) throws Exception {
        int port = 8080;
        if (args != null && args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
            }
        }

        Path projectRoot = Path.of(System.getProperty("user.dir"));
        Path publicDir = projectRoot.resolve("public");
        Path dataDir = projectRoot.resolve("data");
        Path reservationsFile = dataDir.resolve("reservations.txt");
        Path usersFile = dataDir.resolve("users.txt");

        ReservationStore store = new ReservationStore(reservationsFile);
        AuthService auth = new AuthService(usersFile);
        store.ensureExists();
        auth.ensureUsersFile();

        HttpServer server;
        int boundPort = port;
        try {
            server = HttpServer.create(new InetSocketAddress(boundPort), 0);
        } catch (BindException be) {
            server = null;
            for (int p = port + 1; p <= port + 20; p++) {
                try {
                    boundPort = p;
                    server = HttpServer.create(new InetSocketAddress(boundPort), 0);
                    break;
                } catch (BindException ignored) {
                }
            }
            if (server == null) throw be;
        }

        server.createContext("/api/login", ex -> {
            if (HttpUtil.handleOptions(ex)) return;
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                HttpUtil.sendJson(ex, 405, "{\"ok\":false,\"message\":\"Method not allowed\"}");
                return;
            }
            try {
                String body = HttpUtil.readBody(ex);
                Map<String, String> obj = JsonUtil.parseFlatObject(body);
                String username = obj.getOrDefault("username", "").trim();
                String password = obj.getOrDefault("password", "").trim();
                if (username.isEmpty() || password.isEmpty()) {
                    HttpUtil.sendJson(ex, 400, "{\"ok\":false,\"message\":\"Username and password are required\"}");
                    return;
                }
                String token = auth.login(username, password);
                HttpUtil.sendJson(ex, 200, "{\"ok\":true,\"token\":" + JsonUtil.jsonString(token) + "}");
            } catch (IllegalArgumentException iae) {
                HttpUtil.sendJson(ex, 401, "{\"ok\":false,\"message\":" + JsonUtil.jsonString(iae.getMessage()) + "}");
            } catch (Exception e) {
                HttpUtil.sendJson(ex, 500, "{\"ok\":false,\"message\":\"Server error\"}");
            }
        });

        server.createContext("/api/logout", ex -> {
            if (HttpUtil.handleOptions(ex)) return;
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                HttpUtil.sendJson(ex, 405, "{\"ok\":false,\"message\":\"Method not allowed\"}");
                return;
            }
            String token = HttpUtil.bearerToken(ex);
            auth.logout(token);
            HttpUtil.sendJson(ex, 200, "{\"ok\":true,\"message\":\"Logged out\"}");
        });

        server.createContext("/api/me", ex -> {
            if (HttpUtil.handleOptions(ex)) return;
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                HttpUtil.sendJson(ex, 405, "{\"ok\":false,\"message\":\"Method not allowed\"}");
                return;
            }
            try {
                String token = HttpUtil.bearerToken(ex);
                String username = auth.requireUser(token);
                HttpUtil.sendJson(ex, 200, "{\"ok\":true,\"username\":" + JsonUtil.jsonString(username) + "}");
            } catch (IllegalArgumentException iae) {
                HttpUtil.sendJson(ex, 401, "{\"ok\":false,\"message\":" + JsonUtil.jsonString(iae.getMessage()) + "}");
            }
        });

        server.createContext("/api/rates", ex -> {
            if (HttpUtil.handleOptions(ex)) return;
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                HttpUtil.sendJson(ex, 405, "{\"ok\":false,\"message\":\"Method not allowed\"}");
                return;
            }

            Map<String, Integer> rates = RoomRates.defaultRates();
            StringBuilder arr = new StringBuilder();
            arr.append("[");
            boolean first = true;
            for (Map.Entry<String, Integer> e : rates.entrySet()) {
                if (!first) arr.append(",");
                first = false;
                arr.append("{\"roomType\":").append(JsonUtil.jsonString(e.getKey()))
                        .append(",\"ratePerNight\":").append(e.getValue()).append("}");
            }
            arr.append("]");

            HttpUtil.sendJson(ex, 200, "{\"ok\":true,\"rates\":" + arr + "}");
        });

        server.createContext("/api/help", ex -> {
            if (HttpUtil.handleOptions(ex)) return;
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                HttpUtil.sendJson(ex, 405, "{\"ok\":false,\"message\":\"Method not allowed\"}");
                return;
            }

            String help = "How to use Ocean View Resort Reservation System\n"
                    + "\n1) Login: Use your username/password to access the system.\n"
                    + "2) Add Reservation: Enter reservation number, guest details, room type and dates.\n"
                    + "3) Display Reservation: Search by reservation number to view full details.\n"
                    + "4) Bill: Enter reservation number to calculate nights and total cost.\n"
                    + "5) Logout/Exit: Logout to end your session safely.\n"
                    + "\nNotes:\n"
                    + "- Reservation numbers must be unique.\n"
                    + "- Check-out date must be after check-in date.\n";
            HttpUtil.sendJson(ex, 200, "{\"ok\":true,\"text\":" + JsonUtil.jsonString(help) + "}");
        });

        server.createContext("/api/reservations", ex -> {
            if (HttpUtil.handleOptions(ex)) return;
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                HttpUtil.sendJson(ex, 405, "{\"ok\":false,\"message\":\"Method not allowed\"}");
                return;
            }

            try {
                String token = HttpUtil.bearerToken(ex);
                auth.requireUser(token);

                String body = HttpUtil.readBody(ex);
                Map<String, String> obj = JsonUtil.parseFlatObject(body);

                String reservationNumber = obj.getOrDefault("reservationNumber", "").trim();
                String guestName = obj.getOrDefault("guestName", "").trim();
                String address = obj.getOrDefault("address", "").trim();
                String contactNumber = obj.getOrDefault("contactNumber", "").trim();
                String roomType = obj.getOrDefault("roomType", "").trim().toUpperCase();
                String checkInStr = obj.getOrDefault("checkIn", "").trim();
                String checkOutStr = obj.getOrDefault("checkOut", "").trim();

                if (reservationNumber.isEmpty()) throw new IllegalArgumentException("Reservation number is required");
                if (guestName.isEmpty()) throw new IllegalArgumentException("Guest name is required");
                if (address.isEmpty()) throw new IllegalArgumentException("Address is required");
                if (contactNumber.isEmpty()) throw new IllegalArgumentException("Contact number is required");
                if (roomType.isEmpty()) throw new IllegalArgumentException("Room type is required");
                if (checkInStr.isEmpty() || checkOutStr.isEmpty()) throw new IllegalArgumentException("Check-in and check-out dates are required");

                LocalDate checkIn = LocalDate.parse(checkInStr);
                LocalDate checkOut = LocalDate.parse(checkOutStr);
                if (!checkOut.isAfter(checkIn)) {
                    throw new IllegalArgumentException("Check-out date must be after check-in date");
                }

                RoomRates.rateForRoomType(roomType);

                Reservation r = new Reservation(reservationNumber, guestName, address, contactNumber, roomType, checkIn, checkOut);
                store.add(r);
                HttpUtil.sendJson(ex, 200, "{\"ok\":true,\"message\":\"Reservation saved successfully\"}");
            } catch (IllegalArgumentException iae) {
                int status = iae.getMessage() != null && iae.getMessage().toLowerCase().contains("not logged") ? 401 : 400;
                HttpUtil.sendJson(ex, status, "{\"ok\":false,\"message\":" + JsonUtil.jsonString(iae.getMessage()) + "}");
            } catch (Exception e) {
                HttpUtil.sendJson(ex, 500, "{\"ok\":false,\"message\":\"Server error\"}");
            }
        });

        server.createContext("/api/reservations/", ex -> {
            if (HttpUtil.handleOptions(ex)) return;
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                HttpUtil.sendJson(ex, 405, "{\"ok\":false,\"message\":\"Method not allowed\"}");
                return;
            }

            try {
                String token = HttpUtil.bearerToken(ex);
                auth.requireUser(token);

                String path = ex.getRequestURI().getPath();
                String id = path.substring("/api/reservations/".length());
                id = URLDecoder.decode(id, StandardCharsets.UTF_8);
                if (id.isEmpty()) {
                    HttpUtil.sendJson(ex, 400, "{\"ok\":false,\"message\":\"Reservation number is required\"}");
                    return;
                }

                Reservation r = store.find(id);
                if (r == null) {
                    HttpUtil.sendJson(ex, 404, "{\"ok\":false,\"message\":\"Reservation not found\"}");
                    return;
                }

                Map<String, String> fields = new LinkedHashMap<>();
                fields.put("reservationNumber", JsonUtil.jsonString(r.reservationNumber));
                fields.put("guestName", JsonUtil.jsonString(r.guestName));
                fields.put("address", JsonUtil.jsonString(r.address));
                fields.put("contactNumber", JsonUtil.jsonString(r.contactNumber));
                fields.put("roomType", JsonUtil.jsonString(r.roomType));
                fields.put("checkIn", JsonUtil.jsonString(r.checkIn.toString()));
                fields.put("checkOut", JsonUtil.jsonString(r.checkOut.toString()));
                String reservationJson = JsonUtil.jsonObjectRaw(fields);

                HttpUtil.sendJson(ex, 200, "{\"ok\":true,\"reservation\":" + reservationJson + "}");
            } catch (IllegalArgumentException iae) {
                HttpUtil.sendJson(ex, 401, "{\"ok\":false,\"message\":" + JsonUtil.jsonString(iae.getMessage()) + "}");
            } catch (Exception e) {
                HttpUtil.sendJson(ex, 500, "{\"ok\":false,\"message\":\"Server error\"}");
            }
        });

        server.createContext("/api/bill/", ex -> {
            if (HttpUtil.handleOptions(ex)) return;
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                HttpUtil.sendJson(ex, 405, "{\"ok\":false,\"message\":\"Method not allowed\"}");
                return;
            }

            try {
                String token = HttpUtil.bearerToken(ex);
                auth.requireUser(token);

                String path = ex.getRequestURI().getPath();
                String id = path.substring("/api/bill/".length());
                id = URLDecoder.decode(id, StandardCharsets.UTF_8);
                if (id.isEmpty()) {
                    HttpUtil.sendJson(ex, 400, "{\"ok\":false,\"message\":\"Reservation number is required\"}");
                    return;
                }

                Reservation r = store.find(id);
                if (r == null) {
                    HttpUtil.sendJson(ex, 404, "{\"ok\":false,\"message\":\"Reservation not found\"}");
                    return;
                }

                long nights = ChronoUnit.DAYS.between(r.checkIn, r.checkOut);
                int rate = RoomRates.rateForRoomType(r.roomType);
                long total = nights * (long) rate;

                String billJson = "{" +
                        "\"reservationNumber\":" + JsonUtil.jsonString(r.reservationNumber) + "," +
                        "\"guestName\":" + JsonUtil.jsonString(r.guestName) + "," +
                        "\"roomType\":" + JsonUtil.jsonString(r.roomType) + "," +
                        "\"checkIn\":" + JsonUtil.jsonString(r.checkIn.toString()) + "," +
                        "\"checkOut\":" + JsonUtil.jsonString(r.checkOut.toString()) + "," +
                        "\"nights\":" + nights + "," +
                        "\"ratePerNight\":" + rate + "," +
                        "\"total\":" + total +
                        "}";

                HttpUtil.sendJson(ex, 200, "{\"ok\":true,\"bill\":" + billJson + "}");
            } catch (IllegalArgumentException iae) {
                HttpUtil.sendJson(ex, 401, "{\"ok\":false,\"message\":" + JsonUtil.jsonString(iae.getMessage()) + "}");
            } catch (Exception e) {
                HttpUtil.sendJson(ex, 500, "{\"ok\":false,\"message\":\"Server error\"}");
            }
        });

        server.createContext("/", new StaticHandler(publicDir));

        server.setExecutor(null);
        server.start();
        System.out.println("Ocean View Resort system started.");
        System.out.println("Open: http://localhost:" + boundPort + "/");
    }

    static class StaticHandler implements HttpHandler {
        private final Path publicDir;

        StaticHandler(Path publicDir) {
            this.publicDir = publicDir;
        }

        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (HttpUtil.handleOptions(ex)) return;
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                HttpUtil.sendText(ex, 405, "Method Not Allowed", "text/plain");
                return;
            }

            String rawPath = ex.getRequestURI().getPath();
            String rel = rawPath == null ? "/" : rawPath;
            if (rel.equals("/")) rel = "/index.html";

            Path target = publicDir.resolve(rel.substring(1)).normalize();
            if (!target.startsWith(publicDir.normalize())) {
                HttpUtil.sendText(ex, 403, "Forbidden", "text/plain");
                return;
            }

            if (!Files.exists(target) || Files.isDirectory(target)) {
                HttpUtil.sendText(ex, 404, "Not Found", "text/plain");
                return;
            }

            String ct = contentType(target.getFileName().toString());
            byte[] bytes = Files.readAllBytes(target);
            ex.getResponseHeaders().set("Content-Type", ct);
            ex.getResponseHeaders().set("Cache-Control", "no-store");
            ex.sendResponseHeaders(200, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        }

        private static String contentType(String name) {
            String lower = name.toLowerCase();
            if (lower.endsWith(".html")) return "text/html; charset=utf-8";
            if (lower.endsWith(".css")) return "text/css; charset=utf-8";
            if (lower.endsWith(".js")) return "application/javascript; charset=utf-8";
            if (lower.endsWith(".png")) return "image/png";
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
            if (lower.endsWith(".svg")) return "image/svg+xml";
            return "application/octet-stream";
        }
    }
}