@echo off
setlocal
cd /d "%~dp0"
if errorlevel 1 goto failed
where py >nul 2>nul
if not errorlevel 1 goto use_py
where python >nul 2>nul
if not errorlevel 1 goto use_python
echo Python 3 non trovato. Installa Python 3.10 o successivo e riprova.
goto failed

:use_py
py -3 build.py %*
goto finished

:use_python
python build.py %*
goto finished

:finished
set "POKEWILDS_BUILD_EXIT=%errorlevel%"
if not "%POKEWILDS_BUILD_EXIT%"=="0" echo Build non riuscita. Leggi gli errori sopra e build\build.log se presente.
if not defined POKEWILDS_NO_PAUSE pause
exit /b %POKEWILDS_BUILD_EXIT%

:failed
if not defined POKEWILDS_NO_PAUSE pause
exit /b 1
