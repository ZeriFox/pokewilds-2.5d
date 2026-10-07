@echo off
setlocal
call "%~dp0run-johto.cmd" %*
exit /b %errorlevel%
