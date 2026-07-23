package ru.zkir.urbaneye3d.assetconfig;

public class DistanceRange {
    public final double minDistance;
    public final double maxDistance;

    public DistanceRange(double minDistance, double maxDistance) {
        this.minDistance = minDistance;
        this.maxDistance = maxDistance;
    }

    public boolean matches(double distance) {
        return distance >= minDistance && distance <= maxDistance;
    }
}
