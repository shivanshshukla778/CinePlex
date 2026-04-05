# 🎬 CinePlex — Movie Ticket Booking System

A full-stack Movie Ticket Booking system built with **Java 17 + JDBC + MySQL 8** on the backend and a **cinematic HTML/CSS/JS SPA** on the frontend.

---

## 📁 Project Structure

```
MOVIE TICKET BOOKING/
├── pom.xml                          # Maven build (Java 17, MySQL Connector 9.6)
├── schema.sql                       # Full MySQL schema (DDL)
├── seed.sql                         # Sample data seeder
└── src/
    └── main/
        ├── java/com/movieticketbooking/
        │   ├── Main.java            # Console app entry point
        │   ├── db/
        │   │   └── DBConnection.java       # Singleton JDBC connection
        │   ├── model/
        │   │   ├── Movie.java
        │   │   ├── Show.java
        │   │   ├── Seat.java
        │   │   └── Booking.java
        │   ├── dao/
        │   │   ├── MovieDAO.java           # DB queries for Movies
        │   │   ├── ShowDAO.java            # DB queries for Shows
        │   │   ├── SeatDAO.java            # Seat availability + mark booked
        │   │   └── BookingDAO.java         # ACID transaction for booking
        │   ├── service/
        │   │   └── BookingService.java     # Business logic layer
        │   └── util/
        │       └── TicketPrinter.java      # Console ticket renderer
        └── webapp/
            ├── index.html           # SPA frontend
            ├── style.css            # Cinematic dark theme
            └── app.js               # Full JS logic + simulated DB
```

---

## 🗄️ Database Setup (MySQL 8+)

```sql
-- 1. Create database
CREATE DATABASE movie_ticket_booking;
USE movie_ticket_booking;

-- 2. Run schema
SOURCE schema.sql;

-- 3. Seed sample data
SOURCE seed.sql;
```

---

## ☕ Running the Java Console App

### Prerequisites
- Java 17+
- Maven 3.8+
- MySQL 8 running locally

### Configure DB credentials
Edit `src/main/java/com/movieticketbooking/db/DBConnection.java`:
```java
private static final String DB_URL  = "jdbc:mysql://localhost:3306/movie_ticket_booking?...";
private static final String USER     = "root";     // ← your MySQL user
private static final String PASSWORD = "root";     // ← your MySQL password
```

### Build & Run
```bash
# Build fat JAR
mvn clean package

# Run
java -jar target/movie-ticket-booking-1.0.0-SNAPSHOT.jar
```

### Console Flow
```
1. Browse Movies  →  select movie_id
2. Browse Shows   →  select show_id
3. View seat grid (✅ available / ❌ booked)
4. Enter seat numbers  e.g.  A1,A2,B5
5. Enter your name
6. Confirm  →  Booking committed (JDBC transaction)
7. Ticket printed to console 🎟
```

---

## 🌐 Running the Web App (No Server Needed)

Just **open `index.html`** in your browser:

```
src/main/webapp/index.html
```

> Data is stored in **localStorage** — mirrors the MySQL schema exactly.
> Works offline. No server required.

### Web Features
| Feature | Details |
|---|---|
| Movie Catalog | 6 movies with genre, duration, poster |
| Show Selection | Multiple shows per movie with live seat count |
| Interactive Seat Map | Rows A–E × 10 cols, click to select |
| ACID Transaction | Double-booking prevention (optimistic lock) |
| Ticket Generation | Printable ticket with barcode |
| Booking History | View all past bookings in modal |
| Responsive | Works on mobile, tablet, desktop |
| Print-ready | `Ctrl+P` prints the ticket cleanly |

---

## 🔄 System Flow (mirrors Java JDBC)

```
User
 │
 ▼
SELECT movie   → MovieDAO.getAllMovies()
 │
 ▼
SELECT show    → ShowDAO.getShowsByMovieId(movieId)
 │
 ▼
SELECT seats   → SeatDAO.getAvailableSeats(showId)
 │
 ▼
BOOK           → BookingDAO.bookSeats()  ← ACID Transaction
                  ┌─ START TRANSACTION
                  ├─ SELECT … FOR UPDATE  (lock check)
                  ├─ INSERT INTO Bookings
                  ├─ INSERT INTO Booking_Seats
                  ├─ UPDATE Seats SET is_booked=TRUE
                  └─ COMMIT  (or ROLLBACK on error)
 │
 ▼
TICKET         → TicketPrinter.print()
```

---

## 📌 Key Concepts Demonstrated

| Concept | Implementation |
|---|---|
| **ACID Transactions** | `con.setAutoCommit(false)` → commit / rollback |
| **Double Booking Prevention** | `SELECT … FOR UPDATE` + exception on `is_booked=TRUE` |
| **Many-to-Many** | `Booking_Seats` junction table |
| **DAO Pattern** | Separate DAO class per entity |
| **Service Layer** | `BookingService` orchestrates DAOs |
| **Prepared Statements** | All queries use `PreparedStatement` (SQL injection safe) |
| **Batch Insert** | `ps.addBatch()` for bulk seat creation |

---

## 🎟️ Sample Console Ticket Output

```
╔════════════════════════════════════════════════════╗
║           🎬  CINEPLEX  –  YOUR TICKET             ║
╠════════════════════════════════════════════════════╣
║                                                    ║
║  🎥  MOVIE   : Inception                           ║
║  🎭  GENRE   : Sci-Fi                              ║
║  ⏱️  DURATION : 148 minutes                         ║
║                                                    ║
║  📅  SHOW    : Thursday, 03 April 2026  |  07:00 PM ║
║  🏛️  SCREEN  : Screen 1                            ║
║                                                    ║
║  🪑  SEATS   : A1  A2  B3                          ║
║                                                    ║
║  👤  GUEST   : Shivansh Shukla                     ║
║  🎟️  BOOKING : #1001                               ║
║  🕐  BOOKED  : 03-Apr-2026 22:45:12               ║
║                                                    ║
║                  ENJOY THE SHOW!  🍿               ║
╚════════════════════════════════════════════════════╝
```

---

*Built with ☕ Java 17 · JDBC · MySQL 8 · Vanilla HTML/CSS/JS*
