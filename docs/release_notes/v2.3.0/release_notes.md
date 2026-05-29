# v2.3.0 Trees and Species

In this release the following features are added:

* `leaf_type` is now inferred from the built-in species database. If `leaf_type` tag is missing but `species` or `genus` tags are present, the plugin figures `leaf_type`  automatically.  For example,  the plugin now knows that `species=Malus domestica` is `broadleaved`, while `species=Picea abies` is `needleleaved`, and picks the right tree model accordingly.

*  Validation check for `species` and `genus` tags are added, so you may be sure that the entered values are correct.

* Added support for advertising columns (Morris columns). Nodes tagged with `advertising=column` will now be rendered as 3D street furniture.

## Notes on species database
The species information comes from two sources:

1. The curated species list on the osm wiki: [Tag:natural=tree/List_of_Species](https://wiki.openstreetmap.org/wiki/Tag:natural%3Dtree/List_of_Species) 
2. From the OSM database itself. The list of species used for trees in OSM is compiled, check agaist [Plant of the World Online](https://powo.science.kew.org/), and, if `leaf_type` information is present, it is used for inferrence. 

You can see the current list of known species [here](https://github.com/Zkir/UrbanEye3D/blob/master/docs/tree_species.md). It will be updated from time to time with the new plugin releases.

*Have fun!*
