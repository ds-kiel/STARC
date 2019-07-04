package org.contikios.cooja.plugins.vanet.vehicle;

import org.contikios.cooja.Mote;
import org.contikios.cooja.plugins.Vanet;
import org.contikios.cooja.plugins.vanet.transport_network.intersection.Intersection;
import org.contikios.cooja.plugins.vanet.transport_network.intersection.Lane;
import org.contikios.cooja.plugins.vanet.transport_network.intersection.TiledMapHandler;
import org.contikios.cooja.plugins.vanet.vehicle.physics.DirectionalDistanceSensor;
import org.contikios.cooja.plugins.vanet.vehicle.physics.VehicleBody;
import org.contikios.cooja.plugins.vanet.world.World;
import org.contikios.cooja.plugins.vanet.world.physics.Vector2D;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;

public class Vehicle implements VehicleInterface {
    private MessageProxy messageProxy; // used for communication with cooja motes
    private VehicleBody body; // our physics model
    private TiledMapHandler mapHandler;
    private DirectionalDistanceSensor distanceSensor;

    private int id;
    static final boolean OTHER_DIRECTIONS = true;
    static final boolean TILE_FREEDOM = true;


    byte[] wantedRequest = new byte[0];
    byte[] currentRequest = new byte[0];

    private int state = STATE_INIT;
    private int requestState = REQUEST_STATE_INIT;


    private World world;

    private Intersection currentIntersection;


    public Vehicle(World world, Mote m, int id) {

        this.world = world;
        this.id = id;

        messageProxy = new MessageProxy(m);
        body = new VehicleBody(String.valueOf(id));

        body.setCenter(
                new Vector2D(
                        m.getInterfaces().getPosition().getXCoordinate(),
                        m.getInterfaces().getPosition().getYCoordinate()
                )
        );

        distanceSensor = new DirectionalDistanceSensor(body);

        this.mapHandler = world.getMapHandler();
    }


    void init() {
        initRandomPos();

        world.getPhysics().addBody(body);
        world.getPhysics().addSensor(distanceSensor);
    }

    public void destroy() {
        // remove stuff
        world.getPhysics().removeBody(body);
        world.getPhysics().removeSensor(distanceSensor);
    }

    public World getWorld() {
        return world;
    }

    public DirectionalDistanceSensor getDistanceSensor() {
        return distanceSensor;
    }

    public VehicleBody getBody() {
        return body;
    }

    @Override
    public int getState() {
        return state;
    }

    public int getID() {
        return id;
    }

    public void step(double delta) {
        // handle messages first
        byte[] msg = null;

        msg = messageProxy.receive();
        while (msg != null) {
            handleMessage(msg);
            msg = messageProxy.receive();
        }

        state = handleStates(state);

        Vector2D wantedPos = null;

        double threshold = 0.1 * Vanet.SCALE; // we only move if the distance to the waypoint is more than this value...

        if (state == STATE_QUEUING) {
            // and the distance to front
            double sensedDist = distanceSensor.readValue();

            // TODO: check if sensed dist is higher than the distance to startpos plus vehicle size?
            double velDist = body.getVel().length(); // we need to increase the sensor distance when moving

            velDist *= velDist; // quadratic

            threshold += velDist*2; // increase the threshold since we do not want to move over the position!

            if (sensedDist > 1.0 * Vanet.SCALE+velDist*2 || sensedDist == -1.0) { // we start moving, if there is enough space
                wantedPos = startPos;
            }
        } else if (state == STATE_MOVING || state == STATE_LEAVING || state == STATE_LEFT) {
            wantedPos = curWayPoint;
        }

        // now we will handle our movement
        // we are able to turn and to accelerate/decelerate
        handleVehicle(delta, wantedPos, threshold);
        handleReservation();
    }


    Vector2D startPos;
    Vector2D endPos;

    // Update the state, return value will be the next state
    private int handleStates(int state) {

        if (state == STATE_INITIALIZED) {

            byte[] bytes = new byte[2];
            bytes[0] = 'C';
            //TODO: Check that this does not overflow
            bytes[1] = (byte)((currentIntersection.getId()+11)&0xFF);
            messageProxy.send(bytes);
            return STATE_QUEUING;
        } else if (state == STATE_QUEUING) {
            if (Vector2D.distance(startPos, body.getCenter()) < 0.2 * Vanet.SCALE) {
                messageProxy.send("J".getBytes());
                requestReservation();
                return STATE_WAITING;
            } else {
                return STATE_QUEUING;
            }
        } else if (state == STATE_WAITING) {

            if (requestState == REQUEST_STATE_ACCEPTED) {
                return STATE_MOVING;
            } else {
                return STATE_WAITING;
            }
        }
        else if (state == STATE_MOVING) {
            updateWaypoints();


            if (TILE_FREEDOM) {
                // free our reservation
                requestReservation();
            }

            if (curWayPointIndex >= waypoints.size() - 1) {
                requestReservation();
                // Try to leave the network
                messageProxy.send("L".getBytes());
                return STATE_LEAVING;
            } else {
                return STATE_MOVING;
            }
        } else if (state == STATE_LEAVING) {
            updateWaypoints();
            return STATE_LEAVING;
        }
        else if (state == STATE_LEFT)  {
            updateWaypoints();
            if (curWayPointIndex >= waypoints.size()) {
                return STATE_FINISHED;
            } else {
                return STATE_LEFT;
            }

        } else if (state == STATE_FINISHED) {
            // we reset to some other lane
            return STATE_FINISHED;
        }
        return state;
    }

