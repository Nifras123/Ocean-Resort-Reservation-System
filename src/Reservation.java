import java.time.LocalDate;

public class Reservation {
    public final String reservationNumber;
    public final String ownerUsername;
    public final String guestName;
    public final String address;
    public final String contactNumber;
    public final String roomType;
    public final LocalDate checkIn;
    public final LocalDate checkOut;

    public Reservation(
            String reservationNumber,
            String ownerUsername,
            String guestName,
            String address,
            String contactNumber,
            String roomType,
            LocalDate checkIn,
            LocalDate checkOut
    ) {
        this.reservationNumber = reservationNumber;
        this.ownerUsername = ownerUsername;
        this.guestName = guestName;
        this.address = address;
        this.contactNumber = contactNumber;
        this.roomType = roomType;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
    }
}
