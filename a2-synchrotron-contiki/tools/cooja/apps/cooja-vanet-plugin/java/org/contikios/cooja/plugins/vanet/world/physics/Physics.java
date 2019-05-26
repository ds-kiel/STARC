package org.contikios.cooja.plugins.vanet.world.physics;


import org.contikios.cooja.plugins.vanet.log.Logger;

import java.util.ArrayList;

public class Physics {

    private ArrayList<Body> bodies = new ArrayList<Body>();

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
                   //Logger.event("collision", bodyA.getName() + " with " + bodyB.getName());
                }
            }
        }
    }

    public void addBody(Body b) {
        if (!this.bodies.contains(b)) {
            this.bodies.add(b);
        }
    }

    private boolean collides(Body a, Body b) {

        if (a instanceof CircleBody && b instanceof CircleBody) {
            return CircleBody.doCirclesCollide((CircleBody)a, (CircleBody)b);
        } else {
            return false;
        }
    }
}
