package com.movieticketbooking.dao;

import com.movieticketbooking.db.DBConnection;
import com.movieticketbooking.model.Show;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for the Shows table.
 */
public class ShowDAO {

    /**
     * Fetch all shows for a given movie ID.
     */
    public List<Show> getShowsByMovieId(int movieId) throws SQLException {
        List<Show> shows = new ArrayList<>();
        String sql = "SELECT show_id, movie_id, show_time, screen_no " +
                     "FROM Shows WHERE movie_id = ? ORDER BY show_time";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, movieId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    shows.add(mapRow(rs));
                }
            }
        }
        return shows;
    }

    /**
     * Fetch a single show by its ID.
     */
    public Show getShowById(int showId) throws SQLException {
        String sql = "SELECT show_id, movie_id, show_time, screen_no " +
                     "FROM Shows WHERE show_id = ?";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, showId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    /**
     * Add a new show (admin utility).
     */
    public int addShow(int movieId, LocalDateTime showTime, int screenNo) throws SQLException {
        String sql = "INSERT INTO Shows (movie_id, show_time, screen_no) VALUES (?, ?, ?)";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, movieId);
            ps.setTimestamp(2, Timestamp.valueOf(showTime));
            ps.setInt(3, screenNo);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        return -1;
    }

    // ── Private helpers ───────────────────────────────────────────────────────
    private Show mapRow(ResultSet rs) throws SQLException {
        return new Show(
                rs.getInt("show_id"),
                rs.getInt("movie_id"),
                rs.getTimestamp("show_time").toLocalDateTime(),
                rs.getInt("screen_no")
        );
    }
}
