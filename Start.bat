@echo off
cd /d "%~dp0"
start "" javaw -jar "%~dp0pdf-sofortdruck.jar" "%~dp0config.json"
