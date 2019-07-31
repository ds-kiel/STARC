package org.contikios.cooja.plugins.vanet.vehicle.physics;

import org.contikios.cooja.plugins.Vanet;
import org.contikios.cooja.plugins.vanet.world.physics.Body;
import org.contikios.cooja.plugins.vanet.world.physics.Computation.LineIntersection;
import org.contikios.cooja.plugins.vanet.world.physics.Physics;
import org.contikios.cooja.plugins.vanet.world.physics.Sensor;
import org.contikios.cooja.plugins.vanet.world.physics.Vector2D;

import java.util.Collection;

public class DirectionalDistanceSensor implements Sensor  {

    private VehicleBody ownBody;
    private double maxLength;
    private double val = -1.0;

    private LineIntersection lineIntersection;

    public DirectionalDistanceSensor(VehicleBody ownBody) {
        this.ownBody = ownBody;
        this.maxLength = 500.0f* Vanet.SCALE;
    }

    @Override
    public void update(Physics physics, double delta) {

        Vector2D dir = ownBody.getDir();
        Vector2D pos = ownBody.getCenter();

        lineIntersection = computeNearestBodyCollisions(physics, pos, dir, ownBody, maxLength);
    }

    public static LineIntersection computeNearestBodyCollisions(Physics physics, Vector2D pos, Vector2D dir, Body except, double maxLength) {
        Collection<LineIntersection> intersectedBodies = physics.computeLineIntersections(pos, dir);

        double val = -1;
        LineIntersection li = null;
        for (LineIntersection i: intersectedBodies) {
            if (i.body == except) {
                continue; // no intersection with ourself ;)
            }

            double v = i.distance;
            if (v >= 0.0 && v <= maxLength && (val == -1.0 || v < val)) {
                val = v;
                li = i;
            }
        }
        return li;
    };

    public double readValue() {
        if (lineIntersection != null) {
            return lineIntersection.distance;
        } else {
            return -1.0;
        }
    }

    public LineIntersection getLineIntersection() {
        return lineIntersection;
    }
}
