#!/usr/bin/env powershell
# =====================================================================
# CinePlex -- MySQL Setup Script
# Installs portable MySQL 8, creates the database, and seeds the data
# Run ONCE before starting the server
# =====================================================================

$ErrorActionPreference = "Stop"
$ROOT      = $PSScriptRoot
$TOOLS_DIR = "$ROOT\.tools"
$MYSQL_DIR = "$TOOLS_DIR\mysql8"
$MYSQL_URL = "https://downloads.mariadb.org/rest-api/mariadb/10.11.11/mariadb-10.11.11-winx64.zip"

function Write-Step { param($m) Write-Host ""; Write-Host "  >> $m" -ForegroundColor Cyan }
function Write-Ok   { param($m) Write-Host "  OK  $m" -ForegroundColor Green }
function Write-Warn { param($m) Write-Host "  !!  $m" -ForegroundColor Yellow }

# ---- 1. Download portable MySQL 8 ----------------------------------
Write-Step "Setting up portable MySQL 8 ..."
New-Item -ItemType Directory -Force "$TOOLS_DIR\mysql_data" | Out-Null

if (-not (Test-Path "$MYSQL_DIR\bin\mysqld.exe")) {
    Write-Host "  Downloading MariaDB 10.11 LTS (~100 MB) ..."
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    Invoke-WebRequest -Uri $MYSQL_URL -OutFile "$TOOLS_DIR\mysql.zip" -UseBasicParsing
    Write-Host "  Extracting ..."
    Expand-Archive "$TOOLS_DIR\mysql.zip" "$TOOLS_DIR" -Force
    Get-ChildItem "$TOOLS_DIR" -Directory | Where-Object { $_.Name -match "^mariadb" } |
        Rename-Item -NewName "mysql8"
    Remove-Item "$TOOLS_DIR\mysql.zip"
}
Write-Ok "MariaDB 10.11 ready at $MYSQL_DIR"

$MYSQLD = "$MYSQL_DIR\bin\mysqld.exe"
$MYSQL  = "$MYSQL_DIR\bin\mysql.exe"
$MYSQLA = "$MYSQL_DIR\bin\mysqladmin.exe"

# ---- 2. Initialize data dir ----------------------------------------
$DATA_DIR = "$TOOLS_DIR\mysql_data"
if (-not (Test-Path "$DATA_DIR\mysql")) {
    Write-Step "Initializing MariaDB data directory ..."
    $installDb = "$MYSQL_DIR\bin\mysql_install_db.exe"
    if (Test-Path $installDb) {
        & $installDb --datadir="$DATA_DIR" --password="" 2>&1 | Out-Null
    } else {
        # Fallback: mysqld --install (older portable builds)
        & $MYSQLD --datadir="$DATA_DIR" --bootstrap 2>&1 | Out-Null
    }
    Write-Ok "Data directory initialized (root user, no password)"
}

# ---- 3. Start mysqld -----------------------------------------------
Write-Step "Starting MySQL server (port 3306) ..."
$running = try { (& $MYSQLA -u root --connect-timeout=2 ping 2>&1) -match "alive" } catch { $false }

if (-not $running) {
    Start-Process -FilePath $MYSQLD `
        -ArgumentList "--datadir=`"$DATA_DIR`" --port=3306 --console" `
        -NoNewWindow
    Write-Host "  Waiting for MySQL to start ..."
    Start-Sleep -Seconds 5
}
Write-Ok "MySQL running."

# ---- 4. Create database and load schema + seed ----------------------
Write-Step "Creating database and loading schema ..."

$SCHEMA = "$ROOT\schema.sql"
$SEED   = "$ROOT\seed.sql"

# Create DB
& $MYSQL -u root -e "CREATE DATABASE IF NOT EXISTS movie_ticket_booking;" 2>&1
Write-Ok "Database 'movie_ticket_booking' ready."

# Load schema
& $MYSQL -u root movie_ticket_booking -e "source $SCHEMA" 2>&1
Write-Ok "Schema loaded."

# Load seed
& $MYSQL -u root movie_ticket_booking -e "source $SEED" 2>&1
Write-Ok "Seed data loaded."

# ---- Done -----------------------------------------------------------
Write-Host ""
Write-Host "  +--------------------------------------------------+"
Write-Host "  |  MySQL setup complete!                           |"
Write-Host "  |                                                  |"
Write-Host "  |  Now run:  powershell -ExecutionPolicy Bypass    |"
Write-Host "  |            -File run.ps1                         |"
Write-Host "  |                                                  |"
Write-Host "  |  to start the CinePlex server at :8080           |"
Write-Host "  +--------------------------------------------------+"
