@echo off
setlocal enabledelayedexpansion

echo ===================================================================
echo             Sms Auto Forwarder - Automated Apk Builder
echo ===================================================================
echo.

rem Check Common Java Installation Paths
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
        goto FoundJava
    )
)

where java >nul 2>nul
if %errorlevel% equ 0 (
    set "JAVA_EXE=java"
    goto FoundJava
)

rem Search Common Android Studio Bundled Jdk Paths
if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" (
    set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
    set "JAVA_EXE=C:\Program Files\Android\Android Studio\jbr\bin\java.exe"
    goto FoundJava
)

if exist "C:\Program Files\Java" (
    for /d %%D in ("C:\Program Files\Java\jdk*") do (
        if exist "%%D\bin\java.exe" (
            set "JAVA_HOME=%%D"
            set "JAVA_EXE=%%D\bin\java.exe"
            goto FoundJava
        )
    )
)

echo [!] Java Development Kit (JDK 17+) was not automatically detected.
echo.
echo Please follow either of these 2 easy options:
echo.
echo Option 1 (Recommended):
echo   Open this project folder in Android Studio:
echo   - Click "Open" and select this folder.
echo   - Click "Build" in the top menu -^> "Build Bundle(s) / APK(s)" -^> "Build APK(s)".
echo.
echo Option 2 (Install OpenJDK via Terminal):
echo   Run in Administrator PowerShell:
echo   winget install Microsoft.OpenJDK.17
echo   Then re-run this BuildApk.bat script!
echo.
pause
exit /b 1

:FoundJava
echo [*] Java detected at: %JAVA_EXE%
echo [*] Building Production Release And Debug APKs using Gradle Wrapper...
echo.

call gradlew.bat assembleRelease assembleDebug

if %errorlevel% equ 0 (
    echo.
    echo ===================================================================
    echo [SUCCESS] APKs built successfully!
    echo Production Release APK: app\build\outputs\apk\release\app-release.apk
    echo Debug APK:              app\build\outputs\apk\debug\app-debug.apk
    echo ===================================================================
    echo.
    if exist "app\build\outputs\apk\release" (
        explorer.exe "app\build\outputs\apk\release"
    ) else (
        explorer.exe "app\build\outputs\apk\debug"
    )
) else (
    echo.
    echo [!] Gradle build failed. Check error output above.
)

pause
