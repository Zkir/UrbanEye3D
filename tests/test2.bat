pushd d:\z3dViewer
call mvn package
if ERRORLEVEL 1 goto :err
xcopy /Y d:\z3dViewer\target\threedviewer-1.0-SNAPSHOT.jar c:\Users\zkir\AppData\Roaming\JOSM\plugins
java -jar d:\tools\josm\josm-tested.jar d:\z3dViewer\tests\test2.osm
echo errorlevel %errorlevel%
goto :end
:err
echo test failed
:end
echo test succeed
popd
