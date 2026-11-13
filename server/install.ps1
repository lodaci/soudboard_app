# Installazione di SoundVault sul NUC (Windows 10/11)
# Esegui in PowerShell come amministratore, dalla cartella "server":
#   powershell -ExecutionPolicy Bypass -File .\install.ps1 -Library "D:\Suoni" -Lan
param(
    [string]$Library = "C:\SoundVault\library",
    [string]$Data = "C:\SoundVault\data",
    [int]$Port = 8765,
    [switch]$Lan,          # rende il server raggiungibile anche dalla rete di casa
    [switch]$NoService     # non creare l'avvio automatico
)
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

Write-Host "== Controllo Python ==" -ForegroundColor Cyan
$py = Get-Command py -ErrorAction SilentlyContinue
if (-not $py) {
    Write-Host "Python non trovato. Installalo con:  winget install Python.Python.3.12  e riapri PowerShell." -ForegroundColor Red
    exit 1
}

if (-not (Test-Path .venv)) { py -3 -m venv .venv }
$python = Join-Path $PSScriptRoot ".venv\Scripts\python.exe"
& $python -m pip install --upgrade pip | Out-Null
& $python -m pip install -r requirements.txt

Write-Host "== Configurazione ==" -ForegroundColor Cyan
$setupArgs = @("-m", "soundvault", "setup", "--library", $Library, "--data", $Data, "--port", $Port)
if ($Lan) { $setupArgs += "--lan" }
& $python @setupArgs

if (-not (Get-Command ffmpeg -ErrorAction SilentlyContinue)) {
    Write-Host "Suggerimento: installa ffmpeg (winget install Gyan.FFmpeg) per convertire i WAV in MP3 leggeri per il telefono." -ForegroundColor Yellow
}

if ($Lan) {
    Write-Host "== Firewall: apro la porta $Port sulla rete privata ==" -ForegroundColor Cyan
    if (-not (Get-NetFirewallRule -DisplayName "SoundVault" -ErrorAction SilentlyContinue)) {
        New-NetFirewallRule -DisplayName "SoundVault" -Direction Inbound -Protocol TCP -LocalPort $Port -Action Allow -Profile Private | Out-Null
    }
}

if (-not $NoService) {
    Write-Host "== Avvio automatico (Utilità di pianificazione) ==" -ForegroundColor Cyan
    $action = New-ScheduledTaskAction -Execute $python -Argument "-m soundvault serve" -WorkingDirectory $PSScriptRoot
    $trigger = New-ScheduledTaskTrigger -AtStartup
    $principal = New-ScheduledTaskPrincipal -UserId "SYSTEM" -LogonType ServiceAccount -RunLevel Highest
    $settings = New-ScheduledTaskSettingsSet -RestartCount 5 -RestartInterval (New-TimeSpan -Minutes 1) `
        -ExecutionTimeLimit (New-TimeSpan -Days 0) -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries
    Register-ScheduledTask -TaskName "SoundVault" -Action $action -Trigger $trigger -Principal $principal `
        -Settings $settings -Force | Out-Null
    Start-ScheduledTask -TaskName "SoundVault"
    Write-Host "SoundVault avviato e configurato per partire all'accensione del NUC." -ForegroundColor Green
}

Write-Host "`nFatto. Prossimi passi: importa delle raccolte (vedi collections.md) e configura l'accesso da internet (README.md)." -ForegroundColor Green
