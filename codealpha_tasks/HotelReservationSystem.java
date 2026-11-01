package codealpha_tasks;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.Set;

public class HotelReservationSystem {
    private static final Path BOOKINGS_FILE = Paths.get("bookings.csv");
    private static final List<Room> ROOMS = Arrays.asList(
            new Room("101", RoomType.STANDARD),
            new Room("102", RoomType.STANDARD),
            new Room("201", RoomType.DELUXE),
            new Room("202", RoomType.DELUXE),
            new Room("301", RoomType.SUITE));
    private static final List<String> CSV_HEADER = Arrays.asList(
            "bookingId", "guestName", "roomId", "checkIn", "checkOut", "totalCents", "status");

    private final Scanner scanner;
    private final List<Reservation> reservations = new ArrayList<>();
    private int nextBookingNumber = 1;

    private HotelReservationSystem(Scanner scanner) throws IOException {
        this.scanner = scanner;
        loadReservations();
    }

    public static void main(String[] args) {
        try (Scanner scanner = new Scanner(System.in)) {
            new HotelReservationSystem(scanner).run();
        } catch (IOException exception) {
            System.err.println("Unable to load or save bookings.csv: " + exception.getMessage());
        }
    }

    private void run() throws IOException {
        System.out.println("Hotel Reservation System");
        boolean running = true;
        while (running) {
            System.out.println("\n1. Search available rooms");
            System.out.println("2. Make a reservation");
            System.out.println("3. View booking details");
            System.out.println("4. Cancel a reservation");
            System.out.println("5. List reservations");
            System.out.println("6. Exit");
            System.out.print("Choose an option: ");

            switch (scanner.nextLine().trim()) {
                case "1":
                    searchRooms();
                    break;
                case "2":
                    makeReservation();
                    break;
                case "3":
                    viewReservation();
                    break;
                case "4":
                    cancelReservation();
                    break;
                case "5":
                    listReservations();
                    break;
                case "6":
                    running = false;
                    System.out.println("Goodbye.");
                    break;
                default:
                    System.out.println("Please choose a number from 1 to 6.");
            }
        }
    }

    private void searchRooms() {
        StayDates dates = promptStayDates();
        if (dates == null) {
            return;
        }

        System.out.print("Room category (Standard, Deluxe, Suite, or All): ");
        String category = scanner.nextLine().trim();
        RoomType type = parseRoomType(category);
        if (type == null && !category.equalsIgnoreCase("all")) {
            System.out.println("Choose Standard, Deluxe, Suite, or All.");
            return;
        }
        displayAvailableRooms(dates, type);
    }

    private RoomType parseRoomType(String value) {
        if (value.equalsIgnoreCase("all")) {
            return null;
        }
        for (RoomType type : RoomType.values()) {
            if (type.label.equalsIgnoreCase(value)) {
                return type;
            }
        }
        return null;
    }

    private void makeReservation() throws IOException {
        StayDates dates = promptStayDates();
        if (dates == null) {
            return;
        }

        List<Room> availableRooms = displayAvailableRooms(dates, null);
        if (availableRooms.isEmpty()) {
            return;
        }

        System.out.print("Room number: ");
        String roomId = scanner.nextLine().trim();
        Room selectedRoom = findRoom(roomId);
        if (selectedRoom == null || !availableRooms.contains(selectedRoom)) {
            System.out.println("That room is not available for the selected dates.");
            return;
        }

        System.out.print("Guest name: ");
        String guestName = scanner.nextLine().trim();
        if (guestName.isEmpty()) {
            System.out.println("Guest name cannot be blank.");
            return;
        }

        long nights = ChronoUnit.DAYS.between(dates.checkIn, dates.checkOut);
        long totalCents = selectedRoom.type.rateCents * nights;
        System.out.printf(Locale.ROOT, "Total for %d night(s): %s%n",
                nights, formatMoney(totalCents));
        System.out.print("Enter PAID to simulate successful payment, or anything else to cancel: ");
        if (!scanner.nextLine().trim().equalsIgnoreCase("PAID")) {
            System.out.println("Payment was not completed; no reservation was made.");
            return;
        }

        Reservation reservation = new Reservation(
                nextBookingId(), guestName, selectedRoom.id, dates.checkIn,
                dates.checkOut, totalCents, ReservationStatus.BOOKED);
        reservations.add(reservation);
        try {
            saveReservations();
        } catch (IOException exception) {
            reservations.remove(reservation);
            throw exception;
        }
        nextBookingNumber++;
        System.out.println("Reservation confirmed. Booking ID: " + reservation.bookingId);
    }

    private void viewReservation() {
        System.out.print("Booking ID: ");
        Reservation reservation = findReservation(scanner.nextLine().trim());
        if (reservation == null) {
            System.out.println("Booking not found.");
            return;
        }
        displayReservation(reservation);
    }

