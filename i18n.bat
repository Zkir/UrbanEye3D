@echo off
setlocal

echo [1/3] Generating source file list...

if not exist "target" mkdir "target"

rem This command is copied from the exec-maven-plugin configuration
powershell -NoProfile -Command "$files = Get-ChildItem -Path src\main\java -Recurse -Filter *.java | ForEach-Object { $_.FullName }; [System.IO.File]::WriteAllLines('target\srcfiles.txt', $files)"
if %errorlevel% neq 0 (
    echo ERROR: Failed to generate source file list.
    exit /b %errorlevel%
)

echo.
echo [2/3] Extracting gettext keys with xgettext...
rem This command is copied from the exec-maven-plugin configuration
xgettext -c --from-code=utf-8 --output=po/urbaneye3d.pot --language=Java -ktrc:1c,2 -kmarktrc:1c,2 -ktr -kmarktr -ktrn:1,2 -ktrnc:1c,2,3 --files-from=target/srcfiles.txt
if %errorlevel% neq 0 (
    echo ERROR: xgettext failed to extract keys.
    exit /b %errorlevel%
)

echo.
echo [3/3] Compiling .po to .lang files...
rem This command is copied from the exec-maven-plugin configuration
perl d:/src2/josm/i18n/i18n.pl --potfile=po/urbaneye3d.pot --basedir=src/main/resources/data/ po/ru.po
if %errorlevel% neq 0 (
    echo ERROR: Failed to generate language files.
    exit /b %errorlevel%
)

echo.
echo Internationalization build completed successfully.

endlocal
