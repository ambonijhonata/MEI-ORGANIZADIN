@echo off
cd /d "%~dp0"
mvn spring-boot:run -Dspring-boot.run.profiles=dev
pause
