pushd d:\UrbanEye3D
call mvn package -DskipTests
if ERRORLEVEL 1 goto :err
xcopy /Y d:\UrbanEye3D\target\urbaneye3d.jar c:\Users\zkir\AppData\Roaming\JOSM\plugins
java -jar d:\tools\josm\josm-tested.jar d:\UrbanEye3D\tests\test2.osm
echo errorlevel %errorlevel%
echo test succeed
goto :end
:err
echo test failed
:end
popd

