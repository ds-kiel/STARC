package org.contikios.cooja.plugins.vanet.world;

import org.contikios.cooja.*;
import org.contikios.cooja.interfaces.Position;
import org.contikios.cooja.plugins.Vanet;
import org.contikios.cooja.plugins.vanet.config.VanetConfig;
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

    // use singleton for easier Access to random and config
    private static World inst = null;

    private Simulation simulation;
    private long currentMS = 0;
    private Random rand;
    private VanetConfig config;

    private Physics physics;
    private VehicleManager vehicleManager;
    private TransportNetwork transportNetwork;


    private MoteType vehicleMoteType;
    private Map<Integer, Mote> moteMap = new HashMap<>();
    private IDGenerator idGenerator;

    public World(Simulation simulation, Random rand, VanetConfig config) {
        inst = this; // initialize singleton

        this.rand = rand;
        this.config = config; // make config static and thus accessible in all other components

        this.simulation = simulation;
        this.physics = new Physics();
        this.transportNetwork = new TransportNetwork(config.getNetworkWidth(), config.getNetworkHeight(), config.getIntersectionType());

        this.vehicleMoteType = simulation.getMoteType("vehicle");
        this.vehicleManager = new VehicleManager(this, config.getIntersectionType());
        this.idGenerator = new IDGenerator(1, 65535); // only allow ids between 1 and 2^16-1

        // we remove all nodes in the beginning
        Arrays.stream(simulation.getMotes()).forEach(simulation::removeMote);
    }

    public TransportNetwork getTransportNetwork() {
        return this.transportNetwork;
    }

    public static long getCurrentMS() {
        return inst.currentMS;
    }
    public static Random getRand() {return inst.rand;}
    public static VanetConfig getConfig() {return inst.config;}

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

        double vehiclesPerSecond = (config.getVehiclesPerHour() / 3600.0);
        int wanted = (int) ((currentMS / 1000.0f) * vehiclesPerSecond) - vehicleManager.getTotal();

        for (int i = 0; i < wanted; ++i) {
            Integer id = idGenerator.next();
            if (id != null) {
                Mote m = vehicleMoteType.generateMote(simulation);
                m.getInterfaces().getMoteID().setMoteID(id);
                initVehicle(m);
                simulation.addMote(m);
            } else {
                break; // no free ids yet
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
        Mote ma = getMote(a);
        Mote mb = getMote(b);

        a.setMote(mb);
        b.setMote(ma);


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
        simulation.removeMote(m);
    }

    public AbstractMap.SimpleImmutableEntry<Lane, Vector2D> getFreePosition() {

        int turn;
        double r = rand.nextDouble();

        /* Use strictly smaller than because nextDouble excludes the 1.0 */
        if (r < config.getLeftTurnRate()) {
            turn = IntersectionLayout.TURN_LEFT;
        } else if (r < config.getLeftTurnRate() + config.getRightTurnRate()) {
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

    public VehicleInterface getVehicleByPhysicsBody(Body body) {
        return vehicleManager.getVehicleCollection().stream()
                .filter(v -> v.getBody() == body).findAny().get();
    }
}
