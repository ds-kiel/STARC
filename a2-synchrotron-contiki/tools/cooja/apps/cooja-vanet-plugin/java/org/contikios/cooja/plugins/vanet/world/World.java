package org.contikios.cooja.plugins.vanet.world;

import org.contikios.cooja.*;
import org.contikios.cooja.interfaces.Position;
import org.contikios.cooja.plugins.Vanet;
import org.contikios.cooja.plugins.vanet.transport_network.TransportNetwork;
import org.contikios.cooja.plugins.vanet.transport_network.intersection.Lane;
import org.contikios.cooja.plugins.vanet.transport_network.intersection.TiledMapHandler;
import org.contikios.cooja.plugins.vanet.transport_network.intersection.layout.IntersectionLayout;
import org.contikios.cooja.plugins.vanet.transport_network.intersection.layout.ThreeLaneIntersectionLayout;
import org.contikios.cooja.plugins.vanet.vehicle.VehicleInterface;
import org.contikios.cooja.plugins.vanet.vehicle.VehicleManager;
import org.contikios.cooja.plugins.vanet.world.physics.Body;
import org.contikios.cooja.plugins.vanet.world.physics.Computation.LineIntersection;
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

    private Map<Integer, Mote> moteMap = new HashMap<>();

    private MoteType vehicleMoteType;
    private MoteType initiatorMoteType;

    private IDGenerator idGenerator;

    private double vehiclesPerHour = 0.0f;

    // TODO: We might want to inject the whole config here?
    private double leftTurnRate;
    private double rightTurnRate;

    public World(Simulation simulation, int networkWidth, int networkHeight, int intersectionType, double leftTurnRate, double rightTurnRate) {
        this.simulation = simulation;
        this.vehicleMoteType = simulation.getMoteType("vehicle");
        this.initiatorMoteType = simulation.getMoteType("vehicle");
        this.physics = new Physics();
        this.transportNetwork = new TransportNetwork(networkWidth, networkHeight, intersectionType);
        this.vehicleManager = new VehicleManager(this, intersectionType);
        this.idGenerator = new IDGenerator(1, (2^16)-1);

        this.leftTurnRate = leftTurnRate;
        this.rightTurnRate = rightTurnRate;

        // TODO: This is implementation specific
        // we remove all nodes
        Arrays.stream(simulation.getMotes()).forEach(simulation::removeMote);
        // we place the initial nodes onto the intersections

        if (intersectionType == TransportNetwork.INTERSECTION_TYPE_DECENTRALIZED) {
            placeInitiators();
        }
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
        return this.transportNetwork.getIntersection(0, 0).getMapHandler();
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


    public void simulate(long ms, long deltaMS) {

        double delta = deltaMS / 1000.0;
        this.currentMS = ms;

        spawnVehicles();

        // step through every vehicle and update the position in the simulation
        Collection<VehicleInterface> vehicleCollection = vehicleManager.getVehicleCollection();

        // read the position back from the simulation
        vehicleCollection.forEach(this::readPosition);

        // simulate components for each step
        // update sensors etc...
        this.physics.simulate(delta, currentMS);

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
        Mote mote = moteMap.get(v.getID());
        Position pos = mote.getInterfaces().getPosition();
        Vector2D center = v.getBody().getCenter();
        pos.setCoordinates(center.getX(), center.getY(), 0);
    }

    private void readPosition(VehicleInterface v) {
        Mote mote = moteMap.get(v.getID());
        Vector2D center = v.getBody().getCenter();
        Position pos = mote.getInterfaces().getPosition();
        center.setX(pos.getXCoordinate());
        center.setY(pos.getYCoordinate());
    }

    private void spawnVehicles() {

        if (vehicleMoteType == null) {
            return;
        }

        // TODO: we might want to use the lane probability as well?
        double vehiclesPerSecond = (vehiclesPerHour / 3600.0);
        int wanted = (int) ((currentMS / 1000.0f) * vehiclesPerSecond) - vehicleManager.getTotal();

        for (int i = 0; i < wanted; ++i) {
            Mote m = vehicleMoteType.generateMote(simulation);
            Integer id = idGenerator.next();
            if (id != null) {
                m.getInterfaces().getMoteID().setMoteID(id);
                initVehicle(m);

                simulation.stopSimulation(true);
                simulation.addMote(m);
                simulation.startSimulation();
            }
        }
    }


    public Mote getMote(VehicleInterface v){
        return moteMap.get(v.getID());
    }

    /**
     * Used to swap the motes but not the vehicles, used for an easy handover in virtual platoons
     */
    public void swapMotes(VehicleInterface a, VehicleInterface b) {

        if (a == null) {
            System.out.println("a is null");
        }
        if (b  == null) {
            System.out.println("b is null");
        }


        Mote ma = getMote(a);
        Mote mb = getMote(b);

        a.setMote(mb);
        b.setMote(ma);

        if (ma == null) {
            System.out.println("ma is null");
        }

        if (mb == null) {
            System.out.println("mb is null");
        }

        // change Ids to make change invisible to other components
        int tmpId = ma.getInterfaces().getMoteID().getMoteID();
        ma.getInterfaces().getMoteID().setMoteID(mb.getID());
        mb.getInterfaces().getMoteID().setMoteID(tmpId);

        moteMap.put(a.getID(), mb);
        moteMap.put(b.getID(), ma);

        // we write back the positions of the vehicles
        writeBackPosition(a);
        writeBackPosition(b);
    }

    public Collection<VehicleInterface> getVehicles() {
        return new ArrayList<>(vehicleManager.getVehicleCollection());
    }

    public VehicleInterface getVehicle(Mote m) {
        for (Map.Entry<Integer, Mote> entry : moteMap.entrySet()) {
            if (Objects.equals(m, entry.getValue())) {
                return this.vehicleManager.getVehicle(entry.getKey());
            }
        }
        return null;
    }

    public void initVehicle(Mote m) {
        VehicleInterface v = vehicleManager.createVehicle(m);
        moteMap.put(v.getID(), m);
        writeBackPosition(v); // support initial position setting
    }

    public void removeVehicle(VehicleInterface v) {
        Mote m = moteMap.get(v.getID());
        moteMap.remove(v.getID());
        vehicleManager.removeVehicle(v.getID());
        idGenerator.free(m.getID());

        simulation.stopSimulation(true);
        simulation.removeMote(m);
        simulation.startSimulation();
    }

    public AbstractMap.SimpleImmutableEntry<Lane, Vector2D> getFreePosition() {

        int turn;
        double r = RAND.nextDouble();

        /* Use strictly smaller than because nextDouble excludes the 1.0 */
        if (r < leftTurnRate) {
            turn = IntersectionLayout.TURN_LEFT;
        } else if (r < leftTurnRate + rightTurnRate) {
            turn = IntersectionLayout.TURN_RIGHT;
        } else {
            turn = IntersectionLayout.STRAIGHT;
        }

        Lane l = this.transportNetwork.getRandomStartLaneWithTurn(turn);

        Vector2D d = new Vector2D(l.getDirectionVector());
        d.scale(-1);

        // we translate the endpos a bit such that we check right from the beginning
        Vector2D endPos = new Vector2D(l.getDirectionVector());
        endPos.scale(0.5 * Vanet.SCALE);
        endPos.add(l.getEndPos());

        // we need to check the collision
        Collection<LineIntersection> LineIntersections = this.physics.computeLineIntersections(endPos, d);

        double maxDist = LineIntersections.stream().
                map(i -> i.distance).
                filter(x -> x >= 0.0).
                max(Double::compareTo).
                orElse(0.0);

        Vector2D freePos = new Vector2D(d);
        freePos.scale(maxDist + (0.5 + ThreeLaneIntersectionLayout.LANE_LENGTH) * Vanet.SCALE);
        freePos.add(endPos);
        return new AbstractMap.SimpleImmutableEntry<>(l, freePos);
    }

    public void setVehiclesPerSecond(double vehiclesPerHour) {
        if (vehiclesPerHour != this.vehiclesPerHour) {
            this.vehiclesPerHour = vehiclesPerHour;
            // TODO: if we want to manually change the value, we would need to save the last changed num and time
        }
    }

    public VehicleInterface getVehicleByPhysicsBody(Body body) {
        return vehicleManager.getVehicleCollection().stream()
                .filter(v -> v.getBody() == body).findAny().get();
    }
}
