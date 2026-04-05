/**
 * CinePlex – Movie Ticket Booking System
 * app.js  |  Frontend connected to Java REST API (http://localhost:8080/api/*)
 *
 * Architecture:
 *   API{}     → thin fetch() wrapper for every REST endpoint
 *   State{}   → application state (selected movie/show/seats)
 *   render*() → pure UI functions (async, await API calls)
 *   Event-listeners at the bottom wire everything together
 */

'use strict';

/* ════════════════════════════════════════════════════════════════════
   1. CONSTANTS
   ════════════════════════════════════════════════════════════════════ */

const BASE = '';          // same-origin (served by ApiServer at :8080)
const SEAT_PRICE = 220;   // ₹ per seat

/** Visual metadata for each movie — matched by movie_id from DB seed order */
const MOVIE_VISUALS = {
  1: { icon: '🌀', c1: '#1e3a5f', c2: '#0f2040' },   // Inception
  2: { icon: '🪐', c1: '#0d2137', c2: '#1a3a5c' },   // Interstellar
  3: { icon: '🦇', c1: '#1a1a2e', c2: '#16213e' },   // The Dark Knight
  4: { icon: '⚡', c1: '#3a1d1d', c2: '#6b1111' },   // Avengers
  5: { icon: '🏜️', c1: '#3d2b00', c2: '#6b4400' },  // Dune
  6: { icon: '☢️', c1: '#2b1a00', c2: '#4a2e00' },  // Oppenheimer
};

/* ════════════════════════════════════════════════════════════════════
   2. REST API CLIENT
   ════════════════════════════════════════════════════════════════════ */

