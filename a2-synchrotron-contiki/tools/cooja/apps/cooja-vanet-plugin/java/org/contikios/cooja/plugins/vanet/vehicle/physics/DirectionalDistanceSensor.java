package org.contikios.cooja.plugins.vanet.vehicle.physics;

import org.contikios.cooja.plugins.vanet.world.physics.Computation.Intersection;
import org.contikios.cooja.plugins.vanet.world.physics.Physics;
import org.contikios.cooja.plugins.vanet.world.physics.Sensor;
import org.contikios.cooja.plugins.vanet.world.physics.Vector2D;

import java.util.Collection;

public class DirectionalDistanceSensor implements Sensor  {

    private VehicleBody ownBody;
    private double maxLength;
    private double val = -1.0;

    public DirectionalDistanceSensor(VehicleBody ownBody) {
        this.ownBody = ownBody;
        this.maxLength = 500.0f;
    }

    @Override
    public void update(Physics physics, double delta) {

        Vector2D dir = ownBody.getDir();
        Vector2D pos = ownBody.getCenter();

        Collection<Intersection> intersectedBodies = physics.computeLineIntersections(pos, dir);

        val = -1.0;

        for (Intersection i: intersectedBodies) {
            if (i.body == ownBody) {
                continue; // no junction with ourself ;)
            }

            double v = i.distance;
            if (v >= 0.0 && v <= maxLength && (val == -1.0 || v < val)) {
                val = v;
            }
        }
    }

    public double readValue() {
        return val;
    }
}
