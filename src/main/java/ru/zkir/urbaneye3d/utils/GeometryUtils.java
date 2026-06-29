package ru.zkir.urbaneye3d.utils;

public class GeometryUtils {

    public static class Ray {
        public final Point3D origin;
        public final Point3D direction;

        public Ray(Point3D origin, Point3D direction) {
            this.origin = origin;
            this.direction = direction.normalize();
        }

        public Point3D getPoint(double distance) {
            return origin.add(direction.mult(distance));
        }
    }

    /**
     * Slab method for ray-AABB intersection.
     */
    public static boolean intersectRayAABB(Ray ray, Point3D min, Point3D max) {
        double tmin = (min.x - ray.origin.x) / ray.direction.x;
        double tmax = (max.x - ray.origin.x) / ray.direction.x;

        if (tmin > tmax) {
            double temp = tmin;
            tmin = tmax;
            tmax = temp;
        }

        double tymin = (min.y - ray.origin.y) / ray.direction.y;
        double tymax = (max.y - ray.origin.y) / ray.direction.y;

        if (tymin > tymax) {
            double temp = tymin;
            tymin = tymax;
            tymax = temp;
        }

        if ((tmin > tymax) || (tymin > tmax)) {
            return false;
        }

        if (tymin > tmin) {
            tmin = tymin;
        }

        if (tymax < tmax) {
            tmax = tymax;
        }

        double tzmin = (min.z - ray.origin.z) / ray.direction.z;
        double tzmax = (max.z - ray.origin.z) / ray.direction.z;

        if (tzmin > tzmax) {
            double temp = tzmin;
            tzmin = tzmax;
            tzmax = temp;
        }

        if ((tmin > tzmax) || (tzmin > tmax)) {
            return false;
        }

        return tmax > 0;
    }

    /**
     * Robust ray-polygon intersection for planar non-convex polygons.
     * @return distance to intersection, or Double.NaN if no intersection
     */
    public static double intersectRayPolygon(Ray ray, java.util.List<Point3D> verts, int[] indices) {
        if (indices.length < 3) return Double.NaN;

        // 1. Define the plane of the polygon using the first 3 vertices
        Point3D v0 = verts.get(indices[0]);
        Point3D v1 = verts.get(indices[1]);
        Point3D v2 = verts.get(indices[2]);

        Point3D edge1 = v1.subtract(v0);
        Point3D edge2 = v2.subtract(v0);
        Point3D normal = edge1.cross(edge2).normalize();

        // 2. Find intersection of ray with the plane
        double denom = normal.dot(ray.direction);
        if (Math.abs(denom) < 1e-7) {
            return Double.NaN; // Ray is parallel to the plane
        }

        double t = v0.subtract(ray.origin).dot(normal) / denom;
        if (t < 1e-7) {
            return Double.NaN; // Intersection is behind the ray or too close
        }

        Point3D p = ray.getPoint(t);

        // 3. Point-in-polygon test (2D projection)
        // Find the best axis to drop (the one where the normal is largest)
        double absX = Math.abs(normal.x);
        double absY = Math.abs(normal.y);
        double absZ = Math.abs(normal.z);

        int count = 0;
        int n = indices.length;
        for (int i = 0; i < n; i++) {
            Point3D a = verts.get(indices[i]);
            Point3D b = verts.get(indices[(i + 1) % n]);

            double ax, ay, bx, by, px, py;

            if (absX >= absY && absX >= absZ) { // Project to YZ
                ax = a.y; ay = a.z; bx = b.y; by = b.z; px = p.y; py = p.z;
            } else if (absY >= absX && absY >= absZ) { // Project to XZ
                ax = a.x; ay = a.z; bx = b.x; by = b.z; px = p.x; py = p.z;
            } else { // Project to XY
                ax = a.x; ay = a.y; bx = b.x; by = b.y; px = p.x; py = p.y;
            }

            // Ray casting algorithm in 2D
            if (((ay > py) != (by > py)) &&
                (px < (bx - ax) * (py - ay) / (by - ay) + ax)) {
                count++;
            }
        }

        return (count % 2 != 0) ? t : Double.NaN;
    }
}
