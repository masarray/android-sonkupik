@echo off
setlocal
cd /d "%~dp0"
call BUILD_DEBUG_CLICK.bat
if errorlevel 1 exit /b 1

set "ADB_EXE="
if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" set "ADB_EXE=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not defined ADB_EXE if exist "%ANDROID_HOME%\platform-tools\adb.exe" set "ADB_EXE=%ANDROID_HOME%\platform-tools\adb.exe"
if not defined ADB_EXE (
  echo.
  echo adb.exe tidak ditemukan. APK sudah berhasil di-build, install manual saja.
  pause
  exit /b 0
)

"%ADB_EXE%" devices
"%ADB_EXE%" install -r "app\build\outputs\apk\debug\app-debug.apk"
pause
