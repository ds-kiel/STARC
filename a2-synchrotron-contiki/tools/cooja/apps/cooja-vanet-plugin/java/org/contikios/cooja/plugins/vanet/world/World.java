package org.contikios.cooja.plugins.vanet.world;

import org.contikios.cooja.Mote;
import org.contikios.cooja.interfaces.Position;
import org.contikios.cooja.plugins.vanet.vehicle.Vehicle;
import org.contikios.cooja.plugins.vanet.world.physics.Physics;
import org.contikios.cooja.plugins.vanet.world.physics.Vector2D;

import java.util.ArrayList;

public class World {

    private Physics physics;

    private ArrayList<Vehicle> vehicles = new ArrayList<>();

    public World() {
        this.physics = new Physics();
    }

    public void simulate(double delta) {

        // read the position back from the simulation

        // step through every vehicle and update the position in the simulation
        for (Vehicle v: vehicles) {
            Mote mote = v.getMote();
            Vector2D center = v.getBody().getCenter();
            Position pos = mote.getInterfaces().getPosition();
            center.setX(pos.getXCoordinate());
            center.setY(pos.getYCoordinate());
        }

        // step through every vehicle logic and execute it
        for (Vehicle v: vehicles) {
            v.step(delta);
        }

        // simulate components for each step
        this.physics.simulate(delta);
        // add other components here like sensors etc...

        // step through every vehicle and update the position in the simulation
        for (Vehicle v: vehicles) {
            Mote mote = v.getMote();
            Position pos = mote.getInterfaces().getPosition();
            Vector2D center = v.getBody().getCenter();
            pos.setCoordinates(center.getX(), center.getY(), 0);
        }
    }

    public void addVehicle(Vehicle v) {
        this.vehicles.add(v);
        // we also add the vehicle body to the physics
        this.physics.addBody(v.getBody());
    }
}
