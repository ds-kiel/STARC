package org.contikios.cooja.plugins.vanet.vehicle.physics;

import org.contikios.cooja.plugins.vanet.world.physics.BodyCollision;
import org.contikios.cooja.plugins.vanet.world.physics.CircleBody;
import org.contikios.cooja.plugins.vanet.world.physics.CollisionAwareBody;
import org.contikios.cooja.plugins.vanet.world.physics.Vector2D;

import java.util.ArrayList;
import java.util.Collection;

public class VehicleBody extends CircleBody implements CollisionAwareBody {

    protected Collection<BodyCollision> collisions = new ArrayList<>();

    public VehicleBody(String name) {
        super(name, 1);
    }

    private Vector2D dir = new Vector2D();

    public Vector2D getDir() {
        return dir;
    }

    public void setDir(Vector2D dir) {
        this.dir = dir;
    }

    @Override
    public void addCollision(BodyCollision bodyCollision) {
        collisions.add(bodyCollision);
    }

    @Override
    public boolean hasCollision(long from, long to) {
        return collisions.stream().anyMatch(
                bc -> from <= bc.getTime() && bc.getTime() < to
        );
    }
}
