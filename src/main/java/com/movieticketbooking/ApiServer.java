package com.movieticketbooking;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.movieticketbooking.dao.BookingDAO;
import com.movieticketbooking.dao.MovieDAO;
import com.movieticketbooking.dao.SeatDAO;
import com.movieticketbooking.dao.ShowDAO;
import com.movieticketbooking.model.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;

/**
 * Lightweight REST API server built on Java's built-in HttpServer.
 * No external server (Tomcat/Jetty) needed.
 *
 * Routes:
 *   GET  /                         → serves index.html
 *   GET  /<file>.css|.js           → serves static asset
 *   GET  /api/movies               → all movies
 *   GET  /api/shows?movieId=N      → shows for a movie (with available_count)
 *   GET  /api/seats?showId=N       → all seats for a show
 *   POST /api/book                 → book seats (ACID transaction)
 *   GET  /api/bookings             → all bookings with details
 */
public class ApiServer {

    public static final int    PORT       = 8080;
    private static final Gson   GSON       = new GsonBuilder().create();
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    // ── DAO / Service singletons ──────────────────────────────────────────────
    private static final MovieDAO   movieDAO   = new MovieDAO();
    private static final ShowDAO    showDAO    = new ShowDAO();
    private static final SeatDAO    seatDAO    = new SeatDAO();
    private static final BookingDAO bookingDAO = new BookingDAO();

    // ── Webapp dir (looked up at startup) ────────────────────────────────────
    private static Path webappDir;

    // ══════════════════════════════════════════════════════════════════════════
    public static void start() throws IOException {
        // Locate src/main/webapp relative to working directory
        Path candidate = Path.of("src", "main", "webapp");
        if (Files.isDirectory(candidate) && Files.exists(candidate.resolve("index.html"))) {
            webappDir = candidate.toAbsolutePath();
        } else {
            // Fallback: next to the JAR or root
            webappDir = Path.of(".").toAbsolutePath();
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // API routes (registered first – more specific wins)
        server.createContext("/api/movies",   ex -> handle(ex, ApiServer::handleMovies));
        server.createContext("/api/shows",    ex -> handle(ex, ApiServer::handleShows));
        server.createContext("/api/seats",    ex -> handle(ex, ApiServer::handleSeats));
        server.createContext("/api/book",     ex -> handle(ex, ApiServer::handleBook));
        server.createContext("/api/bookings", ex -> handle(ex, ApiServer::handleBookings));

        // Static file catch-all
        server.createContext("/", ex -> handle(ex, ApiServer::handleStatic));

        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();

        System.out.println();
        System.out.println("  ╔════════════════════════════════════════════╗");
        System.out.println("  ║  🚀  CinePlex REST Server  –  RUNNING     ║");
        System.out.println("  ║  URL : http://localhost:" + PORT + "/            ║");
        System.out.println("  ║  API : http://localhost:" + PORT + "/api/movies  ║");
        System.out.println("  ║  Press Ctrl+C to stop                      ║");
        System.out.println("  ╚════════════════════════════════════════════╝");
        System.out.println();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Handler dispatch wrapper – adds CORS, catches exceptions
    // ══════════════════════════════════════════════════════════════════════════
    @FunctionalInterface
    interface RouteHandler { void handle(HttpExchange ex) throws Exception; }

    private static void handle(HttpExchange ex, RouteHandler handler) {
        // CORS – allow any origin (dev convenience)
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            try { ex.sendResponseHeaders(204, -1); } catch (IOException ignored) {}
            return;
        }

        try {
            handler.handle(ex);
        } catch (IllegalStateException ise) {
            // Double-booking / business error → 409 Conflict
            sendJson(ex, 409, error(ise.getMessage()));
        } catch (IllegalArgumentException iae) {
            sendJson(ex, 400, error(iae.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            sendJson(ex, 500, error("Internal server error: " + e.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // API Handlers
    // ══════════════════════════════════════════════════════════════════════════

    /** GET /api/movies  →  [{movie_id, name, duration, genre}, ...] */
    private static void handleMovies(HttpExchange ex) throws Exception {
        List<Movie> movies = movieDAO.getAllMovies();

        // Convert to plain maps for lean JSON (no extra fields)
        List<Map<String, Object>> result = new ArrayList<>();
        for (Movie m : movies) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("movie_id", m.getMovieId());
            map.put("name",     m.getName());
            map.put("duration", m.getDuration());
            map.put("genre",    m.getGenre());
            result.add(map);
        }
        sendJson(ex, 200, GSON.toJson(result));
    }

    /** GET /api/shows?movieId=N  →  [{show_id, movie_id, show_time, screen_no, available_count}, ...] */
    private static void handleShows(HttpExchange ex) throws Exception {
        int movieId = intParam(ex, "movieId");
        List<Show> shows = showDAO.getShowsByMovieId(movieId);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Show s : shows) {
            int available = seatDAO.getAvailableCount(s.getShowId());
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("show_id",         s.getShowId());
            map.put("movie_id",        s.getMovieId());
            map.put("show_time",       s.getShowTime().format(ISO));
            map.put("screen_no",       s.getScreenNo());
            map.put("available_count", available);
            result.add(map);
        }
        sendJson(ex, 200, GSON.toJson(result));
    }

    /** GET /api/seats?showId=N  →  [{seat_id, seat_number, show_id, is_booked}, ...] */
    private static void handleSeats(HttpExchange ex) throws Exception {
        int showId = intParam(ex, "showId");
        List<Seat> seats = seatDAO.getSeatsByShowId(showId);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Seat s : seats) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("seat_id",     s.getSeatId());
            map.put("seat_number", s.getSeatNumber());
            map.put("show_id",     s.getShowId());
            map.put("is_booked",   s.isBooked());
            result.add(map);
        }
        sendJson(ex, 200, GSON.toJson(result));
    }

    /**
     * POST /api/book
     * Body: { "userName": "...", "showId": 1, "seatIds": [1,2,3] }
     * Response: { "success": true, "booking": { ... } }
     */
    private static void handleBook(HttpExchange ex) throws Exception {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, error("Use POST for /api/book"));
            return;
        }

        // Parse JSON body
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        JsonObject req = JsonParser.parseString(body).getAsJsonObject();

        String userName = req.get("userName").getAsString().trim();
        int    showId   = req.get("showId").getAsInt();

        List<Integer> seatIds = new ArrayList<>();
        req.getAsJsonArray("seatIds").forEach(e -> seatIds.add(e.getAsInt()));

        if (userName.isEmpty())  throw new IllegalArgumentException("userName cannot be empty.");
        if (seatIds.isEmpty())   throw new IllegalArgumentException("seatIds cannot be empty.");

        // ── ACID transaction (delegates to BookingDAO) ────────────────────────
        Booking booking = bookingDAO.bookSeats(userName, showId, seatIds);

        // Build response
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true);

        Map<String, Object> bMap = new LinkedHashMap<>();
        bMap.put("booking_id",   booking.getBookingId());
        bMap.put("user_name",    booking.getUserName());
        bMap.put("show_id",      booking.getShowId());
        bMap.put("booking_time", booking.getBookingTime().format(ISO));
        bMap.put("seat_numbers", booking.getSeatNumbers());
        resp.put("booking", bMap);

        sendJson(ex, 201, GSON.toJson(resp));
    }

