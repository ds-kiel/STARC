package org.contikios.cooja.plugins.vanet.world.physics;

public interface CollisionAwareBody {
    void addCollision(BodyCollision bodyCollision);
    boolean hasCollision(long from, long to);
}
