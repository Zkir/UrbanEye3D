@echo off

SET SOURCE_FILE="d:\_VFR_LANDMARKS_3D_RU\work_folder\00_planet.osm\planet-latest.o5m"
set WORK_FOLDER=.\data

rem echo Working folder: %WORK_FOLDER%

osmfilter %SOURCE_FILE% --keep="building=* and ( levels=* or building:levels=* or height=* or building:height=* )" --drop="building=yes" --ignore-dependencies >%WORK_FOLDER%\buildings.osm

