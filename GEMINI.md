# JOSM 3D Viewer Plugin

## Goal

Create a JOSM plugin that displays loaded buildings (including `building:part=*`) in a separate 3D window.


## next steps

Next we need to make our 3d window repaint when the data 
 changes, for example, loaded from the database, or the user edits something. Otherwise 
 (i.e. as it is now) it is always empty, because List buildings 
 is initialized before the data is loaded.
 
 
## josm source code

 josm source code can be found in ./josm_source