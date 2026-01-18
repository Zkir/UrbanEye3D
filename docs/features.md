# Supported Features

This document outlines the OpenStreetMap tags supported by the UrbanEye3D plugin for rendering 3D buildings.

## Building

The plugin visualizes objects tagged with `building` and `building:part`.

### Height and Levels

The following tags are used to determine the dimensions of the building:

- `height`: The total height of the building, including the roof.
- `building:height`: An alternative to `height`.
- `min_height`: The height of the building's base from the ground.
- `building:levels`: The number of floors. This is used to calculate the building's height if `height` is not specified (assuming a default height per level).
- `building:min_level`: The number of floors the building is raised from the ground.

### Roof Dimensions

- `roof:height`: The height of the roof.
- `roof:levels`: The number of levels the roof occupies. Used to calculate `roof:height` if not specified.

### Color and Material

- `building:colour` or `colour`: The color of the building's walls.
- `roof:colour`: The color of the roof.
- `building:material`: The material of the walls. This can influence the default color.
- `roof:material`: The material of the roof. This can influence the default color.

### Other Tags

- `roof:direction`: Specifies the direction for certain roof types, like `skillion`. Can be a numerical value (0-360) or a cardinal direction (N, E, S, W as well as NE, SW, etc.).
- `roof:orientation`: Defines the orientation for roofs like `gabled` and `saltbox` (e.g., `along` or `across`).

## Supported Roof Shapes (`roof:shape`)

Below is a list of all supported `roof:shape` values. The [S3DB standard](https://wiki.openstreetmap.org/wiki/Simple_3D_Buildings) is supported, as well as several additional roof shapes.

### `flat`
A standard flat roof. If `roof:levels` or `roof:height` are specified, a [fascia](https://en.wikipedia.org/wiki/Fascia_(architecture)) is created.

![Image of a flat roof](images/roof_flat.png)

### `skillion`
A single-sloped roof surface. The direction is controlled by `roof:direction`. `roof:direction` can be arbitrary.
![Image of a skillion roof](images/roof_skillion.png)

### `gabled`
A classic roof with two sloping sides meeting at a ridge. The orientation (`along` or `across`) can be specified with `roof:orientation`.
`roof:direction` is also supported, but roof orientation snaps to the nearest along/across direction.

![Image of a gabled roof](images/roof_gabled.png)


### `hipped`
A roof where all sides slope downwards to the walls.

![Image of a hipped roof](images/roof_hipped.png)

A straight skeleton [algorithm](https://en.wikipedia.org/wiki/Straight_skeleton) is used. So in the case of non-convex building footprints, interesting shapes are created.
Luckily, they are quite close to real architecture.

![hipped_roof_straight_skeleton](images/hipped_roof_straight_skeleton.png)

Since this roof shape is completely automatic, neither `roof:direction` nor `roof:orientation` have any effect.


### `half-hipped`
A combination of a gabled and a hipped roof.

![Image of a half-hipped roof](images/roof_half-hipped.png)

The orientation (`along` or `across`) can be specified with `roof:orientation`.
`roof:direction` is also supported, but roof orientation snaps to the nearest along/across direction.

This roof shape is supported for quadrangular bases only.


### `gambrel`
A symmetrical two-sided roof with two slopes on each side.

![Image of a gambrel roof](images/roof_gambrel.png)

The orientation (`along` or `across`) can be specified with `roof:orientation`.
`roof:direction` is also supported, but roof orientation snaps to the nearest along/across direction.

### `round`
A round, semi-cylinder or hangar-like roof.

![Image of a round roof](images/roof_round.png)

The orientation (`along` or `across`) can be specified with `roof:orientation`.
`roof:direction` is also supported, but roof orientation snaps to the nearest along/across direction.

### `mansard`
A four-sided gambrel-style hip roof characterized by two slopes on each of its sides.

![Image of a mansard roof](images/roof_mansard.png)

Only quadrangular bases are supported.

### `pyramidal` (or `cone`)
A roof that rises to a single point. Suitable for arbitrary bases. `roof:shape=pyramidal` and `roof:shape=cone` are considered to be complete synonyms.
To get an actual cone, you need a circular base with enough nodes.

![Image of a pyramidal roof](images/roof_pyramidal.png)

Keys `roof:orientation` and `roof:direction` have no effect for the obvious reason.

### `dome`
A hemispherical roof. Like `pyramidal`, this roof shape is suitable for arbitrary bases. To get a traditional dome, you need a circular base with enough nodes.
In the case of a rectangular base, you will get a modern-like dome.

![Image of a dome roof](images/roof_dome.png)

### `onion`
An onion-shaped dome, often found on churches.

![Image of an onion roof](images/roof_onion.png)

Currently, there are no additional parameters to control the onion shape.
Note: here we follow the F4 map approach, and an original OSM way/relation defines a [tholobate](https://en.wikipedia.org/wiki/Tholobate), and the onion itself gets wider.


### `saltbox`
There is no consensus in OSM about what `roof:shape=saltbox` should mean. Here we just follow the F4 map interpretation.

![Image of a saltbox roof](images/roof_saltbox.png)

The keys `roof:orientation` and `roof:direction` should work as for other similar roof shapes.

### `cross_gabled` (or `crosspitched`)
Two gabled roof sections are put together at right angles.

![Image of a cross_gabled roof](images/roof_cross_gabled.png)

Only quadrangular footprints are supported. Neither `roof:orientation` nor `roof:direction` has any effect.

### `side_hipped`
A one-sided hipped roof: hipped from one side and gabled from the other. Only quadrangular footprints are supported.

![Image of a side_hipped roof](images/roof_side_hipped.png) ![side_hipped_direction explanation](images/RoofOrientationForSideHippedExplaining.jpg)

The roof orientation can be controlled via the `roof:direction` tag. The direction value signifies the direction from the roof centroid to the triangular face. `roof:direction=0` or `roof:direction=north` in this example.


This roof shape is particularly useful for building parts, to easily create a building like this:

![side_hipped roof example](images/side_hipped_building_example.png)


### `half-dome`
Half of a dome roof. Especially useful for orthodox church architecture.

![Image of a half-dome roof](images/roof_half-dome.png)

The apex of the dome is located above the middle of the longest side of the base.

### `apse_gabled`
This is a gabled roof with a semicircular apse at one end.

![Image of a apse_gabled roof](images/roof_apse_gabled.png)

The apex of the apse is located above the middle of the longest side of the base.

## Barriers

The plugin also supports rendering of `barrier` objects (`barrier=*`)

- `barrier=wall`: Rendered as a wall with a default width of 0.25m and height of 1.5m.
- `barrier=hedge`: Rendered as a hedge with a default width of 0.5m and height of 1.5m.
- `barrier=fence`: Rendered as a fence with a default width of 0.1m and height of 1.5m.
- `barrier=city_wall`: Rendered as a wall with a default width of 1m and height of 5m.

For all barrier types, the following tags can be used to override the default values:

- `height`: The height of the barrier.
- `width`: The width of the barrier.
- `colour`: The color of the barrier.

Note that in OSM `barrier=*` is considered to be a linear object, even if the way is closed. To override this, use the `area=yes` tag. In the later case the `width` tag is not applied. 