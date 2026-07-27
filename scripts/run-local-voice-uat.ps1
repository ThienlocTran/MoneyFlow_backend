param(
    [string]$Database = "moneyflow_voice_uat",
    [string]$Username = "moneyflow_local",
    [string]$Password = "moneyflow_local_password",
    [string]$HostName = "localhost",
    [int]$Port = 55432
)

$ErrorActionPreference = "Stop"

if ($HostName -notin @("localhost", "127.0.0.1")) {
    throw "Voice UAT must use a local database host."
}

if ($Database -ne "moneyflow_voice_uat") {
    throw "Voice UAT must use database moneyflow_voice_uat."
}

$env:SPRING_PROFILES_ACTIVE = "local"
$env:MONEYFLOW_DB_URL = "jdbc:postgresql://${HostName}:${Port}/${Database}"
$env:MONEYFLOW_DB_USERNAME = $Username
$env:MONEYFLOW_DB_PASSWORD = $Password
$env:PATH = "C:\Windows\System32\WindowsPowerShell\v1.0;$env:PATH"

Remove-Item Env:\DB_URL -ErrorAction SilentlyContinue
Remove-Item Env:\DB_USERNAME -ErrorAction SilentlyContinue
Remove-Item Env:\DB_PASSWORD -ErrorAction SilentlyContinue
Remove-Item Env:\SPRING_DATASOURCE_URL -ErrorAction SilentlyContinue
Remove-Item Env:\SPRING_DATASOURCE_USERNAME -ErrorAction SilentlyContinue
Remove-Item Env:\SPRING_DATASOURCE_PASSWORD -ErrorAction SilentlyContinue

Write-Host "MoneyFlow voice UAT DB target: host=$HostName port=$Port database=$Database"
.\mvnw.cmd spring-boot:run
