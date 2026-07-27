$ErrorActionPreference = "Stop"

$SourceUrl = $env:MONEYFLOW_DB_URL
$SourceUser = $env:MONEYFLOW_DB_USERNAME
$SourcePassword = $env:MONEYFLOW_DB_PASSWORD

if (-not $SourceUrl) { $SourceUrl = $env:DB_URL }
if (-not $SourceUser) { $SourceUser = $env:DB_USERNAME }
if (-not $SourcePassword) { $SourcePassword = $env:DB_PASSWORD }

if (-not $SourceUrl -or -not $SourceUser -or -not $SourcePassword) {
    throw "Source DB env is missing. Set MONEYFLOW_DB_URL/USERNAME/PASSWORD or DB_URL/USERNAME/PASSWORD."
}

$uriText = $SourceUrl -replace '^jdbc:', ''
$uri = [System.Uri]$uriText
$sourceHost = $uri.Host
$sourcePort = if ($uri.Port -gt 0) { $uri.Port } else { 5432 }
$sourceDb = $uri.AbsolutePath.TrimStart('/')

if ($sourceHost -in @("localhost", "127.0.0.1")) {
    throw "Unexpected source DB host."
}

$backupDir = "D:\MindMirror\MoneyFlow\_local_db_backups"
New-Item -ItemType Directory -Force -Path $backupDir | Out-Null
$dumpPath = Join-Path $backupDir ("moneyflow_voice_uat_{0:yyyyMMdd_HHmmss}.sql" -f (Get-Date))

$maskedHost = if ($sourceHost.Length -gt 10) { $sourceHost.Substring(0, 6) + "***" + $sourceHost.Substring($sourceHost.LastIndexOf(".")) } else { "***" }
Write-Host "Source DB: host=$maskedHost database=$sourceDb"
Write-Host "Target DB: host=localhost port=55432 database=moneyflow_voice_uat"

docker run --rm `
    -e PGPASSWORD=$SourcePassword `
    -v "${backupDir}:/backup" `
    postgres:18 `
    pg_dump -h $sourceHost -p $sourcePort -U $SourceUser -d $sourceDb --format=plain --no-owner --no-privileges --file "/backup/$([IO.Path]::GetFileName($dumpPath))"

docker exec moneyflow-voice-uat-postgres psql -U moneyflow_local -d moneyflow_voice_uat -v ON_ERROR_STOP=1 -c "drop schema if exists public cascade; create schema public;"

$restoreInput = (Get-Content $dumpPath) | Where-Object { $_ -notmatch '^SET transaction_timeout' }
$restoreInput | docker run --rm -i `
    -e PGPASSWORD=moneyflow_local_password `
    postgres:18 `
    psql -h host.docker.internal -p 55432 -U moneyflow_local -d moneyflow_voice_uat -v ON_ERROR_STOP=1
