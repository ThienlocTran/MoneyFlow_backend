$ErrorActionPreference = "Stop"

$composeFile = Join-Path $PSScriptRoot "..\docker-compose.voice-uat.yml"

docker compose -f $composeFile down -v
docker compose -f $composeFile up -d
docker exec moneyflow-voice-uat-postgres pg_isready -h localhost -U moneyflow_local -d moneyflow_voice_uat
Write-Host "MoneyFlow voice UAT DB reset: host=localhost port=55432 database=moneyflow_voice_uat"
