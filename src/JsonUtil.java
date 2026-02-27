import java.util.LinkedHashMap;
import java.util.Map;

public class JsonUtil {
    public static String escape(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': out.append("\\\\"); break;
                case '"': out.append("\\\""); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default: out.append(c);
            }
        }
        return out.toString();
    }

    public static String jsonString(String s) {
        return "\"" + escape(s) + "\"";
    }

    public static String jsonObjectRaw(Map<String, String> fields) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (Map.Entry<String, String> e : fields.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append(jsonString(e.getKey())).append(":");
            sb.append(e.getValue());
        }
        sb.append("}");
        return sb.toString();
    }

    public static Map<String, String> parseFlatObject(String json) {
        // Minimal JSON parser for inputs like: {"k":"v","k2":"v2"}
        // Assumes keys and values are strings.
        Map<String, String> map = new LinkedHashMap<>();
        if (json == null) return map;
        String s = json.trim();
        if (s.isEmpty()) return map;
        if (s.startsWith("{")) s = s.substring(1);
        if (s.endsWith("}")) s = s.substring(0, s.length() - 1);
        s = s.trim();
        if (s.isEmpty()) return map;

        int i = 0;
        while (i < s.length()) {
            i = skipWs(s, i);
            if (i >= s.length()) break;
            if (s.charAt(i) != '"') break;
            ParseResult key = parseJsonString(s, i);
            i = skipWs(s, key.next);
            if (i >= s.length() || s.charAt(i) != ':') break;
            i++;
            i = skipWs(s, i);
            if (i >= s.length() || s.charAt(i) != '"') break;
            ParseResult val = parseJsonString(s, i);
            map.put(key.value, val.value);
            i = skipWs(s, val.next);
            if (i < s.length() && s.charAt(i) == ',') i++;
        }

        return map;
    }

    private static int skipWs(String s, int i) {
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c != ' ' && c != '\n' && c != '\r' && c != '\t') return i;
            i++;
        }
        return i;
    }

    private static class ParseResult {
        final String value;
        final int next;

        private ParseResult(String value, int next) {
            this.value = value;
            this.next = next;
        }
    }

    private static ParseResult parseJsonString(String s, int startQuote) {
        int i = startQuote + 1;
        StringBuilder out = new StringBuilder();
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '"') {
                return new ParseResult(out.toString(), i + 1);
            }
            if (c == '\\') {
                i++;
                if (i >= s.length()) break;
                char esc = s.charAt(i);
                switch (esc) {
                    case '"': out.append('"'); break;
                    case '\\': out.append('\\'); break;
                    case 'n': out.append('\n'); break;
                    case 'r': out.append('\r'); break;
                    case 't': out.append('\t'); break;
                    default: out.append(esc);
                }
                i++;
                continue;
            }
            out.append(c);
            i++;
        }
        return new ParseResult(out.toString(), i);
    }
}
