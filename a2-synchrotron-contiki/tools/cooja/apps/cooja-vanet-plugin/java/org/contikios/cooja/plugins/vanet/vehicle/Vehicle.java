package org.contikios.cooja.plugins.vanet.vehicle;

import org.contikios.cooja.Mote;
import org.contikios.cooja.plugins.Vanet;
import org.contikios.cooja.plugins.vanet.transport_network.intersection.Intersection;
import org.contikios.cooja.plugins.vanet.transport_network.intersection.Lane;
import org.contikios.cooja.plugins.vanet.transport_network.intersection.TiledMapHandler;
import org.contikios.cooja.plugins.vanet.vehicle.physics.DirectionalDistanceSensor;
import org.contikios.cooja.plugins.vanet.vehicle.physics.VehicleBody;
import org.contikios.cooja.plugins.vanet.world.World;
import org.contikios.cooja.plugins.vanet.world.physics.Physics;
import org.contikios.cooja.plugins.vanet.world.physics.Vector2D;

import java.util.*;

public class Vehicle implements VehicleInterface {
    private MessageProxy messageProxy; // used for communication with cooja motes
    private VehicleBody body; // our physics model
    private DirectionalDistanceSensor distanceSensor;

    private int id;
    static final boolean OTHER_DIRECTIONS = true;
    static final boolean TILE_FREEDOM = false;


    byte[] wantedRequest = new byte[0];
    byte[] currentRequest = new byte[0];

    private int state = STATE_INIT;
    private int requestState = REQUEST_STATE_INIT;

    Vector2D startPos;

    private World world;

    private Intersection currentIntersection;
    private Lane targetLane;


    final double ACCELERATION = 4*0.5; // m/s*s
    final double DECELERATION = 8*0.5; // m/s*s
    final double MAX_SPEED = 13.8889; // m/s, 50 km/h
    final double MAX_TURN = (Math.PI*2.0)/4.0; //(360/4 = 90 degrees per second)


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
    public Intersection getCurrentIntersection() {
        return currentIntersection;
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

        if (state == STATE_QUEUING) {
            wantedPos = startPos;
        } else if (state == STATE_MOVING || state == STATE_LEAVING || state == STATE_LEFT) {
            updateWaypoints();
            wantedPos = getNextWaypoint();
        }

        double maxBrakeDist = wantedPos != null ? Vector2D.distance(wantedPos, body.getCenter()) : 0;

        if (distanceSensor.readValue() >= 0) {
            if (state != STATE_MOVING || curWayPointIndex >= waypoints.size()-3) {
                maxBrakeDist = Math.max(0, Math.min(distanceSensor.readValue() - 2.5*body.getRadius(), maxBrakeDist));
            }
        }
        // now we will handle our movement
        // we are able to turn and to accelerate/decelerate
        drive(delta, wantedPos, maxBrakeDist);
        handleReservation();
    }

