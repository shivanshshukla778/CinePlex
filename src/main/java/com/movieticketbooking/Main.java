package com.movieticketbooking;

import com.movieticketbooking.db.DBConnection;
import com.movieticketbooking.model.*;
import com.movieticketbooking.service.BookingService;
import com.movieticketbooking.util.TicketPrinter;

import java.util.*;

/**
 * Entry point for CinePlex Movie Ticket Booking System.
 *
 * Usage:
 *   java -jar movie-ticket-booking.jar           → console/CLI mode
 *   java -jar movie-ticket-booking.jar --serve   → starts REST API server at :8080
 */
public class Main {

    private static final BookingService service = new BookingService();
    private static final Scanner        scanner  = new Scanner(System.in);

    public static void main(String[] args) throws Exception {

        // ── Mode selection ────────────────────────────────────────────────────
        boolean serveMode = true; // ALWAYS start REST API server for live deployment

        if (serveMode) {
            System.out.println("\n  🌐  Starting CinePlex in SERVER mode...");
            ApiServer.start();
            // Keep main thread alive — server runs on its own executor threads
            Thread.currentThread().join();
            return;
        }

        // ── Console / CLI mode ────────────────────────────────────────────────
        printBanner();
        boolean running = true;
        while (running) {
            printMainMenu();
            int choice = readInt("Enter choice: ");
            switch (choice) {
                case 1 -> bookTicketFlow();
                case 2 -> viewBookingsFlow();
                case 3 -> {
                    System.out.println("\n  👋  Thank you for using CinePlex! Goodbye.\n");
                    running = false;
                }
                default -> System.out.println("  ⚠️  Invalid choice. Please try again.");
            }
        }

        DBConnection.closeConnection();
        scanner.close();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // FLOW 1 – Book a Ticket (CLI)
    // ══════════════════════════════════════════════════════════════════════════
    private static void bookTicketFlow() {
        List<Movie> movies = service.getAllMovies();
        if (movies.isEmpty()) { System.out.println("\n  ℹ️  No movies available.\n"); return; }

        System.out.println("\n╔══════════════════════════════════════════╗");
        System.out.println("║          🎬  AVAILABLE MOVIES            ║");
        System.out.println("╠══════════════════════════════════════════╣");
        for (Movie m : movies)
            System.out.printf("║  [%2d]  %-22s %s/%d min  ║%n",
                    m.getMovieId(), m.getName(), m.getGenre(), m.getDuration());
        System.out.println("╚══════════════════════════════════════════╝");

        int movieId = readInt("Select Movie ID (0 to cancel): ");
        if (movieId == 0) return;
        Movie movie = movies.stream().filter(m -> m.getMovieId() == movieId)
                            .findFirst().orElse(null);
        if (movie == null) { System.out.println("  ⚠️  Invalid Movie ID."); return; }

        // Shows
        List<Show> shows = service.getShowsForMovie(movieId);
        if (shows.isEmpty()) { System.out.println("\n  ℹ️  No shows for this movie.\n"); return; }
        System.out.println("\n  📅  Shows for \"" + movie.getName() + "\":");
        System.out.println("  ─────────────────────────────────────────");
        for (Show s : shows) System.out.println("  " + s);
        System.out.println();

        int showId = readInt("Select Show ID (0 to cancel): ");
        if (showId == 0) return;
        Show show = shows.stream().filter(s -> s.getShowId() == showId)
                         .findFirst().orElse(null);
        if (show == null) { System.out.println("  ⚠️  Invalid Show ID."); return; }

        // Seat map
        List<Seat> allSeats = service.getAllSeats(showId);
        if (allSeats.isEmpty()) { System.out.println("\n  ℹ️  No seats for this show.\n"); return; }
        printSeatGrid(allSeats);

        System.out.print("\n  Enter seat numbers (e.g. A1,A2,B3): ");
        String input = scanner.nextLine().trim().toUpperCase();
        if (input.isEmpty()) { System.out.println("  ⚠️  No seats entered."); return; }

        List<Integer> selectedIds = new ArrayList<>();
        for (String label : input.split(",")) {
            label = label.trim();
            final String lbl = label;
            Optional<Seat> opt = allSeats.stream()
                    .filter(s -> s.getSeatNumber().equalsIgnoreCase(lbl)).findFirst();
            if (opt.isEmpty())       { System.out.println("  ⚠️  Seat \"" + label + "\" not found."); return; }
            if (opt.get().isBooked()){ System.out.println("  ❌  Seat \"" + label + "\" is already booked!"); return; }
            selectedIds.add(opt.get().getSeatId());
        }

        System.out.print("  Enter your name: ");
        String userName = scanner.nextLine().trim();
        if (userName.isEmpty()) { System.out.println("  ⚠️  Name cannot be blank."); return; }

        System.out.printf("%n  📋  Confirm: %s | %s | Seats: %s%n", movie.getName(), show, input);
        System.out.print("  Confirm? (y/n): ");
        if (!scanner.nextLine().trim().equalsIgnoreCase("y")) {
            System.out.println("  ❌  Cancelled."); return;
        }

        try {
            Booking booking = service.bookSeats(userName, showId, selectedIds);
            System.out.println("\n  ✅  Booking Successful!");
            TicketPrinter.print(booking, movie, show);
        } catch (IllegalStateException e) {
            System.out.println("\n  ❌  " + e.getMessage());
        } catch (RuntimeException e) {
            System.out.println("\n  ❌  Error: " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // FLOW 2 – View Bookings
    // ══════════════════════════════════════════════════════════════════════════
    private static void viewBookingsFlow() {
        int showId = readInt("\n  Enter Show ID (0 to cancel): ");
        if (showId == 0) return;

        List<Booking> bookings = service.getBookingsForShow(showId);
        if (bookings.isEmpty()) {
            System.out.println("  ℹ️  No bookings for show #" + showId); return;
        }
        System.out.println("\n  🎟️  Bookings for Show #" + showId + ":");
        System.out.println("  ─────────────────────────────────────────────────");
        bookings.forEach(b -> System.out.println("  " + b));
        System.out.println();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════════════════════
    private static void printBanner() {
        System.out.println();
        System.out.println("  ╔════════════════════════════════════════════╗");
        System.out.println("  ║   🎬  CinePlex Movie Ticket Booking  🎬    ║");
        System.out.println("  ║         Powered by JDBC + MySQL            ║");
        System.out.println("  ╚════════════════════════════════════════════╝");
        System.out.println();
    }

    private static void printMainMenu() {
        System.out.println("  ┌─────────────────────────────┐");
        System.out.println("  │        MAIN MENU            │");
        System.out.println("  ├─────────────────────────────┤");
        System.out.println("  │  1. Book a Ticket           │");
        System.out.println("  │  2. View Bookings (by Show) │");
        System.out.println("  │  3. Exit                    │");
        System.out.println("  └─────────────────────────────┘");
    }

    private static void printSeatGrid(List<Seat> seats) {
        System.out.println("\n  🎭  SEAT MAP  (✅ available  ❌ booked)");
        System.out.println("  ──────────────────────────────────────────");
        System.out.println("            [ 🖥  S C R E E N ]");
        System.out.println("  ──────────────────────────────────────────");
        int i = 0;
        for (Seat s : seats) {
            if (i % 10 == 0) System.out.print("\n  ");
            System.out.printf("  %s%-3s", s.isBooked() ? "❌" : "✅", s.getSeatNumber());
            i++;
        }
        System.out.println("\n");
    }

    private static int readInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            try { return Integer.parseInt(scanner.nextLine().trim()); }
            catch (NumberFormatException e) { System.out.println("  ⚠️  Enter a valid number."); }
        }
    }
}
