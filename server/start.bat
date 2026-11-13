@echo off
rem Avvio manuale di SoundVault (in alternativa all'avvio automatico creato da install.ps1)
cd /d "%~dp0"
".venv\Scripts\python.exe" -m soundvault serve
pause
