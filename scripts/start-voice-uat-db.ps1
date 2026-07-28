$ErrorActionPreference = "Stop"

$composeFile = Join-Path $PSScriptRoot "..\docker-compose.voice-uat.yml"
$port = if ($env:MONEYFLOW_VOICE_UAT_DB_PORT) { [int]$env:MONEYFLOW_VOICE_UAT_DB_PORT } else { 15432 }
$env:MONEYFLOW_VOICE_UAT_DB_PORT = "$port"

docker compose -f $composeFile up -d

docker exec moneyflow-voice-uat-postgres pg_isready -h localhost -U moneyflow_local -d moneyflow_voice_uat
Write-Host "MoneyFlow voice UAT DB ready: host=localhost port=$port database=moneyflow_voice_uat"
