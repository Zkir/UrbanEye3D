package ru.zkir.urbaneye3d.utils;

public class PolygonSelfIntersection {
    public static boolean isSimplePolygon(Point3D[] face) {
        int n = face.length;
        if (n <= 3) return true; // Треугольники и менее не могут самопересекаться

        // Вычисляем нормаль полигона
        Point3D normal = computeNormal(face);
        if (isZeroVector(normal)) {
            // Вырожденный случай: все точки на одной прямой
            return checkDegenerateCase(face);
        }

        // Выбираем плоскость проекции
        int projectionPlane = chooseProjectionPlane(normal);
        Point2D[] points2D = projectPoints(face, projectionPlane);

        // Проверяем пересечения рёбер
        for (int i = 0; i < n; i++) {
            int nextI = (i + 1) % n;
            for (int j = i + 2; j < n; j++) {
                int nextJ = (j + 1) % n;
                if (i == nextJ || j == nextI) continue; // Пропускаем соседние рёбра
                if (segmentsIntersect(points2D[i], points2D[nextI], points2D[j], points2D[nextJ])) {
                    return false;
                }
            }
        }
        return true;
    }

    private static Point3D computeNormal(Point3D[] face) {
        Point3D normal = new Point3D(0, 0, 0);
        Point3D first = face[0];
        for (int i = 1; i < face.length - 1; i++) {
            Point3D v1 = face[i].subtract(first);
            Point3D v2 = face[i + 1].subtract(first);
            normal = v1.cross(v2);
            if (!isZeroVector(normal)) {break;}
        }
        return normal;
    }


    private static boolean isZeroVector(Point3D v) {
        return Math.abs(v.x) < 1e-6 && Math.abs(v.y) < 1e-6 && Math.abs(v.z) < 1e-6;
    }


    private static int chooseProjectionPlane(Point3D normal) {
        double absX = Math.abs(normal.x);
        double absY = Math.abs(normal.y);
        double absZ = Math.abs(normal.z);
        if (absZ >= absX && absZ >= absY) {
            return 0; // Проекция на XY
        } else if (absY >= absX) {
            return 1; // Проекция на XZ
        } else {
            return 2; // Проекция на YZ
        }
    }

    private static Point2D projectPoint(Point3D point, int plane) {
        switch (plane) {
            case 0:
                return new Point2D(point.x, point.y);
            // XY
            case 1:
                return new Point2D(point.x, point.z);
            // XZ
            case 2:
                return new Point2D(point.y, point.z);
            // YZ
            default:
                throw new IllegalArgumentException("Invalid plane");
        }
    }

    private static Point2D[] projectPoints(Point3D[] face, int plane) {
        Point2D[] result = new Point2D[face.length];
        for (int i = 0; i < face.length; i++) {
            result[i] = projectPoint(face[i], plane);
        }
        return result;
    }

    private static boolean segmentsIntersect(Point2D a, Point2D b, Point2D c, Point2D d) {
        int o1 = orientation(a, b, c);
        int o2 = orientation(a, b, d);
        int o3 = orientation(c, d, a);
        int o4 = orientation(c, d, b);

        if (o1 != o2 && o3 != o4) return true;
        if (o1 == 0 && onSegment(a, b, c)) return true;
        if (o2 == 0 && onSegment(a, b, d)) return true;
        if (o3 == 0 && onSegment(c, d, a)) return true;
        return o4 == 0 && onSegment(c, d, b);
    }

    private static int orientation(Point2D a, Point2D b, Point2D c) {
        double val = (b.y - a.y) * (c.x - b.x) - (b.x - a.x) * (c.y - b.y);
        if (Math.abs(val) < 1e-6) return 0;
        return (val > 0) ? 1 : 2;
    }

    private static boolean onSegment(Point2D a, Point2D b, Point2D c) {
        return Math.min(a.x, b.x) <= c.x && c.x <= Math.max(a.x, b.x) &&
                Math.min(a.y, b.y) <= c.y && c.y <= Math.max(a.y, b.y);
    }

    private static boolean checkDegenerateCase(Point3D[] face) {
        // Для вырожденного полигона проверяем, что точки не повторяются и нет пересечений
        // Упрощённая проверка: если все точки коллинеарны, то самопересечения возможны только при повторении точек
        for (int i = 0; i < face.length; i++) {
            for (int j = i + 2; j < face.length; j++) {
                if (face[i].x == face[j].x && face[i].y == face[j].y && face[i].z == face[j].z) {
                    return false; // Найдена повторяющаяся точка
                }
            }
        }
        return true;
    }
}
