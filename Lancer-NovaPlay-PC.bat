@echo off
title NovaPlay IPTV - Version PC
cd /d "%~dp0"
if exist "NovaPlayIPTV-PC.exe" (
    start "" "NovaPlayIPTV-PC.exe"
    exit /b 0
)
start /b "" node "pc\server.js"
timeout /t 1 /nobreak >nul
start "" "http://127.0.0.1:4789"
