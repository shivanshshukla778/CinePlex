package com.movieticketbooking.service;

import com.movieticketbooking.dao.BookingDAO;
import com.movieticketbooking.dao.MovieDAO;
import com.movieticketbooking.dao.SeatDAO;
import com.movieticketbooking.dao.ShowDAO;
import com.movieticketbooking.model.*;

import java.sql.SQLException;
import java.util.List;

/**
 * Service layer – orchestrates DAOs and encapsulates booking business logic.
 * All methods throw RuntimeException-wrapped SQLExceptions for cleaner UI code.
 */
public class BookingService {

    private final MovieDAO   movieDAO   = new MovieDAO();
    private final ShowDAO    showDAO    = new ShowDAO();
    private final SeatDAO    seatDAO    = new SeatDAO();
    private final BookingDAO bookingDAO = new BookingDAO();

    // ── Movie operations ──────────────────────────────────────────────────────

    public List<Movie> getAllMovies() {
        try {
            return movieDAO.getAllMovies();
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching movies: " + e.getMessage(), e);
        }
    }

    public Movie getMovieById(int movieId) {
        try {
            return movieDAO.getMovieById(movieId);
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching movie: " + e.getMessage(), e);
        }
    }

    // ── Show operations ───────────────────────────────────────────────────────

    public List<Show> getShowsForMovie(int movieId) {
        try {
            return showDAO.getShowsByMovieId(movieId);
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching shows: " + e.getMessage(), e);
        }
    }

    public Show getShowById(int showId) {
        try {
            return showDAO.getShowById(showId);
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching show: " + e.getMessage(), e);
        }
    }

    // ── Seat operations ───────────────────────────────────────────────────────

    public List<Seat> getAllSeats(int showId) {
        try {
            return seatDAO.getSeatsByShowId(showId);
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching seats: " + e.getMessage(), e);
        }
    }

    public List<Seat> getAvailableSeats(int showId) {
        try {
            return seatDAO.getAvailableSeats(showId);
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching available seats: " + e.getMessage(), e);
        }
    }

    // ── Booking operations ────────────────────────────────────────────────────

    /**
     * Book seats & return the confirmed booking.
     * Throws IllegalStateException if any seat is already taken (prevents double booking).
     */
    public Booking bookSeats(String userName, int showId, List<Integer> seatIds) {
        if (userName == null || userName.isBlank())
            throw new IllegalArgumentException("User name cannot be blank.");
        if (seatIds == null || seatIds.isEmpty())
            throw new IllegalArgumentException("Select at least one seat.");

        try {
            return bookingDAO.bookSeats(userName, showId, seatIds);
        } catch (IllegalStateException e) {
            throw e;   // double-booking → re-throw as-is
        } catch (SQLException e) {
            throw new RuntimeException("Booking failed: " + e.getMessage(), e);
        }
    }

    public List<Booking> getBookingsForShow(int showId) {
        try {
            return bookingDAO.getBookingsByShow(showId);
        } catch (SQLException e) {
            throw new RuntimeException("Error fetching bookings: " + e.getMessage(), e);
        }
    }
}
