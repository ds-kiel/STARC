package org.contikios.cooja.plugins.vanet.world.physics;

import java.util.Collection;

public class BodyCollision {
    long time;

    Collection<Body> bodies;

    public BodyCollision(long time, Collection<Body> bodies) {
        this.time = time;
        this.bodies = bodies;
    }

    public long getTime() {
        return time;
    }

    public Collection<Body> getBodies() {
        return bodies;
    }
}