    private void cancelReservation() throws IOException {
        System.out.print("Booking ID to cancel: ");
        Reservation reservation = findReservation(scanner.nextLine().trim());
        if (reservation == null) {
            System.out.println("Booking not found.");
            return;
        }
        if (reservation.status == ReservationStatus.CANCELLED) {
            System.out.println("This reservation is already cancelled.");
            return;
        }

        System.out.print("Cancel booking " + reservation.bookingId + "? (yes/no): ");
        if (!scanner.nextLine().trim().equalsIgnoreCase("yes")) {
            System.out.println("Cancellation aborted.");
            return;
        }

        reservation.status = ReservationStatus.CANCELLED;
        try {
            saveReservations();
        } catch (IOException exception) {
            reservation.status = ReservationStatus.BOOKED;
            throw exception;
        }
        System.out.println("Reservation cancelled. The payment is simulated; no refund is processed.");
    }

    private void listReservations() {
        if (reservations.isEmpty()) {
            System.out.println("No reservations have been made.");
            return;
        }
        for (Reservation reservation : reservations) {
            displayReservation(reservation);
            System.out.println();
        }
    }

    private void displayReservation(Reservation reservation) {
        Room room = findRoom(reservation.roomId);
        System.out.println("Booking ID: " + reservation.bookingId);
        System.out.println("Guest: " + reservation.guestName);
        System.out.println("Room: " + reservation.roomId + " (" + room.type.label + ")");
        System.out.println("Check-in: " + reservation.checkIn);
        System.out.println("Check-out: " + reservation.checkOut);
        System.out.println("Total paid (simulated): " + formatMoney(reservation.totalCents));
        System.out.println("Status: " + reservation.status.label);
    }

    private List<Room> displayAvailableRooms(StayDates dates, RoomType type) {
        List<Room> available = new ArrayList<>();
        System.out.println("\nAvailable rooms (" + dates.checkIn + " to " + dates.checkOut + "):");
        for (Room room : ROOMS) {
            if ((type == null || room.type == type) && isAvailable(room, dates)) {
                available.add(room);
                System.out.printf(Locale.ROOT, "Room %s | %-8s | %s per night%n",
                        room.id, room.type.label, formatMoney(room.type.rateCents));
            }
        }
        if (available.isEmpty()) {
            System.out.println("No rooms are available for those dates.");
        }
        return available;
    }

    private boolean isAvailable(Room room, StayDates dates) {
        for (Reservation reservation : reservations) {
            boolean overlaps = dates.checkIn.isBefore(reservation.checkOut)
                    && dates.checkOut.isAfter(reservation.checkIn);
            if (reservation.status == ReservationStatus.BOOKED
                    && reservation.roomId.equals(room.id) && overlaps) {
                return false;
            }
        }
        return true;
    }

    private StayDates promptStayDates() {
        try {
            System.out.print("Check-in date (YYYY-MM-DD): ");
            LocalDate checkIn = LocalDate.parse(scanner.nextLine().trim());
            System.out.print("Check-out date (YYYY-MM-DD): ");
            LocalDate checkOut = LocalDate.parse(scanner.nextLine().trim());
            if (!checkOut.isAfter(checkIn)) {
                System.out.println("Check-out must be after check-in.");
                return null;
            }
            return new StayDates(checkIn, checkOut);
        } catch (DateTimeException exception) {
            System.out.println("Enter valid dates in YYYY-MM-DD format.");
            return null;
        }
    }

    private String nextBookingId() {
        return String.format(Locale.ROOT, "HR-%04d", nextBookingNumber);
    }

    private Reservation findReservation(String bookingId) {
        for (Reservation reservation : reservations) {
            if (reservation.bookingId.equalsIgnoreCase(bookingId)) {
                return reservation;
            }
        }
        return null;
    }

    private static Room findRoom(String roomId) {
        for (Room room : ROOMS) {
            if (room.id.equals(roomId)) {
                return room;
            }
        }
        return null;
    }

    private void loadReservations() throws IOException {
        if (!Files.exists(BOOKINGS_FILE)) {
            return;
        }

        Set<String> bookingIds = new HashSet<>();
        try (BufferedReader reader = Files.newBufferedReader(BOOKINGS_FILE, StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            List<String> parsedHeader;
            try {
                parsedHeader = parseCsvRow(header);
            } catch (IllegalArgumentException exception) {
                throw new IOException("bookings.csv has a malformed header.", exception);
            }
            if (!CSV_HEADER.equals(parsedHeader)) {
                throw new IOException("bookings.csv has an invalid header.");
            }

            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                try {
                    List<String> fields = parseCsvRow(line);
                    if (fields.size() != CSV_HEADER.size()) {
                        throw new IllegalArgumentException("expected 7 columns");
                    }
                    String bookingId = fields.get(0);
                    if (!bookingId.matches("HR-[0-9]{4,}") || !bookingIds.add(bookingId)) {
                        throw new IllegalArgumentException("invalid or duplicate booking ID");
                    }
                    String guestName = fields.get(1);
                    Room room = findRoom(fields.get(2));
                    if (guestName.trim().isEmpty() || room == null) {
                        throw new IllegalArgumentException("invalid guest name or room number");
                    }
                    LocalDate checkIn = LocalDate.parse(fields.get(3));
                    LocalDate checkOut = LocalDate.parse(fields.get(4));
                    if (!checkOut.isAfter(checkIn)) {
                        throw new IllegalArgumentException("check-out must be after check-in");
                    }
                    long totalCents = Long.parseLong(fields.get(5));
                    if (totalCents <= 0) {
                        throw new IllegalArgumentException("total must be positive");
                    }
                    ReservationStatus status = ReservationStatus.fromValue(fields.get(6));
                    reservations.add(new Reservation(
                            bookingId, guestName, room.id, checkIn, checkOut, totalCents, status));
                    nextBookingNumber = Math.max(nextBookingNumber,
                            Integer.parseInt(bookingId.substring(3)) + 1);
                } catch (RuntimeException exception) {
                    throw new IOException("Invalid booking on line " + lineNumber + ".", exception);
                }
            }
        }
    }

