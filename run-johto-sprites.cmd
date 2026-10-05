@echo off
setlocal
set "POKEWILDS_VISUAL=johto"
set "POKEWILDS_MODELS=off"
call "%~dp0run.cmd" %*
exit /b %errorlevel%
