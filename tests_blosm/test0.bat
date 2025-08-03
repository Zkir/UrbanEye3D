@echo off
SET BLENDER_EXE="c:\Program Files\Blender Foundation\Blender 4.4\blender.exe"
echo %BLENDER_EXE%

%BLENDER_EXE% --background --python d:\_VFR_LANDMARKS_3D_RU\scripts\osm2obj_b4.py -- d:\UrbanEye3D\tests_blosm\test0.osm .\output\


