@echo off
setlocal
cd /d "%~dp0"

set "JAVA_HOME="
if exist "D:\Program Files\Android\Android Studio\jbr\bin\java.exe" set "JAVA_HOME=D:\Program Files\Android\Android Studio\jbr"
if not defined JAVA_HOME if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
if not defined JAVA_HOME if exist "%ProgramFiles%\Android\Android Studio\jbr\bin\java.exe" set "JAVA_HOME=%ProgramFiles%\Android\Android Studio\jbr"

if not defined JAVA_HOME (
  echo ERROR: Android Studio JBR tidak ditemukan.
  echo Cek instalasi Android Studio.
  pause
  exit /b 1
)

set "PATH=%JAVA_HOME%\bin;%PATH%"

echo JAVA_HOME=%JAVA_HOME%
call gradlew.bat :app:assembleDebug
if errorlevel 1 (
  echo.
  echo BUILD FAILED
  pause
  exit /b 1
)

echo.
echo BUILD SUCCESS
if exist "app\build\outputs\apk\debug\app-debug.apk" echo APK: app\build\outputs\apk\debug\app-debug.apk
pause
