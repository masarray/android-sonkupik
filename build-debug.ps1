$ErrorActionPreference = "Stop"

$javaHomeCandidates = @(
    "D:\Program Files\Android\Android Studio\jbr",
    "C:\Program Files\Android\Android Studio\jbr",
    "$env:LOCALAPPDATA\Programs\Android Studio\jbr"
)

$javaHome = $javaHomeCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $javaHome) {
    throw "Android Studio JBR tidak ditemukan. Cek lokasi Android Studio lalu set JAVA_HOME manual."
}

$env:JAVA_HOME = $javaHome
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

Write-Host "JAVA_HOME = $env:JAVA_HOME"
java -version

.\gradlew.bat :app:assembleDebug
