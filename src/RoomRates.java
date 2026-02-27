import java.util.LinkedHashMap;
import java.util.Map;

public class RoomRates {
    public static Map<String, Integer> defaultRates() {
        Map<String, Integer> rates = new LinkedHashMap<>();
        rates.put("STANDARD", 8000);
        rates.put("DELUXE", 12000);
        rates.put("SUITE", 20000);
        return rates;
    }

    public static int rateForRoomType(String roomType) {
        Integer rate = defaultRates().get(roomType);
        if (rate == null) {
            throw new IllegalArgumentException("Unknown room type: " + roomType);
        }
        return rate;
    }
}