    private void handleReservation() {
        if (!Arrays.equals(wantedRequest, currentRequest)) {
            // we need to update our request
            if (requestState != REQUEST_STATE_SENT) {
                // we can send our new request
                currentRequest = wantedRequest;
                messageProxy.send(currentRequest);
                requestState = REQUEST_STATE_SENT;
            }
        }
    }

    private void requestReservation() {
        TiledMapHandler.PathHelper pathHandler = mapHandler.createPathHelper();
        for(int i = curWayPointIndex; i < waypoints.size(); ++i) {
            pathHandler.reservePos(waypoints.get(i));
        }
        //System.out.print("Vehicle " + id + " TILES: ");
        wantedRequest = pathHandler.getByteIndices();

        if (!Arrays.equals(wantedRequest, currentRequest)) {
            // we need to update our request
            if (requestState == REQUEST_STATE_ACCEPTED) {
                requestState = REQUEST_STATE_INIT;
            }
        }
    }

    private void handleMessage(byte[] msg) {
        //System.out.println(new String(msg));

        if (state == STATE_INIT && new String(msg).equals("init")) {
            init();
            state = STATE_INITIALIZED;
        } else if (state == STATE_LEAVING && new String(msg).equals("left")) {
            // TODO: Introduce a separate join state just as with the requests
            state = STATE_LEFT;
        }

        // handle request states
        if (requestState == REQUEST_STATE_SENT && new String(msg).equals("ack")) {
            requestState = REQUEST_STATE_ACKNOWLEDGED;
        } else if (requestState == REQUEST_STATE_ACKNOWLEDGED && new String(msg).equals("accepted")) {
            if (Arrays.equals(wantedRequest, currentRequest)) {
                requestState = REQUEST_STATE_ACCEPTED;
            } else {
                requestState = REQUEST_STATE_INIT;
            }
        }
    }

    private ArrayList<Vector2D> waypoints;
    private Vector2D curWayPoint;
    private int curWayPointIndex = 0;

    private void updateWaypoints() {

        if (waypoints.size() == 0 || curWayPointIndex >= waypoints.size()) {
            return; // cant update if there is no way...
        }

        if (curWayPoint == null) {
            curWayPointIndex = 0;
            curWayPoint = waypoints.get(curWayPointIndex);
        }

        Vector2D pos = body.getCenter();
        // we now check the distance to our current wayPoint
        if (Vector2D.distance(curWayPoint, pos) - 0.5 * Vanet.SCALE < 0.001) {
            curWayPointIndex++;
            // we use the next waypoint if available...
            if (curWayPointIndex < waypoints.size()) {
                curWayPoint = waypoints.get(curWayPointIndex);
            } else {
                curWayPoint = null;
            }
        }
    }

    public ArrayList<Vector2D> getWaypoints() {
        return waypoints;
    }

    public int getCurWayPointIndex() {
        return curWayPointIndex;
    }

    private void handleVehicle(double delta, Vector2D wantedPos, double threshold) {

        double acc = 4; // m/s*s
        double dec = 8; // m/s*s
        double maxSpeed = 13.8889; // m/s, 50 km/h
        double maxTurn = (Math.PI*2)/4; //(360/4 = 90 degrees per second)

        Vector2D vel = body.getVel();
        Vector2D pos = body.getCenter();
        Vector2D dir = body.getDir();

        Vector2D acceleration = new Vector2D();

        // we check our waypoints
        if (wantedPos != null) {

            // compare the wantedDir with the current direction
            Vector2D wantedDir = Vector2D.diff(wantedPos, pos);

            double a = Vector2D.angle(dir, wantedDir);

            // check our steering
            double turn = delta*maxTurn;
            double a2 = Math.abs(a);
            turn = Math.max(-a2, Math.min(a2, turn));
            dir.rotate(Math.signum(a)*turn);

            // rotate the velocity too
            vel.rotate(Math.signum(a)*turn);

            // compute angle again
            a = Vector2D.angle(wantedDir, dir);

            // check if we want to accelerate or decelerate
            if (Math.abs(a) < Math.PI/4 && Vector2D.distance(wantedPos, pos) > threshold) {

                // we start to accelerate
                double x = maxSpeed-vel.length();
                if (x > 0.1*delta) {
                    acceleration = new Vector2D(dir);
                    acceleration.scale(acc);
                }
            } else {
                // we start to decelerate
                // TODO: a bit more realistic?
                if (vel.length() > 0.1*delta) {
                    acceleration = new Vector2D(dir);
                    acceleration.scale(-dec);
                }
            }
        } else {
            // just stop
            // we start to decelerate
            if (vel.length() > 0.1*delta) {
                acceleration = new Vector2D(dir);
                acceleration.scale(-dec);
            } else {
                vel.setX(0);
                vel.setY(0);
            }
        }

        // per second squared
        acceleration.scale(delta*delta);

        // accelerate!
        vel.translate(acceleration);
    }

    private void initRandomPos() {
        // init the wanted position

        AbstractMap.SimpleImmutableEntry<Lane, Vector2D> res = this.world.getFreePosition();
        Lane lane = res.getKey();
        this.waypoints = new ArrayList<>(); //lane.getWayPoints(this.mapHandler);

        this.body.setCenter(res.getValue()); // move to center of tile
        this.body.setDir(new Vector2D(lane.getDirectionVector()));
        this.body.setVel(new Vector2D()); // reset vel


        this.currentIntersection = lane.getEndIntersection();

        startPos = lane.getEndPos();
        curWayPointIndex = 0;
        curWayPoint = null;
    }
}