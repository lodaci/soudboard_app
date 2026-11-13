@echo off
rem Scorciatoia per i comandi: soundvault.bat import ... / soundvault.bat token list / soundvault.bat index
cd /d "%~dp0"
".venv\Scripts\python.exe" -m soundvault %*
