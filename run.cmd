@echo off
setlocal
cd /d "%~dp0"
if errorlevel 1 goto failed
if not exist "dist\pokewilds-rebuilt.jar" goto missing_game
if not defined POKEWILDS_VISUAL set "POKEWILDS_VISUAL=classic"
if not defined POKEWILDS_MODELS set "POKEWILDS_MODELS=off"
set "POKEWILDS_WORKDIR=run"
if "%POKEWILDS_VISUAL%"=="johto" set "POKEWILDS_WORKDIR=run-johto"
if not exist "%POKEWILDS_WORKDIR%" mkdir "%POKEWILDS_WORKDIR%"
cd /d "%~dp0%POKEWILDS_WORKDIR%"
if errorlevel 1 (
    echo Impossibile accedere alla cartella run per i salvataggi.
    goto failed
)
if exist "..\toolchain\jdk-17\bin\java.exe" (
    set "POKEWILDS_JAVA=..\toolchain\jdk-17\bin\java.exe"
    goto launch
)
if defined JAVA_HOME (
    if not exist "%JAVA_HOME%\bin\java.exe" (
        echo JAVA_HOME non contiene bin\java.exe.
        goto failed
    )
    set "POKEWILDS_JAVA=%JAVA_HOME%\bin\java.exe"
    goto launch
)
where java >nul 2>nul
if errorlevel 1 (
    echo Java non trovato. Ripristina toolchain\jdk-17 oppure configura JAVA_HOME o PATH.
    goto failed
)
set "POKEWILDS_JAVA=java"

:launch
echo Avvio PokeWilds [%POKEWILDS_VISUAL%]. Salvataggi e impostazioni: %POKEWILDS_WORKDIR%.
"%POKEWILDS_JAVA%" "-Dpokewilds.visual=%POKEWILDS_VISUAL%" "-Dpokewilds.models=%POKEWILDS_MODELS%" -jar "..\dist\pokewilds-rebuilt.jar" %*
set "POKEWILDS_RUN_EXIT=%errorlevel%"
if "%POKEWILDS_RUN_EXIT%"=="0" exit /b 0
echo Il gioco si e chiuso con errore %POKEWILDS_RUN_EXIT%. Leggi i messaggi sopra.
if not defined POKEWILDS_NO_PAUSE pause
exit /b %POKEWILDS_RUN_EXIT%

:missing_game
echo Manca il gioco compilato: dist\pokewilds-rebuilt.jar.
if exist "build.py" (
    echo Questa e la cartella dei sorgenti. build.cmd da solo non scarica le dipendenze.
    echo Per giocare usa il pacchetto pokewilds-2.5d-windows-x64.zip, estratto completamente.
    echo Per ricompilare servono anche i due JAR in lib e un JDK 17: vedi README.md.
) else (
    echo Il pacchetto e incompleto. Estrai tutto pokewilds-2.5d-windows-x64.zip.
    echo Avvia il gioco dalla cartella estratta, senza spostare soltanto questo file.
)
goto failed

:failed
if not defined POKEWILDS_NO_PAUSE pause
exit /b 1
