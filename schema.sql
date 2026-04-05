-- Movie Ticket Booking System (MySQL 8+)
-- Run: CREATE DATABASE movie_ticket_booking; then use this DB and execute this file.

SET SQL_MODE = 'TRADITIONAL';

CREATE TABLE IF NOT EXISTS Movies (
    movie_id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    duration INT NOT NULL,
    genre VARCHAR(50) NOT NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS Shows (
    show_id INT PRIMARY KEY AUTO_INCREMENT,
    movie_id INT NOT NULL,
    show_time DATETIME NOT NULL,
    screen_no INT NOT NULL,
    CONSTRAINT fk_shows_movie
        FOREIGN KEY (movie_id) REFERENCES Movies(movie_id)
        ON DELETE CASCADE
) ENGINE=InnoDB;

-- A seat is tied to a show (so seat availability is per show_time).
CREATE TABLE IF NOT EXISTS Seats (
    seat_id INT PRIMARY KEY AUTO_INCREMENT,
    seat_number VARCHAR(10) NOT NULL,
    show_id INT NOT NULL,
    is_booked TINYINT(1) NOT NULL DEFAULT 0,
    CONSTRAINT fk_seats_show
        FOREIGN KEY (show_id) REFERENCES Shows(show_id)
        ON DELETE CASCADE,
    CONSTRAINT uq_show_seat_number UNIQUE (show_id, seat_number)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS Bookings (
    booking_id INT PRIMARY KEY AUTO_INCREMENT,
    user_name VARCHAR(100) NOT NULL,
    show_id INT NOT NULL,
    booking_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bookings_show
        FOREIGN KEY (show_id) REFERENCES Shows(show_id)
        ON DELETE CASCADE
) ENGINE=InnoDB;

-- Many-to-many: one booking can include multiple seats.
CREATE TABLE IF NOT EXISTS Booking_Seats (
    booking_id INT NOT NULL,
    seat_id INT NOT NULL,
    PRIMARY KEY (booking_id, seat_id),
    CONSTRAINT fk_booking_seats_booking
        FOREIGN KEY (booking_id) REFERENCES Bookings(booking_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_booking_seats_seat
        FOREIGN KEY (seat_id) REFERENCES Seats(seat_id)
        ON DELETE CASCADE
) ENGINE=InnoDB;

-- Helpful indexes (performance)
CREATE INDEX IF NOT EXISTS idx_shows_movie_id ON Shows(movie_id);
CREATE INDEX IF NOT EXISTS idx_seats_show_booked ON Seats(show_id, is_booked);
CREATE INDEX IF NOT EXISTS idx_booking_seats_booking_id ON Booking_Seats(booking_id);

