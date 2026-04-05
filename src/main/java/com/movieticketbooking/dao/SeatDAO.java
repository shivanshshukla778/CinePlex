package com.movieticketbooking.dao;

import com.movieticketbooking.db.DBConnection;
import com.movieticketbooking.model.Seat;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for the Seats table.
 */
public class SeatDAO {

    /**
     * Fetch all seats for a given show (available & booked).
     */
    public List<Seat> getSeatsByShowId(int showId) throws SQLException {
        List<Seat> seats = new ArrayList<>();
        String sql = "SELECT seat_id, seat_number, show_id, is_booked " +
                     "FROM Seats WHERE show_id = ? ORDER BY seat_number";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, showId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    seats.add(mapRow(rs));
                }
            }
        }
        return seats;
    }

    /**
     * Fetch only AVAILABLE (not-booked) seats for a show.
     */
    public List<Seat> getAvailableSeats(int showId) throws SQLException {
        List<Seat> seats = new ArrayList<>();
        String sql = "SELECT seat_id, seat_number, show_id, is_booked " +
                     "FROM Seats WHERE show_id = ? AND is_booked = FALSE " +
                     "ORDER BY seat_number";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, showId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    seats.add(mapRow(rs));
                }
            }
        }
        return seats;
    }

    /**
     * Mark a seat as booked (called inside a transaction; connection is passed in).
     */
    public void markSeatBooked(Connection con, int seatId) throws SQLException {
        String sql = "UPDATE Seats SET is_booked = TRUE WHERE seat_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, seatId);
            ps.executeUpdate();
        }
    }

    /**
     * Count of seats still available for a show (used by shows API).
     */
    public int getAvailableCount(int showId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM Seats WHERE show_id = ? AND is_booked = FALSE";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, showId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * Create seats for a show in bulk (admin utility).
     * seatLabels e.g. ["A1","A2","B1"...]
     */
    public void createSeatsForShow(int showId, List<String> seatLabels) throws SQLException {
        String sql = "INSERT INTO Seats (seat_number, show_id) VALUES (?, ?)";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            for (String label : seatLabels) {
                ps.setString(1, label);
                ps.setInt(2, showId);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────
    private Seat mapRow(ResultSet rs) throws SQLException {
        return new Seat(
                rs.getInt("seat_id"),
                rs.getString("seat_number"),
                rs.getInt("show_id"),
                rs.getBoolean("is_booked")
        );
    }
}
