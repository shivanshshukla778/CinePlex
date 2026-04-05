package com.movieticketbooking.model;

/**
 * Represents a movie in the database.
 */
public class Movie {
    private int movieId;
    private String name;
    private int duration;   // minutes
    private String genre;

    public Movie() {}

    public Movie(int movieId, String name, int duration, String genre) {
        this.movieId  = movieId;
        this.name     = name;
        this.duration = duration;
        this.genre    = genre;
    }

    // ── Getters ──────────────────────────────────────────────────────────────
    public int    getMovieId()  { return movieId;  }
    public String getName()     { return name;     }
    public int    getDuration() { return duration; }
    public String getGenre()    { return genre;    }

    // ── Setters ──────────────────────────────────────────────────────────────
    public void setMovieId(int movieId)    { this.movieId  = movieId;  }
    public void setName(String name)       { this.name     = name;     }
    public void setDuration(int duration) { this.duration = duration; }
    public void setGenre(String genre)     { this.genre    = genre;    }

    @Override
    public String toString() {
        return String.format("[%d] %s (%s) – %d min", movieId, name, genre, duration);
    }
}
