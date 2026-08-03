package com.example.isst;

import android.graphics.Color;

public class CelestialObject {

    public enum Type {
        SUN,
        MOON,
        MERCURY,
        VENUS,
        MARS,
        JUPITER,
        SATURN,
        ISS,
        HUBBLE,
        TIANGONG,
        STAR
    }

    public String name;
    public Type type;
    public double azimuth;
    public double elevation;
    public int color;
    public String emoji;
    public float size;
    public double magnitude;
    public boolean showLabel = true;

    public CelestialObject(String name, Type type, int color, String emoji, float size) {
        this.name = name;
        this.type = type;
        this.color = color;
        this.emoji = emoji;
        this.size = size;
        this.magnitude = 0;
    }

    public CelestialObject(
            String name,
            Type type,
            int color,
            String emoji,
            float size,
            double magnitude,
            boolean showLabel) {
        this.name = name;
        this.type = type;
        this.color = color;
        this.emoji = emoji;
        this.size = size;
        this.magnitude = magnitude;
        this.showLabel = showLabel;
    }

    public boolean isPlanetaryObject() {
        return type == Type.SUN
                || type == Type.MOON
                || type == Type.MERCURY
                || type == Type.VENUS
                || type == Type.MARS
                || type == Type.JUPITER
                || type == Type.SATURN;
    }

    public boolean isSatellite() {
        return type == Type.ISS
                || type == Type.HUBBLE
                || type == Type.TIANGONG;
    }

    public boolean isStar() {
        return type == Type.STAR;
    }

    public static CelestialObject[] createDefaults() {
        return new CelestialObject[]{
                new CelestialObject("Sun", Type.SUN, Color.parseColor("#FFD700"), "☀", 28f),
                new CelestialObject("Moon", Type.MOON, Color.parseColor("#E8E8E8"), "🌙", 24f),
                new CelestialObject("Mercury", Type.MERCURY, Color.parseColor("#B0B0B0"), "☿", 16f),
                new CelestialObject("Venus", Type.VENUS, Color.parseColor("#00E5FF"), "♀", 18f),
                new CelestialObject("Mars", Type.MARS, Color.parseColor("#FF4500"), "♂", 18f),
                new CelestialObject("Jupiter", Type.JUPITER, Color.parseColor("#FFA040"), "♃", 22f),
                new CelestialObject("Saturn", Type.SATURN, Color.parseColor("#FFD700"), "♄", 20f),
                new CelestialObject("ISS", Type.ISS, Color.parseColor("#00E5FF"), "🛰", 20f),
                new CelestialObject("Hubble", Type.HUBBLE, Color.parseColor("#8EE6FF"), "✦", 16f),
                new CelestialObject("Tiangong", Type.TIANGONG, Color.parseColor("#FFDE7A"), "✦", 16f),
        };
    }

    public static CelestialObject[] createSolarSystemDefaults() {
        return new CelestialObject[]{
                new CelestialObject("Sun", Type.SUN, Color.parseColor("#FFD700"), "☀", 28f),
                new CelestialObject("Moon", Type.MOON, Color.parseColor("#E8E8E8"), "🌙", 24f),
                new CelestialObject("Mercury", Type.MERCURY, Color.parseColor("#B0B0B0"), "☿", 16f),
                new CelestialObject("Venus", Type.VENUS, Color.parseColor("#00E5FF"), "♀", 18f),
                new CelestialObject("Mars", Type.MARS, Color.parseColor("#FF4500"), "♂", 18f),
                new CelestialObject("Jupiter", Type.JUPITER, Color.parseColor("#FFA040"), "♃", 22f),
                new CelestialObject("Saturn", Type.SATURN, Color.parseColor("#FFD700"), "♄", 20f),
        };
    }
}
