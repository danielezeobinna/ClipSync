@echo off
rd /s /q build 2>nul
rd /s /q dist 2>nul
del ClipSync.spec 2>nul
python -m PyInstaller --onefile --windowed --name ClipSync --icon=logo.ico --add-data "logo.png;." --add-data "logo.ico;." clip_server.py
echo Done! Check dist folder.
pause