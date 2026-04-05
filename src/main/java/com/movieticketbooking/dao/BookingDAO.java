package com.movieticketbooking.dao;

import com.movieticketbooking.db.DBConnection;
import com.movieticketbooking.model.*;

import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Data Access Object for Bookings and Booking_Seats tables.
 *
 * The core bookSeats() method bundles everything in one ACID transaction:
 *   1. Insert into Bookings
 *   2. Insert into Booking_Seats (many-to-many)
 *   3. UPDATE Seats.is_booked = TRUE
 *   COMMIT – or ROLLBACK on any error.
 */
public class BookingDAO {

    private final SeatDAO seatDAO = new SeatDAO();

    /**
     * Book one or more seats for a given show.
     *
     * @param userName  the customer's name
     * @param showId    the target show
     * @param seatIds   list of seat_id values to book
     * @return fully-populated Booking object (with seat numbers) on success
     * @throws SQLException     on DB errors
     * @throws IllegalStateException if any requested seat is already booked
     */
    public Booking bookSeats(String userName, int showId, List<Integer> seatIds)
            throws SQLException {

        Connection con = DBConnection.getConnection();
        con.setAutoCommit(false);   // ← START TRANSACTION

        try {
            // Step 1: Lock & verify seats are still available
            String checkSql = "SELECT seat_id, is_booked, seat_number " +
                               "FROM Seats WHERE seat_id = ? FOR UPDATE";

            List<String> bookedLabels = new ArrayList<>();

            for (int seatId : seatIds) {
                try (PreparedStatement ps = con.prepareStatement(checkSql)) {
                    ps.setInt(1, seatId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalArgumentException(
                                    "Seat " + seatId + " does not exist.");
                        }
                        if (rs.getBoolean("is_booked")) {
                            throw new IllegalStateException(
                                    "Seat " + rs.getString("seat_number") +
                                    " is already booked! Choose another seat.");
                        }
                        bookedLabels.add(rs.getString("seat_number"));
                    }
                }
            }

            // Step 2: Insert booking record
            int bookingId;
            String insertBooking = "INSERT INTO Bookings (user_name, show_id) VALUES (?, ?)";
            try (PreparedStatement ps = con.prepareStatement(
                    insertBooking, Statement.RETURN_GENERATED_KEYS)) {

                ps.setString(1, userName);
                ps.setInt(2, showId);
                ps.executeUpdate();

                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("Failed to get booking_id.");
                    bookingId = keys.getInt(1);
                }
            }

            // Step 3: Insert into Booking_Seats & mark seats booked
            String insertSeatLink = "INSERT INTO Booking_Seats (booking_id, seat_id) VALUES (?, ?)";
            try (PreparedStatement ps = con.prepareStatement(insertSeatLink)) {
                for (int seatId : seatIds) {
                    ps.setInt(1, bookingId);
                    ps.setInt(2, seatId);
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            // Step 4: Mark seats as booked
            for (int seatId : seatIds) {
                seatDAO.markSeatBooked(con, seatId);
            }

            con.commit();  // ← COMMIT

            // Build result object
            Booking booking = new Booking();
            booking.setBookingId(bookingId);
            booking.setUserName(userName);
            booking.setShowId(showId);
            booking.setBookingTime(java.time.LocalDateTime.now());
            booking.setSeatNumbers(bookedLabels);
            return booking;

        } catch (Exception e) {
            con.rollback();  // ← ROLLBACK on any error
            throw (e instanceof SQLException)
                    ? (SQLException) e
                    : new SQLException("Booking failed – rolled back: " + e.getMessage(), e);
        } finally {
            con.setAutoCommit(true);
        }
    }

    /**
     * Fetch all bookings for a given show (history view).
     */
    public List<Booking> getBookingsByShow(int showId) throws SQLException {
        List<Booking> bookings = new ArrayList<>();
        String sql = "SELECT b.booking_id, b.user_name, b.show_id, b.booking_time, " +
                     "       GROUP_CONCAT(s.seat_number ORDER BY s.seat_number) AS seats " +
                     "FROM Bookings b " +
                     "JOIN Booking_Seats bs ON b.booking_id = bs.booking_id " +
                     "JOIN Seats s          ON bs.seat_id   = s.seat_id " +
                     "WHERE b.show_id = ? " +
                     "GROUP BY b.booking_id, b.user_name, b.show_id, b.booking_time";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, showId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Booking b = new Booking(
                            rs.getInt("booking_id"),
                            rs.getString("user_name"),
                            rs.getInt("show_id"),
                            rs.getTimestamp("booking_time").toLocalDateTime()
                    );
                    String seatsCsv = rs.getString("seats");
                    b.setSeatNumbers(List.of(seatsCsv.split(",")));
                    bookings.add(b);
                }
            }
        }
        return bookings;
    }

    /**
     * Fetch ALL bookings across all shows, joining Movies + Shows for the REST API.
     * Returns newest-first.
     */
    public List<BookingDetail> getAllBookingsWithDetails() throws SQLException {
        List<BookingDetail> results = new ArrayList<>();

        String sql =
            "SELECT b.booking_id, b.user_name, b.show_id, b.booking_time, " +
            "       GROUP_CONCAT(st.seat_number ORDER BY st.seat_number) AS seats, " +
            "       sh.show_time, sh.screen_no, sh.movie_id, " +
            "       m.name AS movie_name, m.duration, m.genre " +
            "FROM   Bookings b " +
            "LEFT JOIN Booking_Seats bs ON b.booking_id = bs.booking_id " +
            "LEFT JOIN Seats         st ON bs.seat_id   = st.seat_id " +
            "JOIN      Shows         sh ON b.show_id    = sh.show_id " +
            "JOIN      Movies        m  ON sh.movie_id  = m.movie_id " +
            "GROUP BY b.booking_id, b.user_name, b.show_id, b.booking_time, " +
            "         sh.show_time, sh.screen_no, sh.movie_id, " +
            "         m.name, m.duration, m.genre " +
            "ORDER BY b.booking_id DESC";

        try (Connection con = DBConnection.getConnection();
             Statement  stmt = con.createStatement();
             ResultSet  rs   = stmt.executeQuery(sql)) {

            while (rs.next()) {
                int    bookingId  = rs.getInt("booking_id");
                String userName   = rs.getString("user_name");
                int    showId     = rs.getInt("show_id");
                java.time.LocalDateTime bookingTime =
                        rs.getTimestamp("booking_time").toLocalDateTime();

                String seatsCsv  = rs.getString("seats");
                List<String> seatList = (seatsCsv != null)
                        ? Arrays.asList(seatsCsv.split(","))
                        : List.of();

                Show show = new Show();
                show.setShowId(showId);
                show.setMovieId(rs.getInt("movie_id"));
                show.setShowTime(rs.getTimestamp("show_time").toLocalDateTime());
                show.setScreenNo(rs.getInt("screen_no"));

                Movie movie = new Movie();
                movie.setMovieId(rs.getInt("movie_id"));
                movie.setName(rs.getString("movie_name"));
                movie.setDuration(rs.getInt("duration"));
                movie.setGenre(rs.getString("genre"));

                Booking b = new Booking(bookingId, userName, showId, bookingTime);
                results.add(new BookingDetail(b, show, movie, seatList));
            }
        }
        return results;
    }
}
