package org.contikios.cooja.plugins.vanet.world;

import org.contikios.cooja.Cooja;
import org.contikios.cooja.Mote;
import org.contikios.cooja.MoteType;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.contikimote.ContikiMoteType;
import org.contikios.cooja.interfaces.Position;
import org.contikios.cooja.mote.memory.UnknownVariableException;
import org.contikios.cooja.mote.memory.VarMemory;
import org.contikios.cooja.plugins.Vanet;
import org.contikios.cooja.plugins.vanet.transport_network.TransportNetwork;
import org.contikios.cooja.plugins.vanet.transport_network.intersection.Lane;
import org.contikios.cooja.plugins.vanet.transport_network.intersection.TiledMapHandler;
import org.contikios.cooja.plugins.vanet.vehicle.VehicleInterface;
import org.contikios.cooja.plugins.vanet.vehicle.VehicleManager;
import org.contikios.cooja.plugins.vanet.world.physics.Computation.LaneIntersection;
import org.contikios.cooja.plugins.vanet.world.physics.Physics;
import org.contikios.cooja.plugins.vanet.world.physics.Vector2D;

import java.util.*;
import java.util.stream.Collectors;

public class World {

    private Physics physics;
    private VehicleManager vehicleManager;

    private TransportNetwork transportNetwork;

    private long currentMS = 0;

    public static Random RAND;

    private Simulation simulation;

    private Map<VehicleInterface, Mote> moteMap = new HashMap<>();

    private MoteType vehicleMoteType;
    private MoteType initiatorMoteType;

    private IDGenerator idGenerator;

    private double vehiclesPerHour = 0.0f;

    public World(Simulation simulation, int networkWidth, int networkHeight) {
        this.simulation = simulation;
        this.vehicleMoteType = simulation.getMoteType("vehicle");
        this.initiatorMoteType = simulation.getMoteType("vehicle");
        this.physics = new Physics();
        this.transportNetwork = new TransportNetwork(networkWidth,networkHeight);
        this.vehicleManager = new VehicleManager(this);
        this.idGenerator = new IDGenerator(1, 255);


        // TODO: This is implementation specific
        // we remove all nodes
        Arrays.stream(simulation.getMotes()).forEach(simulation::removeMote);
        // we place the initial nodes onto the intersections
        placeInitiators();
    }

    protected void placeInitiators() {

        this.getTransportNetwork().getIntersections().forEach(
                i -> {
                    Mote m = initiatorMoteType.generateMote(simulation);
                    Integer id = idGenerator.next();
                    if (id != null) {
                        Vector2D c = i.getCenter();
                        m.getInterfaces().getMoteID().setMoteID(id);
                        m.getInterfaces().getPosition().setCoordinates(c.getX(), c.getY(), 0);
                        simulation.addMote(m);
                    }
                }
        );
    }

    public TiledMapHandler getMapHandler() {
        return this.transportNetwork.getIntersection(0,0).getMapHandler();
    }

    public TransportNetwork getTransportNetwork() {
        return this.transportNetwork;
    }

    public long getCurrentMS() {
        return currentMS;
    }


    public Physics getPhysics() {
        return physics;
    }

    public void simulate(long deltaMS) {

        // TODO: we should not need to manually addd this here...
        currentMS += deltaMS;
        double delta = deltaMS / 1000.0;

        spawnVehicles();

        // step through every vehicle and update the position in the simulation
        Collection<VehicleInterface> vehicleCollection = vehicleManager.getVehicleCollection();

        // read the position back from the simulation
        vehicleCollection.forEach(this::readPosition);

        // simulate components for each step
        // update sensors etc...
        this.physics.simulate(delta);

        // step through every vehicle logic and execute it
        vehicleCollection.forEach(v -> v.step(delta));

        // step through every vehicle and update the position in the simulation
        vehicleCollection.forEach(this::writeBackPosition);

        // remove unwanted vehicles
        vehicleCollection.stream()
                .filter(v -> v.getState() == VehicleInterface.STATE_FINISHED)
                .collect(Collectors.toList())
                .forEach(this::removeVehicle);
    }

    private void writeBackPosition(VehicleInterface v) {
        Mote mote = moteMap.get(v);
        Position pos = mote.getInterfaces().getPosition();
        Vector2D center = v.getBody().getCenter();
        pos.setCoordinates(center.getX(), center.getY(), 0);
    }

    private void readPosition(VehicleInterface v) {
        Mote mote = moteMap.get(v);
        Vector2D center = v.getBody().getCenter();
        Position pos = mote.getInterfaces().getPosition();
        center.setX(pos.getXCoordinate());
        center.setY(pos.getYCoordinate());
    }

    private void spawnVehicles() {

        if (vehicleMoteType == null) {
            return;
        }

        double vehiclesPerHourPerLane = (vehiclesPerHour/3600.0)*12.0;
        int wanted = (int) ((currentMS/1000.0f)*vehiclesPerHourPerLane) - vehicleManager.getTotal();

        for(int i  = 0; i < wanted; ++i) {
            Mote m = vehicleMoteType.generateMote(simulation);
            Integer id = idGenerator.next();
            if (id != null) {
                m.getInterfaces().getMoteID().setMoteID(id);
                initVehicle(m);
                simulation.addMote(m);
            }
        }
    }

    public VehicleInterface getVehicle(Mote m) {
        for (Map.Entry<VehicleInterface, Mote> entry : moteMap.entrySet()) {
            if (Objects.equals(m, entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    public void initVehicle(Mote m) {
        VehicleInterface v = vehicleManager.createVehicle(m);
        moteMap.put(v, m);
        writeBackPosition(v); // support initial position setting
    }

    public void removeVehicle(VehicleInterface v) {
        Mote m = moteMap.get(v);
        moteMap.remove(v);
        vehicleManager.removeVehicle(v.getID());
        idGenerator.free(m.getID());
        simulation.removeMote(m);
    }

    public AbstractMap.SimpleImmutableEntry<Lane, Vector2D> getFreePosition() {
       Lane l = this.transportNetwork.getRandomStartLane();

       Vector2D d = new Vector2D(l.getDirectionVector());
       d.scale(-1);

       // we translate the endpos a bit such that we check right from the beginning
       Vector2D endPos = new Vector2D(l.getDirectionVector());
       endPos.scale(0.5 * Vanet.SCALE);
       endPos.add(l.getEndPos());


       // we need to check the collision
        Collection<LaneIntersection> laneIntersections = this.physics.computeLineIntersections(endPos,d);

        double maxDist = laneIntersections.stream().
                            map(i -> i.distance).
                            filter(x -> x >= 0.0).
                            max(Double::compareTo).
                            orElse(0.0);

        Vector2D freePos = new Vector2D(d);
        freePos.scale(maxDist + 1.5 * Vanet.SCALE);
        freePos.add(endPos);
        return new AbstractMap.SimpleImmutableEntry<>(l, freePos);
    }

    public void setVehiclesPerSecond(double vehiclesPerHour) {
        if (vehiclesPerHour != this.vehiclesPerHour) {
            this.vehiclesPerHour = vehiclesPerHour;
            // TODO: if we want to manually change the value, we would need to save the last changed num and time
        }
    }
}
