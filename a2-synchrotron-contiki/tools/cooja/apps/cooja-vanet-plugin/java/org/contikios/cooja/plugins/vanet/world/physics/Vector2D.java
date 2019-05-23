package org.contikios.cooja.plugins.vanet.world.physics;

public class Vector2D {
    private double x;
    private double y;

    public Vector2D() {
        this(0,0);
    }

    public Vector2D(Vector2D other) {
        this(other.x,other.y);
    }

    public Vector2D(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public void scale(double l) {
        x *= l;
        y *= l;
    }

    public double length() {
        return Math.sqrt(x*x+y*y);
    }

    public void normalize() {
        double l = length();
        if (l > 0) {
            scale(1/l);
        }
    }

    public void translate(Vector2D other) {
        this.x += other.x;
        this.y += other.y;
    }

    public void rotate(double a) {
        double x1 = (float)(x * Math.cos(a) - y * Math.sin(a));
        double y1 = (float)(x * Math.sin(a) + y * Math.cos(a));

        x = x1;
        y = y1;
    }

    public static double distance(Vector2D a, Vector2D b) {
        return Math.sqrt(
                (a.x-b.x)*(a.x-b.x)+(a.y-b.y)*(a.y-b.y)
        );
    }
}
