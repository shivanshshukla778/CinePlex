-- ═══════════════════════════════════════════════════════════════
-- CinePlex  –  Comprehensive Seed Data  (MySQL 8+)
-- Run AFTER schema.sql:
--   USE movie_ticket_booking;
--   SOURCE seed.sql;
-- ═══════════════════════════════════════════════════════════════

START TRANSACTION;

-- ── 1. Movies ────────────────────────────────────────────────
INSERT INTO Movies (name, duration, genre) VALUES
  ('Inception',          148, 'Sci-Fi'),
  ('Interstellar',       169, 'Sci-Fi'),
  ('The Dark Knight',    152, 'Action'),
  ('Avengers: Endgame',  181, 'Superhero'),
  ('Dune: Part Two',     167, 'Fantasy'),
  ('Oppenheimer',        181, 'Drama');

-- ── 2. Shows ─────────────────────────────────────────────────
-- (2-3 shows per movie on 2026-04-03)
INSERT INTO Shows (movie_id, show_time, screen_no) VALUES
  -- Inception  (movie_id = 1)
  (1, '2026-04-03 10:00:00', 1),
  (1, '2026-04-03 14:30:00', 2),
  (1, '2026-04-03 19:00:00', 1),
  -- Interstellar (movie_id = 2)
  (2, '2026-04-03 11:00:00', 3),
  (2, '2026-04-03 17:00:00', 3),
  -- The Dark Knight (movie_id = 3)
  (3, '2026-04-03 09:30:00', 2),
  (3, '2026-04-03 16:00:00', 4),
  -- Avengers: Endgame (movie_id = 4)
  (4, '2026-04-03 12:00:00', 1),
  (4, '2026-04-03 20:00:00', 2),
  -- Dune: Part Two (movie_id = 5)
  (5, '2026-04-03 13:30:00', 4),
  (5, '2026-04-03 18:30:00', 3),
  -- Oppenheimer (movie_id = 6)
  (6, '2026-04-03 15:00:00', 2),
  (6, '2026-04-03 21:00:00', 1);

-- ── 3. Seats (A1-E10 = 50 seats per show)  ───────────────────
-- Uses a stored procedure so we don't write 650 INSERT rows.

DROP PROCEDURE IF EXISTS create_seats;

DELIMITER //
CREATE PROCEDURE create_seats()
BEGIN
  DECLARE done      INT DEFAULT 0;
  DECLARE cur_show  INT;
  DECLARE row_name  CHAR(1);
  DECLARE col_num   INT;

  DECLARE show_cur CURSOR FOR SELECT show_id FROM Shows;
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

  OPEN show_cur;
  show_loop: LOOP
    FETCH show_cur INTO cur_show;
    IF done THEN LEAVE show_loop; END IF;

    SET row_name = 'A';
    row_loop: LOOP
      SET col_num = 1;
      col_loop: LOOP
        INSERT INTO Seats (seat_number, show_id)
        VALUES (CONCAT(row_name, col_num), cur_show);
        SET col_num = col_num + 1;
        IF col_num > 10 THEN LEAVE col_loop; END IF;
      END LOOP col_loop;

      SET row_name = CASE row_name
        WHEN 'A' THEN 'B'
        WHEN 'B' THEN 'C'
        WHEN 'C' THEN 'D'
        WHEN 'D' THEN 'E'
        ELSE NULL
      END;
      IF row_name IS NULL THEN LEAVE row_loop; END IF;
    END LOOP row_loop;
  END LOOP show_loop;

  CLOSE show_cur;
END //
DELIMITER ;

CALL create_seats();
DROP PROCEDURE IF EXISTS create_seats;

-- ── 4. Pre-book some seats to make the demo look realistic ────
-- Show 1 (Inception 10:00) – book A1,A2,A3,B5,B6,C7
UPDATE Seats SET is_booked = TRUE
WHERE show_id = 1 AND seat_number IN ('A1','A2','A3','B5','B6','C7');

-- Show 6 (Dark Knight 09:30) – busy morning show
UPDATE Seats SET is_booked = TRUE
WHERE show_id = 6 AND seat_number IN ('A1','A2','A3','A4','A5','A6','A7','A8','A9','A10',
                                       'B1','B2','B3','B4','B5','B6','B7','B8',
                                       'C1','C2','C3','C4');

-- Show 8 (Avengers 12:00) – house full almost
UPDATE Seats SET is_booked = TRUE
WHERE show_id = 8 AND seat_number IN ('A1','A2','A3','A4','A5','A6','A7','A8','A9','A10',
                                       'B1','B2','B3','B4','B5','B6','B7','B8','B9','B10',
                                       'C1','C2','C3','C4','C5','C6','C7','C8','C9','C10',
                                       'D1','D2','D3','D4','D5','D6','D7','D8','D9','D10',
                                       'E1','E2','E3','E4','E5','E6','E7','E8');

-- ── 5. Sample bookings (so history modal has data) ────────────
INSERT INTO Bookings (user_name, show_id, booking_time) VALUES
  ('Shivansh Shukla', 1, '2026-04-02 10:15:00'),
  ('Priya Verma',     6, '2026-04-02 11:30:00'),
  ('Rahul Singh',     8, '2026-04-02 12:00:00');

-- Booking_Seats for the above (seat_ids depend on insert order;
-- adjust if your AUTO_INCREMENT differs)
INSERT INTO Booking_Seats (booking_id, seat_id)
SELECT b.booking_id, s.seat_id
FROM   Bookings b
JOIN   Seats    s ON s.show_id = b.show_id
WHERE (b.user_name = 'Shivansh Shukla' AND s.seat_number IN ('A1','A2','A3'))
   OR (b.user_name = 'Priya Verma'     AND s.seat_number IN ('A1','A2'))
   OR (b.user_name = 'Rahul Singh'     AND s.seat_number IN ('A1','A2','A3','A4'));

COMMIT;
