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

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Vector2D) {
            return x == ((Vector2D) obj).getX() && y == ((Vector2D) obj).getY();
        } else {
            return false;
        }
    }

    public double length() {
        return Math.sqrt(x*x+y*y);
    }

    public void normalize() {
        double l = length();
        if (l > 0) {
            scale(1.0/l);
        }
    }

    public void translate(Vector2D other) {
        add(other);
    }

    public void add(Vector2D other) {
        this.x += other.x;
        this.y += other.y;
    }

    public void sub(Vector2D other) {
        this.x -= other.x;
        this.y -= other.y;
    }

    public void rotate(double a) {
        double x1 = (float)(x * Math.cos(a) - y * Math.sin(a));
        double y1 = (float)(x * Math.sin(a) + y * Math.cos(a));

        x = x1;
        y = y1;
    }

    public static Vector2D diff(Vector2D a, Vector2D b) {
        Vector2D r = new Vector2D(a);
        r.sub(b);
        return r;
    }

    public static double distance(Vector2D a, Vector2D b) {
        return Math.sqrt(
                (a.x-b.x)*(a.x-b.x)+(a.y-b.y)*(a.y-b.y)
        );
    }

    public static double dot(Vector2D a, Vector2D b) {
        return a.x*b.x+a.y*b.y;
    }

    public String toString() {
        return "(" +x + ", " + y +")";
    }

    public static double angle(Vector2D a, Vector2D b) {
        return Math.atan2( a.x*b.y - a.y*b.x, a.x*b.x + a.y*b.y );
    }
}
