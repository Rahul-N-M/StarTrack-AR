package com.example.isst;

public class SatelliteTarget {

    public final String name;
    public final int noradId;
    public final CelestialObject.Type type;
    public final int color;
    public final String emoji;
    public final float size;

    public SatelliteTarget(
            String name,
            int noradId,
            CelestialObject.Type type,
            int color,
            String emoji,
            float size) {
        this.name = name;
        this.noradId = noradId;
        this.type = type;
        this.color = color;
        this.emoji = emoji;
        this.size = size;
    }
}
