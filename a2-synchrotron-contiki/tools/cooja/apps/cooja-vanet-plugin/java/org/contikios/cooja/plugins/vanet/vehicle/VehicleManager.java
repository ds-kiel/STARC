package org.contikios.cooja.plugins.vanet.vehicle;

import org.contikios.cooja.Mote;
import org.contikios.cooja.plugins.vanet.world.World;
import org.contikios.cooja.plugins.vanet.world.physics.Vector2D;

import java.util.Collection;
import java.util.HashMap;

public class VehicleManager {
    private HashMap<Integer, VehicleInterface> vehicles = new HashMap<>();
    private World world;

    private int initVal = 0;

    private int idCounter = 0;

    public VehicleManager(World world) {
        this.world = world;
    }

    public Collection<VehicleInterface> getVehicleCollection() {
        return vehicles.values();
    }

    public VehicleInterface getVehicle(int id) {
        return this.vehicles.get(id);
    }

    public synchronized void removeVehicle(int id) {
        if (vehicles.containsKey(id)) {
            VehicleInterface v = vehicles.get(id);
            v.destroy();
            // we remove the vehicle as well
            vehicles.remove(id);
        }
    }

    public synchronized VehicleInterface createVehicle(Mote m) {
        int id = idCounter+1;
        idCounter++;

        // for each mote add a new vehicle
        VehicleInterface v = new Vehicle(world, m, id);
        v = new LogAwareVehicleDecorator(v); // check if we want to log!
        v.getBody().setCenter(new Vector2D(
                initVal, initVal
        ));
        vehicles.put(id, v);
        return v;
    }

    public int getTotal() {
        return idCounter;
    }
}
