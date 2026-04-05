package com.movieticketbooking.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Represents a confirmed booking (aggregated view including seat list).
 */
public class Booking {
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy hh:mm:ss a");

    private int           bookingId;
    private String        userName;
    private int           showId;
    private LocalDateTime bookingTime;
    private List<String>  seatNumbers;  // populated after booking

    public Booking() {}

    public Booking(int bookingId, String userName, int showId,
                   LocalDateTime bookingTime) {
        this.bookingId   = bookingId;
        this.userName    = userName;
        this.showId      = showId;
        this.bookingTime = bookingTime;
    }

    // ── Getters ──────────────────────────────────────────────────────────────
    public int           getBookingId()   { return bookingId;   }
    public String        getUserName()    { return userName;    }
    public int           getShowId()      { return showId;      }
    public LocalDateTime getBookingTime() { return bookingTime; }
    public List<String>  getSeatNumbers() { return seatNumbers; }

    // ── Setters ──────────────────────────────────────────────────────────────
    public void setBookingId(int bookingId)         { this.bookingId   = bookingId;   }
    public void setUserName(String userName)         { this.userName    = userName;    }
    public void setShowId(int showId)               { this.showId      = showId;      }
    public void setBookingTime(LocalDateTime t)      { this.bookingTime = t;           }
    public void setSeatNumbers(List<String> seats)  { this.seatNumbers = seats;       }

    @Override
    public String toString() {
        return String.format("Booking #%d | User: %s | Show: %d | Time: %s | Seats: %s",
                bookingId, userName, showId,
                bookingTime != null ? bookingTime.format(FMT) : "N/A",
                seatNumbers);
    }
}
