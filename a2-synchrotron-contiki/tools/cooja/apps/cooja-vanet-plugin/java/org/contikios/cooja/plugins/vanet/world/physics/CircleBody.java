package org.contikios.cooja.plugins.vanet.world.physics;

public class CircleBody extends Body {

    protected double radius = 0;

    public CircleBody(String name, double radius) {
        this.name = name;
        this.radius = radius;
    }

    public double getRadius() {
        return radius;
    }

    public static boolean doCirclesCollide(CircleBody a, CircleBody b) {
        double minDist = a.getRadius() + b.getRadius();
        double actualDist = Vector2D.distance(a.getCenter(), b.getCenter());

        return actualDist < minDist;
    }
}
