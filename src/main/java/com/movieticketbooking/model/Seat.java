package com.movieticketbooking.model;

/**
 * Represents one seat for a specific show.
 */
public class Seat {
    private int     seatId;
    private String  seatNumber;
    private int     showId;
    private boolean isBooked;

    public Seat() {}

    public Seat(int seatId, String seatNumber, int showId, boolean isBooked) {
        this.seatId     = seatId;
        this.seatNumber = seatNumber;
        this.showId     = showId;
        this.isBooked   = isBooked;
    }

    // ── Getters ──────────────────────────────────────────────────────────────
    public int     getSeatId()     { return seatId;     }
    public String  getSeatNumber() { return seatNumber; }
    public int     getShowId()     { return showId;     }
    public boolean isBooked()      { return isBooked;   }

    // ── Setters ──────────────────────────────────────────────────────────────
    public void setSeatId(int seatId)         { this.seatId     = seatId;     }
    public void setSeatNumber(String s)       { this.seatNumber = s;          }
    public void setShowId(int showId)         { this.showId     = showId;     }
    public void setBooked(boolean isBooked)   { this.isBooked   = isBooked;   }

    @Override
    public String toString() {
        return String.format("%s [%s]", seatNumber, isBooked ? "BOOKED" : "AVAILABLE");
    }
}
