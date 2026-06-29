# NOTES ON tree_typos-bak.csv file

tree_typos-bak.csv -- containts changes, actually performed during `species=*` cleanup in OSM DB.

## IMPORTANT

Please keep in mind the following. `Status` field, or change type is important.  It defines how osm data can be modified.

|Status| meaning|
|:--- | :--- |
| Typo | Only `species` tag is changed to the specied new value  |
| Formatting | Same as Typo. |
| Nonsense | `species` tag is removed|
| Genus omitted | `species` tag is changed to the new value, **provided** genus tag is present and **matched** |
| Genus sp.| `species` tag is removed, but genus value **is moved** to genus tag |
| name:en  | New `species` values is set, but the old value **is preserved and saved** to name:en tag |




## LICENCE

Unlike other parts of the project the file [tree_typos-bak.csv](tree_typos-bak.csv) is licensed under [CC 0 license](https://creativecommons.org/publicdomain/zero/1.0/deed.ru).