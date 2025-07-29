pushd d:\z3dViewer
call mvn package
if ERRORLEVEL 1 goto :err
xcopy /Y d:\z3dViewer\target\urbaneye3d.jar c:\Users\zkir\AppData\Roaming\JOSM\plugins
java -jar d:\tools\josm\josm-tested.jar d:\z3dViewer\tests\test2.osm
echo errorlevel %errorlevel%
echo test succeed
goto :end
:err
echo test failed
:end
popd

