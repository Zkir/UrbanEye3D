# JOSM 3D Viewer Plugin

## Goal

Create a JOSM plugin that displays loaded buildings (including `building:part=*`) in a separate 3D window.

## Benefits for Mappers

This plugin provides significant practical advantages for OpenStreetMap mappers working with JOSM:

1.  **Instant Visual Data Validation:** Mappers can immediately see how their 2D data (building outlines, `height`, `min_height`, `roof:shape` tags, etc.) translate into a 3D model. This allows for:
    *   **Error Detection:** Incorrect heights, wrong roof types, or misaligned outlines become immediately apparent in 3D, which might be easily missed in 2D. For example, a "floating" roof or walls that don't meet the roof are instantly visible.
    *   **Consistency Checks:** Ensure that adjacent buildings or parts of a single building appear harmonious and accurately reflect real-world structures.

2.  **Improved Data Quality:** The ability to quickly visually inspect data encourages mappers to provide more accurate and complete tagging for 3D modeling, thereby enhancing the overall detail and quality of OpenStreetMap data.

3.  **Understanding Complex Geometries:** For buildings with intricate outlines, courtyards, or multiple parts (`building:part=*`), 3D visualization greatly simplifies the comprehension of their structure and interrelationships, which is challenging to grasp from a flat 2D map.

4.  **Efficient Workflow:** Instead of exporting data to external 3D software for verification, mappers receive immediate feedback directly within JOSM. This accelerates the mapping process and reduces iteration cycles.

5.  **Learning and Adoption of New Tags:** For new mappers or those learning about 3D-related tags, the plugin serves as an excellent tool to understand how each tag influences the final 3D representation.

In essence, this plugin is a powerful tool for **quality control, debugging, and data improvement** directly within the mapping workflow, making a mapper's work more precise, efficient, and enjoyable.
