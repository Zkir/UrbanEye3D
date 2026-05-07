package ru.zkir.urbaneye3d.roofgenerators.linearprofile;

import ru.zkir.urbaneye3d.BuildingRecipe;
import ru.zkir.urbaneye3d.RenderableElement;
import ru.zkir.urbaneye3d.utils.Mesh;
import ru.zkir.urbaneye3d.utils.Point2D;
import ru.zkir.urbaneye3d.utils.Point3D;

import java.util.ArrayList;
import java.util.List;

public class MesherLinearProfileRectangular  { //extends RoofGenerator

    private final LinearProfiles profile;
    public MesherLinearProfileRectangular(LinearProfiles profile){
        this.profile = profile;
    }

    //@Override
    public Mesh generate(BuildingRecipe building) {
        List<Point2D> basePoints = building.getContour();
        double height = building.height;
        double minHeight = building.minHeight;
        double wallHeight = building.wallHeight;
        double roofHeight = building.roofHeight;

        if (basePoints.size() != 4) {
           //we do not support now complex contours
           //return null and hope for flat roof fallback!
           return  null;
        }

        boolean nowalls = (wallHeight <= minHeight); //Note that it's rather just degenerate walls. Triangle wall faces may still be generated

        // 1. Рассчитать длины сторон
        double[] sideLengths = new double[4];
        Point2D[] sideNormals = new Point2D[4];
        for (int i = 0; i < 4; i++) {
            Point2D p1 = basePoints.get(i);
            Point2D p2 = basePoints.get((i + 1) % 4);
            Point2D sideVector = p2.subtract(p1);
            sideLengths[i] = sideVector.length();
            //Note: we do not know whether this normal looking inside or outside
            //  and right now do not even bother
            sideNormals[i] = new Point2D(-sideVector.y, sideVector.x);
        }

        // 2. Найти самую длинную сторону
        int longestSideIndex = 0;
        for (int i = 1; i < 4; i++) {
            if (sideLengths[i] > sideLengths[longestSideIndex]) {
                longestSideIndex = i;
            }
        }
        if (!building.roofOrientation.isEmpty()) {
            if (building.roofOrientation.equals("across")) {
                longestSideIndex = (longestSideIndex + 1) % 4;
            }
        }else if ( building.roofDirection!=null && !Double.isNaN(building.roofDirection)){
            //convert the given direction from degrees to vector
            double d = Math.toRadians(building.roofDirection);
            var direction = new Point2D(Math.sin(d), Math.cos(d));

            //let's do a simple thing and find the side which normal is closes to the given direction
            double max_dp = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < 4; i++) {
                var dp = direction.dot( sideNormals[i]);
                if (dp > max_dp ){
                    max_dp = dp;
                    longestSideIndex=i;
                }
            }
        }


        // 3. Переупорядочить точки контура
        List<Point2D> orderedPoints = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            orderedPoints.add(basePoints.get((longestSideIndex + i) % 4));
        }

        Point2D A = orderedPoints.get(0);
        Point2D B = orderedPoints.get(1);
        Point2D C = orderedPoints.get(2);
        Point2D D = orderedPoints.get(3);

        // 4. Рассчитать векторы для коротких сторон
        Point2D vecAD = new Point2D(D.x - A.x, D.y - A.y);
        Point2D vecBC = new Point2D(C.x - B.x, C.y - B.y);

        Mesh mesh = new Mesh(building.bottomColor, building.color, building.roofColor);
        List<Point2D> profile = this.profile.getProfile();
        int profileSize = profile.size();

        // 5. Создать вершины для фронтонов
        // Фронтон 1 (начальный)
        for (Point2D p : profile) {
            double x = A.x + p.x * vecAD.x;
            double y = A.y + p.x * vecAD.y;
            double z = wallHeight + p.y * roofHeight;
            mesh.verts.add(new Point3D(x, y, z));
        }

        // Фронтон 2 (конечный)
        for (Point2D p : profile) {
            double x = B.x + p.x * vecBC.x;
            double y = B.y + p.x * vecBC.y;
            double z = wallHeight + p.y * roofHeight;
            mesh.verts.add(new Point3D(x, y, z));
        }

        int front1Start = 0;
        int front1End = profileSize - 1;
        int front2Start = profileSize;
        int front2End = 2 * profileSize - 1;

        // 6. Добавить точки основания (земли)
        int idxAg; // даже если основание отдельно не создается, индексы всё равно нужны.
        int idxBg;
        int idxCg;
        int idxDg;

        if (!nowalls) {
            Point3D Ag = new Point3D(A.x, A.y, minHeight);
            Point3D Bg = new Point3D(B.x, B.y, minHeight);
            Point3D Cg = new Point3D(C.x, C.y, minHeight);
            Point3D Dg = new Point3D(D.x, D.y, minHeight);

            mesh.verts.add(Ag);
            mesh.verts.add(Bg);
            mesh.verts.add(Cg);
            mesh.verts.add(Dg);

            idxAg = front2End + 1;
            idxBg = front2End + 2;
            idxCg = front2End + 3;
            idxDg = front2End + 4;
        } else
        {   //roof corners will be used instead
            idxAg = front1Start;
            idxBg = front2Start;
            idxCg = front2End;
            idxDg = front1End;
        }

        // 7. Создать грани крыши
        // Фронтон 1
        int[] front1Face = new int[profileSize];
        for (int i = 0; i < profileSize; i++) {
            front1Face[i] = front1Start + i;
        }
        mesh.addWallFace(front1Face);

        // Фронтон 2 (в обратном порядке)
        int[] front2Face = new int[profileSize];
        for (int i = 0; i < profileSize; i++) {
            front2Face[i] = front2End - i;
        }
        mesh.addWallFace(front2Face);

        // Скаты между фронтонами
        for (int i = 0; i < profileSize - 1; i++) {
            int v1 = front1Start + i;       // Точка на первом фронтоне
            int v2 = front1Start + i + 1;   // Следующая точка на первом фронтоне
            int v3 = front2Start + i + 1;   // Следующая точка на втором фронтоне
            int v4 = front2Start + i;       // Точка на втором фронтоне

            int[] quadFace = {v1, v4, v3, v2};
            mesh.addRoofFace(quadFace);
        }

        // 8. Создать стены
        if (!nowalls) {
            // Стена AB
            mesh.addWallFace(new int[]{idxBg, front2Start, front1Start, idxAg});
            // Стена BC
            mesh.addWallFace(new int[]{idxCg, front2End, front2Start, idxBg});
            // Стена CD
            mesh.addWallFace(new int[]{idxDg, front1End, front2End, idxCg});
            // Стена DA
            mesh.addWallFace(new int[]{idxAg, front1Start, front1End, idxDg});
        }

        // 9. Создать нижнюю грань (пол)
        mesh.addBottomFace(new int[]{idxAg, idxDg, idxCg, idxBg});

        return mesh;

    }



}
