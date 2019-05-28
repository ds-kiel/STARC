package org.contikios.cooja.plugins.vanet.world;

import org.contikios.cooja.Mote;
import org.contikios.cooja.interfaces.Position;
import org.contikios.cooja.plugins.vanet.vehicle.Vehicle;
import org.contikios.cooja.plugins.vanet.world.physics.Physics;
import org.contikios.cooja.plugins.vanet.world.physics.Sensor;
import org.contikios.cooja.plugins.vanet.world.physics.Vector2D;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

public class World {

    private Physics physics;

    private HashMap<Integer, Vehicle> vehicles = new HashMap<>();
    private Collection<Sensor> sensors = new ArrayList<>();

    public World() {
        this.physics = new Physics();
    }

    public void simulate(double delta) {

        // read the position back from the simulation

        // step through every vehicle and update the position in the simulation
        Collection<Vehicle> vehicleCollection = vehicles.values();
        vehicleCollection.forEach(this::readPosition);

        // simulate components for each step
        this.physics.simulate(delta);

        // update other components here like sensors etc...
        sensors.forEach( s -> s.update(this.physics, delta));

        // step through every vehicle logic and execute it
        vehicleCollection.forEach(v -> v.step(delta));

        // step through every vehicle and update the position in the simulation
        vehicleCollection.forEach(this::writeBackPosition);
    }

    private void writeBackPosition(Vehicle v) {
        Mote mote = v.getMote();
        Position pos = mote.getInterfaces().getPosition();
        Vector2D center = v.getBody().getCenter();
        pos.setCoordinates(center.getX(), center.getY(), 0);
    }

    private void readPosition(Vehicle v) {
        Mote mote = v.getMote();
        Vector2D center = v.getBody().getCenter();
        Position pos = mote.getInterfaces().getPosition();
        center.setX(pos.getXCoordinate());
        center.setY(pos.getYCoordinate());
    }

    public Vehicle getVehicle(int ID) {
        return this.vehicles.get(ID);
    }

    public Vehicle getVehicle(Mote m) {
        return this.vehicles.get(m.getID());
    }

    public void addVehicle(Vehicle v) {
        this.vehicles.put(v.getMote().getID(), v);
        this.sensors.add(v.getDistanceSensor());
        // we also add the vehicle body to the physics
        this.physics.addBody(v.getBody());
        writeBackPosition(v); // support initial position setting
    }
}
