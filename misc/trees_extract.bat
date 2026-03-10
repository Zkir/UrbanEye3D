@echo off

SET SOURCE_FILE="d:\_VFR_LANDMARKS_3D_RU\work_folder\00_planet.osm\planet-latest.o5m"
set WORK_FOLDER=.\data

rem echo Working folder: %WORK_FOLDER%

osmfilter %SOURCE_FILE% --keep-nodes="natural=tree" --keep-ways= --keep-relations= >%WORK_FOLDER%\trees.osm

