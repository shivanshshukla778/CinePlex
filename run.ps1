#!/usr/bin/env powershell
# =====================================================================
# CinePlex -- One-Click Build & Run Script (Windows PowerShell)
# =====================================================================
# This script:
#   1. Downloads & installs OpenJDK 17 (portable, no admin needed)
#   2. Downloads & installs Apache Maven 3.9 (portable)
#   3. Builds the fat JAR   (mvn clean package)
#   4. Starts the REST API  (java -jar ... --serve)
#
# Run:  powershell -ExecutionPolicy Bypass -File run.ps1

$ErrorActionPreference = "Stop"
$ROOT = $PSScriptRoot

# -- Config -----------------------------------------------------------
$TOOLS_DIR = "$ROOT\.tools"
$JDK_DIR   = "$TOOLS_DIR\jdk17"
$MVN_DIR   = "$TOOLS_DIR\maven"
$JAR       = "$ROOT\target\movie-ticket-booking-1.0.0-SNAPSHOT.jar"

$JDK_URL = "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.11%2B9/OpenJDK17U-jdk_x64_windows_hotspot_17.0.11_9.zip"
$MVN_URL = "https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.zip"

function Write-Step { param($msg) Write-Host "" ; Write-Host "  >> $msg" -ForegroundColor Cyan }
function Write-Ok   { param($msg) Write-Host "  OK  $msg" -ForegroundColor Green }
function Write-Err  { param($msg) Write-Host "  ERR $msg" -ForegroundColor Red; exit 1 }

# -- 1. Check system Java 17+ -----------------------------------------
$JAVA_EXE = $null
$MVN_EXE  = $null

try {
    $jv = (& java -version 2>&1) -join " "
    if ($jv -match 'version "17' -or $jv -match 'version "2[0-9]') {
        $JAVA_EXE = "java"
        Write-Ok "System Java 17+ found."
    }
} catch {}

try {
    $mv = (& mvn --version 2>&1) -join " "
    if ($mv -match "Apache Maven") { $MVN_EXE = "mvn"; Write-Ok "System Maven found." }
} catch {}

# -- 2. Download JDK 17 if needed ------------------------------------
if (-not $JAVA_EXE) {
    Write-Step "Downloading OpenJDK 17 (portable) ..."
    New-Item -ItemType Directory -Force $TOOLS_DIR | Out-Null

    if (-not (Test-Path "$JDK_DIR\bin\java.exe")) {
        $zip = "$TOOLS_DIR\jdk17.zip"
        Write-Host "  Downloading from GitHub Adoptium (this may take a minute)..."
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        Invoke-WebRequest -Uri $JDK_URL -OutFile $zip -UseBasicParsing
        Write-Host "  Extracting JDK..."
        Expand-Archive -Path $zip -DestinationPath $TOOLS_DIR -Force
        Get-ChildItem $TOOLS_DIR -Directory |
            Where-Object { $_.Name -match "^jdk" -and $_.Name -ne "jdk17" } |
            Rename-Item -NewName "jdk17"
        Remove-Item $zip -Force
    }
    $JAVA_EXE = "$JDK_DIR\bin\java.exe"
    Write-Ok "OpenJDK 17 ready."
}

# -- 3. Download Maven if needed -------------------------------------
if (-not $MVN_EXE) {
    Write-Step "Downloading Apache Maven 3.9 (portable) ..."
    New-Item -ItemType Directory -Force $TOOLS_DIR | Out-Null

    if (-not (Test-Path "$MVN_DIR\bin\mvn.cmd")) {
        $zip = "$TOOLS_DIR\maven.zip"
        Write-Host "  Downloading from Apache mirrors..."
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        Invoke-WebRequest -Uri $MVN_URL -OutFile $zip -UseBasicParsing
        Write-Host "  Extracting Maven..."
        Expand-Archive -Path $zip -DestinationPath $TOOLS_DIR -Force
        Get-ChildItem $TOOLS_DIR -Directory |
            Where-Object { $_.Name -match "^apache-maven" } |
            Rename-Item -NewName "maven"
        Remove-Item $zip -Force
    }
    $MVN_EXE = "$MVN_DIR\bin\mvn.cmd"
    Write-Ok "Maven ready."
}

# -- 4. Set JAVA_HOME for Maven --------------------------------------
if ($JAVA_EXE -ne "java") {
    $env:JAVA_HOME = $JDK_DIR
    $env:PATH      = "$JDK_DIR\bin;$env:PATH"
}

# -- 5. Build --------------------------------------------------------
Write-Step "Building CinePlex fat JAR ..."
Set-Location $ROOT
& $MVN_EXE clean package "-q"
if ($LASTEXITCODE -ne 0) { Write-Err "Maven build failed! Check the output above." }
Write-Ok "Build successful -> $JAR"

# -- 6. Auto-start portable MySQL if available ----------------------
$PORTABLE_MYSQLD = "$TOOLS_DIR\mysql8\bin\mysqld.exe"
$PORTABLE_MYSQLA = "$TOOLS_DIR\mysql8\bin\mysqladmin.exe"
$MYSQL_DATA      = "$TOOLS_DIR\mysql_data"

if (Test-Path $PORTABLE_MYSQLD) {
    $alive = try { (& $PORTABLE_MYSQLA -u root --connect-timeout=2 ping 2>&1) -match "alive" } catch { $false }
    if (-not $alive) {
        Write-Step "Starting portable MySQL 8 ..."
        Start-Process -FilePath $PORTABLE_MYSQLD `
            -ArgumentList "--datadir=`"$MYSQL_DATA`" --port=3306" `
            -NoNewWindow
        Start-Sleep -Seconds 5
        Write-Ok "MySQL started."
    } else {
        Write-Ok "MySQL already running."
    }
}

# -- 7. Launch server ------------------------------------------------
Write-Step "Starting CinePlex REST server on http://localhost:8080 ..."
Write-Host ""
Write-Host "  +--------------------------------------------------+"
Write-Host "  |  Open browser at:  http://localhost:8080         |"
Write-Host "  |                                                  |"
Write-Host "  |  NOTE: Make sure MySQL is running and you ran:   |"
Write-Host "  |        USE movie_ticket_booking;                 |"
Write-Host "  |        SOURCE schema.sql;                        |"
Write-Host "  |        SOURCE seed.sql;                          |"
Write-Host "  |                                                  |"
Write-Host "  |  Press Ctrl+C to stop the server                |"
Write-Host "  +--------------------------------------------------+"
Write-Host ""

Start-Process "http://localhost:8080"
& $JAVA_EXE -jar $JAR --serve