    private void saveReservations() throws IOException {
        Path absoluteFile = BOOKINGS_FILE.toAbsolutePath();
        Path temporaryFile = Files.createTempFile(
                absoluteFile.getParent(), "bookings-", ".tmp");
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(temporaryFile, StandardCharsets.UTF_8)) {
                writeCsvRow(writer, CSV_HEADER);
                for (Reservation reservation : reservations) {
                    writeCsvRow(writer, Arrays.asList(
                            reservation.bookingId,
                            reservation.guestName,
                            reservation.roomId,
                            reservation.checkIn.toString(),
                            reservation.checkOut.toString(),
                            Long.toString(reservation.totalCents),
                            reservation.status.name()));
                }
            }
            try {
                Files.move(temporaryFile, absoluteFile, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporaryFile, absoluteFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    private static List<String> parseCsvRow(String line) {
        if (line == null) {
            return null;
        }
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean quoteClosed = false;
        for (int i = 0; i < line.length(); i++) {
            char character = line.charAt(i);
            if (quoted) {
                if (character == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                        quoteClosed = true;
                    }
                } else {
                    field.append(character);
                }
            } else if (character == ',' && !quoted) {
                fields.add(field.toString());
                field.setLength(0);
                quoteClosed = false;
            } else if (character == '"' && field.length() == 0 && !quoteClosed) {
                quoted = true;
            } else if (quoteClosed) {
                throw new IllegalArgumentException("unexpected character after quoted field");
            } else {
                field.append(character);
            }
        }
        if (quoted) {
            throw new IllegalArgumentException("unterminated quoted field");
        }
        fields.add(field.toString());
        return fields;
    }

    private static void writeCsvRow(BufferedWriter writer, List<String> fields) throws IOException {
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                writer.write(',');
            }
            String field = fields.get(i);
            if (field.indexOf(',') >= 0 || field.indexOf('"') >= 0) {
                writer.write('"');
                writer.write(field.replace("\"", "\"\""));
                writer.write('"');
            } else {
                writer.write(field);
            }
        }
        writer.newLine();
    }

    private static String formatMoney(long cents) {
        return String.format(Locale.ROOT, "$%.2f", cents / 100.0);
    }

    private enum RoomType {
        STANDARD("Standard", 8999),
        DELUXE("Deluxe", 14999),
        SUITE("Suite", 24999);

        private final String label;
        private final long rateCents;

        RoomType(String label, long rateCents) {
            this.label = label;
            this.rateCents = rateCents;
        }
    }

    private enum ReservationStatus {
        BOOKED("Booked"),
        CANCELLED("Cancelled");

        private final String label;

        ReservationStatus(String label) {
            this.label = label;
        }

        private static ReservationStatus fromValue(String value) {
            return ReservationStatus.valueOf(value.toUpperCase(Locale.ROOT));
        }
    }

    private static final class Room {
        private final String id;
        private final RoomType type;

        private Room(String id, RoomType type) {
            this.id = id;
            this.type = type;
        }
    }

    private static final class StayDates {
        private final LocalDate checkIn;
        private final LocalDate checkOut;

        private StayDates(LocalDate checkIn, LocalDate checkOut) {
            this.checkIn = checkIn;
            this.checkOut = checkOut;
        }
    }

    private static final class Reservation {
        private final String bookingId;
        private final String guestName;
        private final String roomId;
        private final LocalDate checkIn;
        private final LocalDate checkOut;
        private final long totalCents;
        private ReservationStatus status;

        private Reservation(String bookingId, String guestName, String roomId,
                            LocalDate checkIn, LocalDate checkOut, long totalCents,
                            ReservationStatus status) {
            this.bookingId = bookingId;
            this.guestName = guestName;
            this.roomId = roomId;
            this.checkIn = checkIn;
            this.checkOut = checkOut;
            this.totalCents = totalCents;
            this.status = status;
        }
    }
}