    /** GET /api/bookings  →  all bookings with movie & show details */
    private static void handleBookings(HttpExchange ex) throws Exception {
        List<BookingDetail> details = bookingDAO.getAllBookingsWithDetails();
        sendJson(ex, 200, GSON.toJson(details));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Static File Server
    // ══════════════════════════════════════════════════════════════════════════
    private static final Map<String, String> MIME = Map.of(
        "html", "text/html; charset=utf-8",
        "css",  "text/css; charset=utf-8",
        "js",   "application/javascript; charset=utf-8",
        "ico",  "image/x-icon",
        "png",  "image/png"
    );

    private static void handleStatic(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/") || path.isEmpty()) path = "/index.html";

        // Block path traversal
        if (path.contains("..")) { ex.sendResponseHeaders(403, -1); return; }

        Path file = webappDir.resolve(path.substring(1)).normalize();
        if (!file.startsWith(webappDir) || !Files.isRegularFile(file)) {
            byte[] body = "404 Not Found".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(404, body.length);
            ex.getResponseBody().write(body);
            ex.getResponseBody().close();
            return;
        }

        String ext  = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : "html";
        String mime = MIME.getOrDefault(ext, "application/octet-stream");

        byte[] data = Files.readAllBytes(file);
        ex.getResponseHeaders().set("Content-Type", mime);
        ex.sendResponseHeaders(200, data.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(data); }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════════════════════
    private static void sendJson(HttpExchange ex, int status, String json) {
        try {
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            ex.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String error(String message) {
        return GSON.toJson(Map.of("success", false, "error", message));
    }

    /** Parse a required integer query param. Throws 400 if missing/invalid. */
    private static int intParam(HttpExchange ex, String name) {
        String query = ex.getRequestURI().getRawQuery();
        if (query == null) throw new IllegalArgumentException("Missing query param: " + name);
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals(name)) {
                try { return Integer.parseInt(URLDecoder.decode(kv[1], StandardCharsets.UTF_8)); }
                catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid " + name); }
            }
        }
        throw new IllegalArgumentException("Missing query param: " + name);
    }
}
