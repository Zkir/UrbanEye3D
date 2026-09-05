package ru.zkir.urbaneye3d.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MeshOperations {

    public static Mesh createCube(){

        Mesh mesh = new Mesh();
        // Base dimensions: length along Y axle, widthParam along X axle
        double halfX = 0.5;
        double halfY = 0.5;

        Point3D[] verts = new Point3D[8];

        // Bottom ring (4 vertices) -- added as bottom faces group
        verts[0] = new Point3D(halfX, halfY, 0);              // +X, +Y
        verts[1] = new Point3D(-halfX, halfY, 0);             // -X, +Y
        verts[2] = new Point3D(-halfX, -halfY, 0);            // -X, -Y
        verts[3] = new Point3D(halfX, -halfY, 0);             // +X, -Y

        // Top ring (4 vertices) -- added as roof faces group
        verts[4] = new Point3D(halfX, halfY, 1);         // +X, +Y, +Z
        verts[5] = new Point3D(-halfX, halfY, 1);        // -X, +Y, +Z
        verts[6] = new Point3D(-halfX, -halfY, 1);       // -X, -Y, +Z
        verts[7] = new Point3D(halfX, -halfY, 1);        // +X, -Y, +Z

        for (int i = 0; i < 8; i++) {
            mesh.addVertex(verts[i]);
        }

        int[][] faces = new int[][]{
                {3, 2, 1, 0},           // bottom face
                {4, 5, 6, 7},           // top face
                {0, 1, 5, 4},           // front face
                {1, 2, 6, 5},           // left face
                {2, 3, 7, 6},           // back face
                {3, 0, 4, 7}            // right face
        };
        mesh.addBottomFace(faces[0]);
        mesh.addRoofFace(faces[1]);
        mesh.addWallFace(faces[2]);
        mesh.addWallFace(faces[3]);
        mesh.addWallFace(faces[4]);
        mesh.addWallFace(faces[5]);

        return mesh;
    }
    public static void scale(Mesh mesh, double sx, double sy,  double sz){
        mesh.scale(sx, sy, sz);
    }

    public static void scale(Mesh mesh, List<Point3D> verts, double sx, double sy, double sz){
        for (int i = 0; i < verts.size(); i++) {
            var v=verts.get(i);
            v.x = v.x * sx;
            v.y = v.y * sy;
            v.z = v.z * sz;
        }
        // Invalidate bounding box and vertex cache as coordinates have changed
        mesh.invalidateBBOX();
    }

    public static List<Point3D> selectVerticesByZ(Mesh mesh, double z1, double z2){

        List<Point3D> vertices = new ArrayList<>();

        for (Point3D v:mesh.verts){
            if ((v.z>=z1) &&(v.z<=z2)){
                vertices.add(v);
            }

        }
        return  vertices;
    };

    public static void insertHorizontalEdgeRing(Mesh mesh, double zPosition) {
        List<Point3D> vertices = mesh.verts;

        // Находим вершины ниже и выше заданной позиции
        List<Integer> bottomVerts = new ArrayList<>();
        List<Integer> topVerts = new ArrayList<>();

        for (int i = 0; i < vertices.size(); i++) {
            if (vertices.get(i).z < zPosition) {
                bottomVerts.add(i);
            } else if (vertices.get(i).z > zPosition) {
                topVerts.add(i);
            }
        }

        // Находим и удаляем боковые грани, которые пересекают zPosition
        List<int[]> wallFaces = mesh.getWallFaces();
        List<int[]> facesToRemove = new ArrayList<>();

        for (int[] face : wallFaces) {
            boolean hasBottomVertex = false;
            boolean hasTopVertex = false;

            for (int vertexIndex : face) {
                if (bottomVerts.contains(vertexIndex)) {
                    hasBottomVertex = true;
                }
                if (topVerts.contains(vertexIndex)) {
                    hasTopVertex = true;
                }
            }

            // Если грань содержит вершины и снизу, и сверху - она пересекает кольцо
            if (hasBottomVertex && hasTopVertex) {
                facesToRemove.add(face);
            }
        }

        // Удаляем пересекающие грани
        for (int[] face : facesToRemove) {
            removeFace(mesh, face);
        }

        // Создаем новые боковые грани
        // Для каждой удаленной грани создаем две новые
        for (int[] removedFace : facesToRemove) {
            // Определяем, какие вершины снизу, а какие сверху
            List<Integer> faceBottomVerts = new ArrayList<>();
            List<Integer> faceTopVerts = new ArrayList<>();

            for (int vertexIndex : removedFace) {
                if (bottomVerts.contains(vertexIndex)) {
                    faceBottomVerts.add(vertexIndex);
                } else if (topVerts.contains(vertexIndex)) {
                    faceTopVerts.add(vertexIndex);
                }
            }

            // Находим соответствующие новые вершины кольца
            // Это зависит от порядка вершин в грани
            // Предполагаем, что грани идут по часовой стрелке

            // Нижняя грань


            // Находим соответствующие новые вершины
            Point3D p0 = vertices.get(faceBottomVerts.get(0)).add(vertices.get(faceTopVerts.get(1))).mult(0.5); // Правая
            Point3D p1 = vertices.get(faceBottomVerts.get(1)).add(vertices.get(faceTopVerts.get(0))).mult(0.5); // Левая
            p0.z=zPosition;
            p1.z=zPosition;

            // Определяем, какие новые вершины соответствуют
            int newVert0 = mesh.addVertex(p0);  //правая "средняя"
            int newVert1 = mesh.addVertex(p1);  //левая  "средняя"

            int[] bottomFace = new int[4];
            bottomFace[0] = faceBottomVerts.get(0); //правая нижняя
            bottomFace[1] = faceBottomVerts.get(1); //левая нижняя
            bottomFace[2] = newVert1; // Левая верняя
            bottomFace[3] = newVert0; // Правая верхняя
            mesh.addWallFace(bottomFace);

            // Верхняя грань
            int[] topFace = new int[4];
            topFace[0] = newVert0;// правая нижняя
            topFace[1] = newVert1; //левая нижняя
            topFace[2] = faceTopVerts.get(0); //левая верхня.
            topFace[3] = faceTopVerts.get(1); //правая верхняя

            mesh.addWallFace(topFace);
        }

    }


    private static void removeFace(Mesh mesh, int[] face){
        List<int[]> faces = mesh.faces;

        // Ищем грань для удаления
        for (int i = 0; i < faces.size(); i++) {
            int[] currentFace = faces.get(i);

            if (currentFace.length == face.length) {
                // Создаем копии для сортировки
                int[] sortedCurrent = currentFace.clone();
                int[] sortedFace = face.clone();
                Arrays.sort(sortedCurrent);
                Arrays.sort(sortedFace);

                // Сравниваем отсортированные массивы
                if (Arrays.equals(sortedCurrent, sortedFace)) {
                    faces.remove(i);
                    mesh.faceMaterials.remove(i);
                    mesh.faceUVs.remove(i);
                    break;  // удаляем только первое совпадение
                }
            }
        }

    }

    private static int findCorrespondingRingVertex(Point3D originalVert, Point3D[] ringVerts, int[] newIndices) {
        // Находим вершину кольца с теми же X и Y координатами
        for (int i = 0; i < ringVerts.length; i++) {
            if (Math.abs(originalVert.x - ringVerts[i].x) < 0.001 &&
                    Math.abs(originalVert.y - ringVerts[i].y) < 0.001) {
                return newIndices[i];
            }
        }
        return -1; // не найдено
    }
}
