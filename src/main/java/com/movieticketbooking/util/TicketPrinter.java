package com.movieticketbooking.util;

import com.movieticketbooking.model.Booking;
import com.movieticketbooking.model.Movie;
import com.movieticketbooking.model.Show;

import java.time.format.DateTimeFormatter;

/**
 * Generates a formatted text ticket after a successful booking.
 * Can be printed to console or saved to file.
 */
public class TicketPrinter {

    private static final String LINE   = "═".repeat(52);
    private static final String DASHED = "─".repeat(52);
    private static final DateTimeFormatter SHOW_FMT =
            DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy  |  hh:mm a");
    private static final DateTimeFormatter BOOKED_FMT =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss");

    private TicketPrinter() {}

    /**
     * Print the ticket to System.out.
     *
     * @param booking  confirmed booking object
     * @param movie    the movie being watched
     * @param show     the selected show
     */
    public static void print(Booking booking, Movie movie, Show show) {
        System.out.println();
        System.out.println("╔" + LINE + "╗");
        System.out.println("║" + centre("🎬  CINEPLEX  –  YOUR TICKET", 52) + "║");
        System.out.println("╠" + LINE + "╣");
        System.out.println("║" + blank(52) + "║");
        System.out.println("║  🎥  MOVIE   : " + padRight(movie.getName(), 34) + "║");
        System.out.println("║  🎭  GENRE   : " + padRight(movie.getGenre(), 34) + "║");
        System.out.println("║  ⏱️  DURATION : " + padRight(movie.getDuration() + " minutes", 33) + "║");
        System.out.println("║" + blank(52) + "║");
        System.out.println("║  " + DASHED + "  ║".substring(0, 2));
        System.out.println("║" + blank(52) + "║");
        System.out.println("║  📅  SHOW    : " + padRight(show.getShowTime().format(SHOW_FMT), 34) + "║");
        System.out.println("║  🏛️  SCREEN  : " + padRight("Screen " + show.getScreenNo(), 34) + "║");
        System.out.println("║" + blank(52) + "║");
        System.out.println("║  🪑  SEATS   : " + padRight(String.join("  ", booking.getSeatNumbers()), 34) + "║");
        System.out.println("║" + blank(52) + "║");
        System.out.println("║  " + DASHED + "  ║".substring(0, 2));
        System.out.println("║" + blank(52) + "║");
        System.out.println("║  👤  GUEST   : " + padRight(booking.getUserName(), 34) + "║");
        System.out.println("║  🎟️  BOOKING : " + padRight("#" + booking.getBookingId(), 34) + "║");
        System.out.println("║  🕐  BOOKED  : " + padRight(
                booking.getBookingTime().format(BOOKED_FMT), 34) + "║");
        System.out.println("║" + blank(52) + "║");
        System.out.println("║" + centre("ENJOY THE SHOW!  🍿", 52) + "║");
        System.out.println("╚" + LINE + "╝");
        System.out.println();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private static String centre(String text, int width) {
        int padding = (width - text.length()) / 2;
        return " ".repeat(Math.max(0, padding)) + text +
               " ".repeat(Math.max(0, width - text.length() - padding));
    }

    private static String padRight(String text, int width) {
        if (text == null) text = "";
        if (text.length() > width) text = text.substring(0, width - 3) + "...";
        return text + " ".repeat(width - text.length());
    }

    private static String blank(int width) {
        return " ".repeat(width);
    }
}