    public Vector2D getNextWaypoint() {
        Vector2D originalWP = null;
        Vector2D nextWP = null;

        if (curWayPointIndex < waypoints.size()) {
            originalWP = waypoints.get(curWayPointIndex);
            nextWP = originalWP;

            Vector2D originDir = Vector2D.diff(body.getCenter(), originalWP);

            if (originDir.length() > 0) {
                double threshold = 0.1*Vanet.SCALE;
                originDir.normalize();

                int i = curWayPointIndex+1;

                while(i < waypoints.size()) {
                    Vector2D possWP = waypoints.get(i);
                    double dist = Vector2D.distance(Physics.closestPointOnLine(body.getCenter(), originDir, possWP), possWP);
                    if (dist < threshold) {
                        nextWP = possWP;
                        ++i;
                    } else {
                        break;
                    }
                }
            }
        }
        return nextWP;
    }



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
            // TODO: Allow to join before we reach that point!
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
            return STATE_LEAVING;
        }
        else if (state == STATE_LEFT)  {
            if (curWayPointIndex >= waypoints.size()) {
                if (targetLane.isEndLane()) {
                    return STATE_FINISHED;
                } else {
                    initLane(targetLane);
                    byte[] bytes = new byte[2];
                    bytes[0] = 'C';
                    //TODO: Check that this does not overflow
                    bytes[1] = (byte)((currentIntersection.getId()+11)&0xFF);
                    messageProxy.send(bytes);
                    return STATE_QUEUING;
                }
            } else {
                return STATE_LEFT;
            }

        } else if (state == STATE_FINISHED) {
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
        TiledMapHandler.PathHelper pathHandler = currentIntersection.getMapHandler().createPathHelper();
        for(int i = curWayPointIndex; i < waypoints.size(); ++i) {
            pathHandler.reservePos(waypoints.get(i));
        }
        //System.out.print("Vehicle " + getID() + " TILES: ");
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

    private ArrayList<Vector2D> waypoints = new ArrayList<>();
    private int curWayPointIndex = 0;

    private void updateWaypoints() {
        if (curWayPointIndex >= waypoints.size()) {
            return;
        }

        Vector2D pos = body.getCenter();
        Vector2D curWayPoint = waypoints.get(curWayPointIndex);
        double dist = Vector2D.distance(curWayPoint, pos);

        if (dist < 0.5 * Vanet.SCALE) {
            curWayPointIndex++;
        }
    }

    public ArrayList<Vector2D> getWaypoints() {
        return waypoints;
    }

    public int getCurWayPointIndex() {
        return curWayPointIndex;
    }


    private void handleVehicle(double delta, Vector2D wantedDir, double wantedVel) {
        Vector2D vel = body.getVel();
        Vector2D dir = body.getDir();
        Vector2D acceleration = new Vector2D();

        if (wantedDir != null && wantedDir.length() > 0) {
            // check if we need to rotate
            double a = Vector2D.angle(dir, wantedDir);
            // check our steering
            double turn = Math.signum(a)*Math.min(Math.abs(a), delta*MAX_TURN);
            dir.rotate(turn);
            // rotate the velocity too
            vel.rotate(turn);
            dir.normalize();
        }

        double x = wantedVel-vel.length();

        if (x > ACCELERATION*delta) {
            // we accelerate
            acceleration = new Vector2D(dir);
            acceleration.scale(ACCELERATION);
        } else if (x <= 0.0) {
            // we decelerate
            if (vel.length() > DECELERATION*delta) {
                acceleration = new Vector2D(dir);
                acceleration.scale(-DECELERATION);
            } else {
                vel.setX(0);
                vel.setY(0);
            }
        }

        // per second squared
        acceleration.scale(delta);

        // accelerate!
        vel.translate(acceleration);
    }

    private void drive(double delta, Vector2D wantedPos, double maxBrakeDistance) {

        Vector2D wantedDir = null;
        double wantedVel = 0;

        Vector2D pos = body.getCenter();
        Vector2D dir = body.getDir();

        // we check our waypoints
        if (wantedPos != null) {

            // compare the wantedDir with the current direction
            wantedDir = Vector2D.diff(wantedPos, pos);
            double a = Vector2D.angle(dir, wantedDir);

            wantedVel = MAX_SPEED;
            // we now check if we could brake in the given distance
            if (maxBrakeDistance >= 0.0) {
                double maxVel = Math.sqrt(maxBrakeDistance*2*DECELERATION);
                if (wantedVel > maxVel) {
                    wantedVel = maxVel;
                }
            }

            // we slow down our wantedVelocity based on the angle
            double turnSlowDown = Math.pow(Math.abs(a) / MAX_TURN, 1.0/3.0);
            wantedVel *= (1.0 - Math.min(turnSlowDown, 1.0));
        }

        handleVehicle(delta, wantedDir, wantedVel);
    }

    private void initRandomPos() {
        // init the wanted position

        AbstractMap.SimpleImmutableEntry<Lane, Vector2D> res = this.world.getFreePosition();
        Lane lane = res.getKey();

        this.body.setCenter(res.getValue()); // move to center of tile
        this.body.setDir(new Vector2D(lane.getDirectionVector()));
        this.body.setVel(new Vector2D()); // reset vel

        initLane(lane);
    }

    private void initLane(Lane lane) {
        Collection<Lane> possibleLanes = lane.getEndIntersection().getPossibleLanes(lane);
        // use random lane for now
        // TODO: Use some planned path!
        targetLane = possibleLanes.stream().skip((int) (possibleLanes.size() * World.RAND.nextFloat())).findAny().get();

        this.waypoints = lane.getWayPoints(targetLane);
        this.currentIntersection = lane.getEndIntersection();

        startPos = lane.getEndPos();
        curWayPointIndex = 0;
    }
}