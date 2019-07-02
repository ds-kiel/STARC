package org.contikios.cooja.plugins.vanet.world.physics;


import org.contikios.cooja.plugins.vanet.world.physics.Computation.LaneIntersection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

public class Physics {

    private ArrayList<Body> bodies = new ArrayList<>();
    private Collection<Sensor> sensors = new ArrayList<>();

    public Physics() {

    }

    public void simulate(double delta) {

        // move the bodies based on their velocity
        for (Body body: bodies) {
            Vector2D vel = new Vector2D(body.getVel());
            vel.scale(delta);
            body.getCenter().translate(vel);
        }

        int size = bodies.size();

        for (int i = 0; i < size; i++) {
            Body bodyA = bodies.get(i);

            for (int j = i+1; j < size; j++) {

                Body bodyB = bodies.get(j);

                // else we check collisions!
                if (collides(bodyA, bodyB)) {
                    System.out.println("COLLISION: " +  bodyA.getName() + " with " + bodyB.getName());
                   //Logger.event("collision", 0, bodyA.getName() + " with " + bodyB.getName(), null);
                }
            }
        }

        sensors.forEach( s -> s.update(this, delta));
    }

    public void addBody(Body b) {
        if (!this.bodies.contains(b)) {
            this.bodies.add(b);
        }
    }

    public void removeBody(Body b) {
        this.bodies.remove(b);
    }


    public void addSensor(Sensor s) {
        if (!this.sensors.contains(s)) {
            this.sensors.add(s);
        }
    }

    public void removeSensor(Sensor s) {
        this.sensors.remove(s);
    }

    private boolean collides(Body a, Body b) {

        if (a instanceof CircleBody && b instanceof CircleBody) {
            return CircleBody.doCirclesCollide((CircleBody)a, (CircleBody)b);
        } else {
            return false;
        }
    }

    private Vector2D closestPointOnLine(Vector2D lineStart, Vector2D lineDir, Vector2D pos) {

        Vector2D b = new Vector2D(lineDir);
        b.normalize();
        b.scale(-Vector2D.dot(Vector2D.diff(lineStart, pos), b));

        Vector2D closestPoint = new Vector2D(lineStart);
        closestPoint.add(b);

        return closestPoint;
    }

    private LaneIntersection intersectsLine(Body body, Vector2D linePos, Vector2D lineDir) {

        if (body instanceof CircleBody) {

            CircleBody cBody = (CircleBody) body;
            Vector2D closestPoint = closestPointOnLine(linePos, lineDir, body.getCenter());

            Vector2D diff = Vector2D.diff(closestPoint, linePos);

            double sign = 0.0;
            if(Math.signum(lineDir.getX()) != 0.0) {
                sign = Math.signum(lineDir.getX()) * Math.signum(diff.getX());
            } else if (Math.signum(lineDir.getY()) != 0.0) {
                sign = Math.signum(lineDir.getY()) * Math.signum(diff.getY());
            }

            double d = sign*diff.length();

            double distToLine = Vector2D.distance(body.getCenter(), closestPoint);
            double r = cBody.getRadius();

            // we only have an intersection if the
            if (distToLine <= r) {
                double offset = Math.sqrt(r*r-distToLine*distToLine);

                LaneIntersection in = new LaneIntersection();
                in.body = body;

                in.distance = d;
                return in;
            }
        }
        return null;
    }

    public Collection<LaneIntersection> computeLineIntersections(Vector2D pos, Vector2D dir) {
        // dir has to be normalized!

        return this.bodies.stream()
                .map(b -> intersectsLine(b,pos,dir))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
