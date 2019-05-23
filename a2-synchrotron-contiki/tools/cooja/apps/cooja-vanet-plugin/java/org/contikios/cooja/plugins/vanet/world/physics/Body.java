package org.contikios.cooja.plugins.vanet.world.physics;

abstract class Body {
    protected Vector2D center = new Vector2D();
    protected Vector2D vel = new Vector2D();
    protected Vector2D acc = new Vector2D();

    protected String name;

    public String getName() {
        return name;
    }

    public Vector2D getCenter() {
        return center;
    }

    public void setCenter(Vector2D center) {
        this.center = center;
    }

    public Vector2D getVel() {
        return vel;
    }

    public void setVel(Vector2D vel) {
        this.vel = vel;
    }

    public Vector2D getAcc() {
        return acc;
    }

    public void setAcc(Vector2D acc) {
        this.acc = acc;
    }


    // TODO: We could add mass and force here ;)
}