const API = {

  async _get(path) {
    const res = await fetch(BASE + path);
    if (!res.ok) {
      const err = await res.json().catch(() => ({ error: res.statusText }));
      throw new Error(err.error || res.statusText);
    }
    return res.json();
  },

  async _post(path, body) {
    const res = await fetch(BASE + path, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
    const data = await res.json().catch(() => ({ error: res.statusText }));
    if (!res.ok) throw new Error(data.error || res.statusText);
    return data;
  },

  /** GET /api/movies → [{movie_id, name, duration, genre}, ...] */
  getMovies() { return this._get('/api/movies'); },

  /** GET /api/shows?movieId=N → [{show_id, movie_id, show_time, screen_no, available_count}, ...] */
  getShows(movieId) { return this._get(`/api/shows?movieId=${movieId}`); },

  /** GET /api/seats?showId=N → [{seat_id, seat_number, show_id, is_booked}, ...] */
  getSeats(showId) { return this._get(`/api/seats?showId=${showId}`); },

  /**
   * POST /api/book
   * Body: { userName, showId, seatIds:[...] }
   * Returns: { success:true, booking:{ booking_id, user_name, show_id, booking_time, seat_numbers } }
   */
  bookSeats(userName, showId, seatIds) {
    return this._post('/api/book', { userName, showId, seatIds });
  },

  /** GET /api/bookings → all bookings with embedded show/movie details */
  getAllBookings() { return this._get('/api/bookings'); },
};

/* ════════════════════════════════════════════════════════════════════
   3. APPLICATION STATE
   ════════════════════════════════════════════════════════════════════ */

const state = {
  movies:        [],          // cached from API
  selectedMovie: null,        // { movie_id, name, duration, genre, + visuals }
  selectedShow:  null,        // { show_id, movie_id, show_time, screen_no, available_count }
  allSeats:      [],          // seats for the selected show
  selectedSeats: [],          // [{seat_id, seat_number}] — user picks
  lastBooking:   null,        // result from /api/book
};

/* ════════════════════════════════════════════════════════════════════
   4. DOM HELPERS
   ════════════════════════════════════════════════════════════════════ */

const $ = id => document.getElementById(id);
const VIEWS = ['movies', 'shows', 'seats', 'confirm', 'ticket'];

function showView(name) {
  VIEWS.forEach(v => $(`view-${v}`).classList.toggle('active', v === name));

  // Step indicator
  const idx = VIEWS.indexOf(name) + 1;
  for (let i = 1; i <= 4; i++) {
    const dot  = $(`step-nav-${i}`);
    const line = dot?.nextElementSibling;
    if (i < idx)       { dot.className = 'step done'; }
    else if (i === idx){ dot.className = 'step active'; }
    else               { dot.className = 'step'; }
    if (line?.classList.contains('step-line'))
      line.classList.toggle('done', i < idx);
  }
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

/* ── Loading overlay ─────────────────────────────────────────── */
function setLoading(viewId, on) {
  const el = $(viewId);
  if (!el) return;
  el.style.opacity = on ? '0.4' : '1';
  el.style.pointerEvents = on ? 'none' : '';
}

/* ── Toast ───────────────────────────────────────────────────── */
let _toastTimer;
function toast(msg, type = '') {
  const el = $('toast');
  el.textContent = msg;
  el.className = `toast show ${type}`;
  clearTimeout(_toastTimer);
  _toastTimer = setTimeout(() => el.classList.remove('show'), 3500);
}

/* ── Date helpers ────────────────────────────────────────────── */
function fmtDateTime(iso) {
  const d = new Date(iso);
  return {
    date: d.toLocaleDateString('en-IN', { weekday:'short', year:'numeric', month:'short', day:'numeric' }),
    time: d.toLocaleTimeString('en-IN', { hour:'2-digit', minute:'2-digit', hour12: true }),
    full: d.toLocaleString('en-IN', { weekday:'short', day:'numeric', month:'short', year:'numeric', hour:'2-digit', minute:'2-digit', hour12:true }),
  };
}

/* ── Enrich movie with visual data ───────────────────────────── */
function enrichMovie(m) {
  const vis = MOVIE_VISUALS[m.movie_id] || { icon: '🎬', c1: '#1a1d35', c2: '#0f1120' };
  return { ...m, ...vis };
}

/* ════════════════════════════════════════════════════════════════════
   5. RENDER FUNCTIONS
   ════════════════════════════════════════════════════════════════════ */

/* ── STEP 1: Movies ──────────────────────────────────────────── */
async function renderMovies() {
  setLoading('view-movies', true);
  try {
    const movies = await API.getMovies();
    state.movies = movies.map(enrichMovie);

    const grid = $('movieGrid');
    grid.innerHTML = state.movies.map(m => {
      const showCount = '–';   // not fetched per-movie to keep it fast
      const rating    = (7.5 + (m.movie_id * 0.3)).toFixed(1);
      return `
        <div class="movie-card" id="mc-${m.movie_id}" data-id="${m.movie_id}" role="button" tabindex="0" aria-label="Select ${m.name}">
          <div class="movie-poster">
            <div class="movie-poster-bg" style="--c1:${m.c1};--c2:${m.c2}"></div>
            <span class="movie-poster-icon">${m.icon}</span>
            <span class="movie-badge">⭐ ${rating}</span>
          </div>
          <div class="movie-body">
            <div class="movie-title">${m.name}</div>
            <div class="movie-meta">
              <span class="genre-pill">${m.genre}</span>
              <span class="movie-duration">⏱ ${m.duration} min</span>
            </div>
          </div>
          <button class="movie-select-btn" data-id="${m.movie_id}">Select Movie →</button>
        </div>`;
    }).join('');

    grid.querySelectorAll('[data-id]').forEach(el => {
      el.addEventListener('click',   () => selectMovie(+el.dataset.id));
      el.addEventListener('keydown', e => e.key === 'Enter' && selectMovie(+el.dataset.id));
    });
  } catch (err) {
    $('movieGrid').innerHTML = `
      <div style="grid-column:1/-1;text-align:center;padding:48px;color:var(--red-seat)">
        <div style="font-size:48px;margin-bottom:16px">⚠️</div>
        <b>Cannot connect to CinePlex API</b><br/>
        <span style="color:var(--text-secondary);font-size:14px">
          Make sure the Java server is running:<br/>
          <code style="color:var(--violet-light)">java -jar movie-ticket-booking.jar --serve</code>
        </span>
      </div>`;
    toast('❌ API unreachable — is the Java server running?', 'error');
  } finally {
    setLoading('view-movies', false);
  }
}

/* ── STEP 2: Shows ───────────────────────────────────────────── */
async function renderShows(movieId) {
  const movie = state.selectedMovie;
  $('showsMovieTitle').textContent = `${movie.icon}  ${movie.name}`;
  $('showsMovieMeta').textContent  = `${movie.genre}  •  ${movie.duration} min  •  Choose a show time`;
  $('showsGrid').innerHTML = '<div style="color:var(--text-secondary);padding:32px">Loading shows…</div>';

  setLoading('view-shows', true);
  try {
    const shows = await API.getShows(movieId);
    state.selectedShow = null;

    if (shows.length === 0) {
      $('showsGrid').innerHTML = `<p style="color:var(--text-secondary)">No shows scheduled for this movie.</p>`;
      return;
    }

    $('showsGrid').innerHTML = shows.map(s => {
      const dt          = fmtDateTime(s.show_time);
      const avail       = s.available_count;
      const availClass  = avail === 0 ? 'full' : avail < 10 ? 'low' : '';
      const availText   = avail === 0 ? '❌ House Full'
                        : avail < 10  ? `⚠️ Only ${avail} left`
                        : `✅ ${avail} seats available`;
      const disabled    = avail === 0;
      return `
        <div class="show-card" data-id="${s.show_id}" data-time="${s.show_time}"
             data-screen="${s.screen_no}" data-avail="${avail}"
             role="button" tabindex="${disabled ? -1 : 0}"
             style="${disabled ? 'opacity:.45;cursor:not-allowed;pointer-events:none' : ''}">
          <div class="show-time">${dt.time}</div>
          <div class="show-date">${dt.date}</div>
          <div class="show-screen">Screen ${s.screen_no}</div>
          <div class="show-avail ${availClass}">${availText}</div>
        </div>`;
    }).join('');

    $('showsGrid').querySelectorAll('.show-card:not([style*="not-allowed"])').forEach(el => {
      const show = shows.find(s => s.show_id === +el.dataset.id);
      el.addEventListener('click', () => selectShow(show));
      el.addEventListener('keydown', e => e.key === 'Enter' && selectShow(show));
    });
  } catch (err) {
    toast('❌ Failed to load shows: ' + err.message, 'error');
  } finally {
    setLoading('view-shows', false);
  }
}

/* ── STEP 3: Seat Map ────────────────────────────────────────── */
async function renderSeats(showId) {
  const movie = state.selectedMovie;
  const show  = state.selectedShow;
  const dt    = fmtDateTime(show.show_time);

  $('seatsTitle').textContent = `🪑 Choose Seats — ${movie.name}`;
  $('seatsMeta').textContent  = `${dt.full}  •  Screen ${show.screen_no}  •  ₹${SEAT_PRICE} per seat`;
  $('seatGrid').innerHTML     = '<div style="color:var(--text-secondary);padding:20px">Loading seat map…</div>';

  setLoading('view-seats', true);
  try {
    const seats  = await API.getSeats(showId);
    state.allSeats      = seats;
    state.selectedSeats = [];

    // Build grid grouped by row letter
    const rows = {};
    seats.forEach(s => {
      const row = s.seat_number[0];
      (rows[row] = rows[row] || []).push(s);
    });

    const grid = $('seatGrid');
    grid.innerHTML = '';

    Object.entries(rows).forEach(([row, rowSeats]) => {
      const label = document.createElement('div');
      label.className = 'seat-row-label';
      label.textContent = row;
      grid.appendChild(label);

      rowSeats.forEach(s => {
        const el = document.createElement('div');
        el.className  = `seat ${s.is_booked ? 'booked' : 'available'}`;
        el.id         = `seat-${s.seat_id}`;
        el.dataset.id = s.seat_id;
        el.dataset.number = s.seat_number;
        el.textContent = s.seat_number.slice(1);
        el.setAttribute('role', 'button');
        el.setAttribute('tabindex', s.is_booked ? '-1' : '0');
        el.setAttribute('aria-label', `${s.seat_number}, ${s.is_booked ? 'booked' : 'available'}`);
        el.setAttribute('aria-pressed', 'false');
        if (!s.is_booked) {
          el.addEventListener('click', () => toggleSeat(s, el));
          el.addEventListener('keydown', e => e.key === 'Enter' && toggleSeat(s, el));
        }
        grid.appendChild(el);
      });
    });

    updateSelectionPanel();
  } catch (err) {
    toast('❌ Failed to load seats: ' + err.message, 'error');
  } finally {
    setLoading('view-seats', false);
  }
}

function toggleSeat(seat, el) {
  const idx = state.selectedSeats.findIndex(s => s.seat_id === seat.seat_id);
  if (idx === -1) {
    if (state.selectedSeats.length >= 8) { toast('Maximum 8 seats per booking', 'error'); return; }
    state.selectedSeats.push({ seat_id: seat.seat_id, seat_number: seat.seat_number });
    el.classList.replace('available', 'selected');
    el.setAttribute('aria-pressed', 'true');
  } else {
    state.selectedSeats.splice(idx, 1);
    el.classList.replace('selected', 'available');
    el.setAttribute('aria-pressed', 'false');
  }
  updateSelectionPanel();
}

function updateSelectionPanel() {
  const count = state.selectedSeats.length;
  $('selectedCount').textContent = count === 0 ? 'No seats selected' : `${count} seat${count > 1 ? 's' : ''} selected`;
  $('totalPrice').textContent    = `₹ ${(count * SEAT_PRICE).toLocaleString('en-IN')}`;
  $('proceedToConfirm').disabled = count === 0;
}

/* ── STEP 4: Confirm ─────────────────────────────────────────── */
function renderConfirm() {
  const movie = state.selectedMovie;
  const show  = state.selectedShow;
  const dt    = fmtDateTime(show.show_time);

  $('c-movie').textContent    = movie.name;
  $('c-genre').textContent    = movie.genre;
  $('c-duration').textContent = `${movie.duration} minutes`;
  $('c-showtime').textContent = dt.full;
  $('c-screen').textContent   = `Screen ${show.screen_no}`;
  $('c-seats').textContent    = state.selectedSeats.map(s => s.seat_number).join('  •  ');
  $('c-total').textContent    = `₹ ${(state.selectedSeats.length * SEAT_PRICE).toLocaleString('en-IN')}`;
}

/* ── STEP 5: Ticket ──────────────────────────────────────────── */
function renderTicket(booking) {
  const movie = state.selectedMovie;
  const show  = state.selectedShow;
  const dt    = fmtDateTime(show.show_time);

  $('t-bookingId').textContent = `#${String(booking.booking_id).padStart(6, '0')}`;
  $('t-movie').textContent     = movie.name;
  $('t-date').textContent      = dt.date;
  $('t-time').textContent      = dt.time;
  $('t-screen').textContent    = `Screen ${show.screen_no}`;
  $('t-genre').textContent     = movie.genre;
  $('t-seats').textContent     = booking.seat_numbers.join('  ');
  $('t-guest').textContent     = booking.user_name;
  $('t-total').textContent     = `₹ ${(booking.seat_numbers.length * SEAT_PRICE).toLocaleString('en-IN')}`;

  generateBarcode(booking.booking_id);
  $('t-barcodeNum').textContent = `CP${String(booking.booking_id).padStart(10, '0')}${show.show_id}`;
}

function generateBarcode(seed) {
  const container = $('barcodeLines');
  container.innerHTML = '';
  const rng = () => { seed = (seed * 1664525 + 1013904223) & 0xffffffff; return Math.abs(seed % 9999); };
  for (let i = 0; i < 55; i++) {
    const bar = document.createElement('div');
    bar.className = 'bar';
    bar.style.width  = `${(rng() % 3) + 1}px`;
    bar.style.height = `${(rng() % 20) + 24}px`;
    container.appendChild(bar);
  }
}

/* ── My Bookings Modal ───────────────────────────────────────── */
async function renderBookingsModal() {
  const body = $('bookingsModalBody');
  body.innerHTML = '<p style="color:var(--text-secondary);text-align:center;padding:24px">Loading…</p>';

  try {
    const all = await API.getAllBookings();
    if (all.length === 0) {
      body.innerHTML = `<p style="color:var(--text-secondary);text-align:center;padding:32px">No bookings yet – book your first ticket! 🎬</p>`;
      return;
    }

    body.innerHTML = all.map(b => {
      const dt      = fmtDateTime(b.booking_time);
      const showDt  = b.show ? fmtDateTime(b.show.show_time) : null;
      const vis     = MOVIE_VISUALS[b.movie?.movie_id] || { icon: '🎬' };
      const chips   = (b.seat_numbers || []).map(s => `<span class="seat-chip">${s}</span>`).join('');
      const total   = (b.seat_numbers?.length || 0) * SEAT_PRICE;
      return `
        <div class="booking-history-item">
          <div class="bhi-id">Booking #${String(b.booking_id).padStart(6,'0')}</div>
          <div class="bhi-movie">${vis.icon}  ${b.movie?.name || 'Unknown'}</div>
          <div class="bhi-meta">${b.movie?.genre || ''}  •  ${b.movie?.duration || '–'} min</div>
          ${showDt ? `<div class="bhi-meta">${showDt.full}  •  Screen ${b.show?.screen_no}</div>` : ''}
          <div class="bhi-meta" style="margin-top:4px">Booked: ${dt.date} at ${dt.time}</div>
          <div class="bhi-seats">${chips}</div>
          <div style="font-size:13px;color:var(--gold);margin-top:6px;font-weight:700">
            ₹ ${total.toLocaleString('en-IN')}
          </div>
        </div>`;
    }).join('');
  } catch (err) {
    body.innerHTML = `<p style="color:var(--red-seat);text-align:center;padding:24px">Failed to load bookings: ${err.message}</p>`;
  }
}

/* ════════════════════════════════════════════════════════════════════
   6. FLOW ACTIONS
   ════════════════════════════════════════════════════════════════════ */

async function selectMovie(id) {
  state.selectedMovie  = state.movies.find(m => m.movie_id === id);
  state.selectedSeats  = [];
  showView('shows');
  await renderShows(id);
}

async function selectShow(show) {
  state.selectedShow  = show;
  state.selectedSeats = [];
  showView('seats');
  await renderSeats(show.show_id);
}

function proceedToConfirm() {
  if (state.selectedSeats.length === 0) { toast('Please select at least one seat', 'error'); return; }
  renderConfirm();
  showView('confirm');
}

async function confirmBooking() {
  const name = $('userNameInput').value.trim();
  if (!name) { toast('Please enter your name', 'error'); $('userNameInput').focus(); return; }

  const btn = $('confirmBookingBtn');
  btn.disabled    = true;
  btn.textContent = '⏳ Processing…';

  try {
    const seatIds = state.selectedSeats.map(s => s.seat_id);
    const res     = await API.bookSeats(name, state.selectedShow.show_id, seatIds);

    state.lastBooking = res.booking;
    renderTicket(res.booking);
    showView('ticket');
    toast('🎉 Booking confirmed!', 'success');
  } catch (err) {
    toast(`❌ ${err.message}`, 'error');
    btn.disabled    = false;
    btn.textContent = '🎟 Confirm & Book Now';
  }
}

async function resetApp() {
  state.selectedMovie = null;
  state.selectedShow  = null;
  state.selectedSeats = [];
  state.lastBooking   = null;
  $('userNameInput').value  = '';
  $('userEmailInput').value = '';
  // Reset steps
  ['1','2','3','4'].forEach(i => {
    $(`step-nav-${i}`).className = 'step';
  });
  $('step-nav-1').classList.add('active');
  showView('movies');
  await renderMovies();
}

/* ════════════════════════════════════════════════════════════════════
   7. HERO PARTICLES
   ════════════════════════════════════════════════════════════════════ */
function initParticles() {
  const container = $('heroParticles');
  const colors    = ['#8a5cf6','#a78bfa','#22d3ee','#f5c518'];
  for (let i = 0; i < 28; i++) {
    const p = document.createElement('div');
    p.className = 'particle';
    const size  = Math.random() * 4 + 2;
    p.style.cssText = `
      width:${size}px; height:${size}px;
      background:${colors[i % colors.length]};
      left:${Math.random()*100}%;
      animation-duration:${4+Math.random()*8}s;
      animation-delay:${Math.random()*6}s;
    `;
    container.appendChild(p);
  }
}

/* ════════════════════════════════════════════════════════════════════
   8. BOOT & EVENT WIRING
   ════════════════════════════════════════════════════════════════════ */
document.addEventListener('DOMContentLoaded', async () => {
  initParticles();
  showView('movies');
  await renderMovies();   // ← first real API call

  // Hero CTA
  $('heroCta').addEventListener('click', () =>
    document.querySelector('.main-wrap').scrollIntoView({ behavior: 'smooth' }));

  // Back buttons
  $('backToMovies').addEventListener('click', () => {
    state.selectedShow  = null;
    state.selectedSeats = [];
    showView('movies');
  });
  $('backToShows').addEventListener('click', async () => {
    state.selectedSeats = [];
    showView('shows');
    await renderShows(state.selectedMovie.movie_id);
  });
  $('backToSeats').addEventListener('click', async () => {
    showView('seats');
    await renderSeats(state.selectedShow.show_id);
  });

  // Seat flow
  $('proceedToConfirm').addEventListener('click', proceedToConfirm);

  // Confirm & book
  $('confirmBookingBtn').addEventListener('click', confirmBooking);
  $('userNameInput').addEventListener('keydown', e => e.key === 'Enter' && confirmBooking());

  // Ticket actions
  $('printTicketBtn').addEventListener('click', () => window.print());
  $('bookAnotherBtn').addEventListener('click', resetApp);

  // My bookings modal
  $('viewBookingsBtn').addEventListener('click', async () => {
    $('bookingsModal').classList.add('open');
    await renderBookingsModal();
  });
  $('closeBookingsModal').addEventListener('click', () =>
    $('bookingsModal').classList.remove('open'));
  $('bookingsModal').addEventListener('click', e => {
    if (e.target === $('bookingsModal')) $('bookingsModal').classList.remove('open');
  });
  document.addEventListener('keydown', e => {
    if (e.key === 'Escape') $('bookingsModal').classList.remove('open');
  });
});
