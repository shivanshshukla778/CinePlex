package com.movieticketbooking.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Full denormalized booking view used by the /api/bookings REST endpoint.
 * Contains the booking record + resolved show + resolved movie + seat numbers.
 * Serialized directly to JSON by Gson.
 */
public class BookingDetail {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    // ── Booking fields ────────────────────────────────────────────────────────
    public int          booking_id;
    public String       user_name;
    public int          show_id;
    public String       booking_time;   // ISO-8601 string for easy JSON/JS parsing
    public List<String> seat_numbers;

    // ── Resolved FK fields (JOIN simulation) ──────────────────────────────────
    public ShowDetail   show;
    public MovieDetail  movie;

    // ── Inner DTOs ────────────────────────────────────────────────────────────
    public static class ShowDetail {
        public int    show_id;
        public int    movie_id;
        public String show_time;
        public int    screen_no;

        public ShowDetail(Show s) {
            this.show_id   = s.getShowId();
            this.movie_id  = s.getMovieId();
            this.show_time = s.getShowTime().format(FMT);
            this.screen_no = s.getScreenNo();
        }
    }

    public static class MovieDetail {
        public int    movie_id;
        public String name;
        public int    duration;
        public String genre;

        public MovieDetail(Movie m) {
            this.movie_id = m.getMovieId();
            this.name     = m.getName();
            this.duration = m.getDuration();
            this.genre    = m.getGenre();
        }
    }

    public BookingDetail(Booking b, Show s, Movie m, List<String> seats) {
        this.booking_id   = b.getBookingId();
        this.user_name    = b.getUserName();
        this.show_id      = b.getShowId();
        this.booking_time = b.getBookingTime() != null
                            ? b.getBookingTime().format(FMT)
                            : LocalDateTime.now().format(FMT);
        this.seat_numbers = seats;
        this.show  = s != null ? new ShowDetail(s)  : null;
        this.movie = m != null ? new MovieDetail(m) : null;
    }
}
