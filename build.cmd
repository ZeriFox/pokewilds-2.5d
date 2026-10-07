@echo off
setlocal
cd /d "%~dp0"
if errorlevel 1 goto failed
if not exist "lib\upstream-runtime.jar" goto missing_dependencies
if not exist "lib\gltf-2.1.0.jar" goto missing_dependencies
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

:missing_dependencies
echo Mancano le librerie necessarie alla compilazione.
echo Servono lib\upstream-runtime.jar e lib\gltf-2.1.0.jar dal pacchetto build-inputs.
echo build.cmd non le scarica automaticamente. Le istruzioni sono in README.md.
echo Per giocare usa AVVIA-POKEWILDS.cmd nella cartella completa del gioco.
goto failed

:failed
if not defined POKEWILDS_NO_PAUSE pause
exit /b 1
