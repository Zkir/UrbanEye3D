package ru.zkir.josm.plugins.z3dviewer.utils;

import java.util.ArrayList;
import java.util.List;

public class Mesh {
    public List<Point3D> verts = new ArrayList<>();
    public List<int[]> roofFaces = new ArrayList<>();
    public List<int[]> wallFaces = new ArrayList<>();
    public List<int[]> bottomFaces = new ArrayList<>();
}
