package com.movieticketbooking.dao;

import com.movieticketbooking.db.DBConnection;
import com.movieticketbooking.model.Movie;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for the Movies table.
 */
public class MovieDAO {

    /**
     * Fetch all movies from the database.
     */
    public List<Movie> getAllMovies() throws SQLException {
        List<Movie> movies = new ArrayList<>();
        String sql = "SELECT movie_id, name, duration, genre FROM Movies ORDER BY name";

        try (Connection con = DBConnection.getConnection();
             Statement  st  = con.createStatement();
             ResultSet  rs  = st.executeQuery(sql)) {

            while (rs.next()) {
                movies.add(new Movie(
                        rs.getInt("movie_id"),
                        rs.getString("name"),
                        rs.getInt("duration"),
                        rs.getString("genre")
                ));
            }
        }
        return movies;
    }

    /**
     * Fetch a single movie by its ID.
     */
    public Movie getMovieById(int movieId) throws SQLException {
        String sql = "SELECT movie_id, name, duration, genre FROM Movies WHERE movie_id = ?";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, movieId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Movie(
                            rs.getInt("movie_id"),
                            rs.getString("name"),
                            rs.getInt("duration"),
                            rs.getString("genre")
                    );
                }
            }
        }
        return null;
    }

    /**
     * Add a new movie (admin utility).
     */
    public int addMovie(String name, int duration, String genre) throws SQLException {
        String sql = "INSERT INTO Movies (name, duration, genre) VALUES (?, ?, ?)";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, name);
            ps.setInt(2, duration);
            ps.setString(3, genre);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        return -1;
    }
}
