pushd d:\UrbanEye3D
call mvn package
if ERRORLEVEL 1 goto :err
xcopy /Y d:\UrbanEye3D\target\urbaneye3d.jar c:\Users\zkir\AppData\Roaming\JOSM\plugins
java -jar d:\tools\josm\josm-tested.jar https://www.openstreetmap.org/#map=17/55.756311/37.617552
echo errorlevel %errorlevel%
goto :end
:err
echo test failed
:end
echo test succeed
popd
