package com.movieticketbooking.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Represents one screening (show) for a movie.
 */
public class Show {
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy hh:mm a");

    private int           showId;
    private int           movieId;
    private LocalDateTime showTime;
    private int           screenNo;

    public Show() {}

    public Show(int showId, int movieId, LocalDateTime showTime, int screenNo) {
        this.showId   = showId;
        this.movieId  = movieId;
        this.showTime = showTime;
        this.screenNo = screenNo;
    }

    // ── Getters ──────────────────────────────────────────────────────────────
    public int           getShowId()   { return showId;   }
    public int           getMovieId()  { return movieId;  }
    public LocalDateTime getShowTime() { return showTime; }
    public int           getScreenNo() { return screenNo; }

    // ── Setters ──────────────────────────────────────────────────────────────
    public void setShowId(int showId)             { this.showId   = showId;   }
    public void setMovieId(int movieId)           { this.movieId  = movieId;  }
    public void setShowTime(LocalDateTime t)      { this.showTime = t;        }
    public void setScreenNo(int screenNo)         { this.screenNo = screenNo; }

    @Override
    public String toString() {
        return String.format("[%d] Screen %d | %s", showId, screenNo,
                showTime.format(FMT));
    }
}
