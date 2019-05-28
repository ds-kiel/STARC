package org.contikios.cooja.plugins.vanet.vehicle;

import org.contikios.cooja.Mote;
import org.contikios.cooja.plugins.vanet.vehicle.physics.VehicleBody;
import org.contikios.cooja.plugins.vanet.world.physics.Vector2D;

import java.util.ArrayList;
import java.util.Arrays;

public class Vehicle {
    private Mote mote; // unique identifier
    private MessageProxy messageProxy; // used for communication with cooja motes
    private VehicleBody body; // our physics model
    private TiledMapHandler mapHandler;

    static final boolean OTHER_DIRECTIONS = true;
    static final boolean TILE_FREEDOM = true;

    static private final int STATE_INIT = 0;
    static private final int STATE_INITIALIZED = 5;
    static private final int STATE_WAITING = 7;
    static private final int STATE_MOVING = 10;
    static private final int STATE_FINISHED = 60;

    private int state = STATE_INIT;





    static private final int STATE_REQUEST_INIT = 0;
    static private final int STATE_REQUEST_SENT = 1;
    static private final int STATE_REQUEST_ACKNOWLEDGED = 2;
    static private final int STATE_REQUEST_ACCEPTED = 3;


    byte[] wantedRequest = new byte[0];
    byte[] currentRequest = new byte[0];

    private int requestState = STATE_REQUEST_INIT;

    public Vehicle(Mote mote, MessageProxy messageProxy, VehicleBody body) {
        this.mote = mote;
        this.messageProxy = messageProxy;
        this.body = body;

        this.mapHandler = new TiledMapHandler(6,6,1,1);

        initPosition();
        initReservation();
    }

    public Mote getMote() {
        return mote;
    }

    public VehicleBody getBody() {
        return body;
    }


    public void step(double delta) {
        // handle messages first
        byte[] msg = null;

        msg = messageProxy.receive();
        while (msg != null) {
            handleMessage(msg);
            msg = messageProxy.receive();
        }


        updateWaypoints();
        // now we will handle our movement
        // we are able to turn and to accelerate/decelerate
        handleVehicle(delta);

        if (state == STATE_INITIALIZED) {
            requestReservation();
            state = STATE_WAITING;
        }

        if (state == STATE_WAITING && requestState == STATE_REQUEST_ACCEPTED) {
            state = STATE_MOVING;
        }

        if (curWayPointIndex >= waypoints.size()) {
            requestReservation();
            state = STATE_FINISHED;
        }


        if (state == STATE_MOVING && TILE_FREEDOM) {
            // free our reservation
            requestReservation();
        }

        handleReservation();
    }

    private void handleReservation() {
        if (!Arrays.equals(wantedRequest, currentRequest)) {
            // we need to update our request
            if (requestState != STATE_REQUEST_SENT) {
                // we can send our new request
                currentRequest = wantedRequest;
                messageProxy.send(currentRequest);
                requestState = STATE_REQUEST_SENT;
            }
        }
    }

    private void requestReservation() {
        TiledMapHandler.PathHelper pathHandler = mapHandler.createPathHelper();
        for(int i = curWayPointIndex; i < waypoints.size(); ++i) {
            pathHandler.reservePos(waypoints.get(i));
        }
        System.out.print("Mote " + mote.getID() + " TILES: ");
        wantedRequest = pathHandler.getByteIndices();

        if (!Arrays.equals(wantedRequest, currentRequest)) {
            // we need to update our request
            if (requestState == STATE_REQUEST_ACCEPTED) {
                requestState = STATE_REQUEST_INIT;
            }
        }
    }

    private void handleMessage(byte[] msg) {
        //System.out.println(new String(msg));

        if (state == STATE_INIT && new String(msg).equals("init")) {
            state = STATE_INITIALIZED;
        }


        // handle request states
        if (requestState == STATE_REQUEST_SENT && new String(msg).equals("ack")) {
            requestState = STATE_REQUEST_ACKNOWLEDGED;
        } else if (requestState == STATE_REQUEST_ACKNOWLEDGED && new String(msg).equals("accepted")) {
            if (Arrays.equals(wantedRequest, currentRequest)) {
                requestState = STATE_REQUEST_ACCEPTED;
            } else {
                requestState = STATE_REQUEST_INIT;
            }
        }
    }


    private boolean mayMove() {
        return state == STATE_MOVING;
    }


    ArrayList<Vector2D> waypoints;
    Vector2D curWayPoint;
    int curWayPointIndex = 0;

    private double odx, ody, opx, opy;

    private void updateWaypoints() {
        if (!mayMove()) {
            curWayPoint = null;
            return;
        }

        if (waypoints.size() == 0 || curWayPointIndex >= waypoints.size()) {
            return; // cant update if there is no way...
        }

        if (curWayPoint == null) {
            curWayPointIndex = 0;
            curWayPoint = waypoints.get(curWayPointIndex);
        }

        Vector2D pos = body.getCenter();
        // we now check the distance to our current wayPoint
        if (Vector2D.distance(curWayPoint, pos) - 0.5 < 0.001) {
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

    private void handleVehicle(double delta) {

        double acc = 0.2;
        double maxSpeed = 1.0;
        double maxTurn = (Math.PI*2)/4; //(360/4 = 90 degrees per second)

        Vector2D vel = body.getVel();
        Vector2D pos = body.getCenter();

        // we check our waypoints
        if (curWayPoint != null) {

            if (vel.length() < 0.0000001) {
                // init dir...
                vel.setX(Math.signum(odx));
                vel.setY(Math.signum(ody));
                vel.scale(0.0000001);
            }

            // compare the wantedDir with the current velocity
            Vector2D wantedDir = Vector2D.diff(curWayPoint, pos);

            double a = Vector2D.angle(vel, wantedDir);
            //System.out.println("Mote " + mote.getID() + " wants to turn " + (a/(Math.PI) * 180));

            // check our steering
            double turn = delta*maxTurn;
            double a2 = Math.abs(a);
            turn = Math.max(-a2, Math.min(a2, turn));
            vel.rotate(Math.signum(a)*turn);

            // compute angle again
            a = Vector2D.angle(wantedDir, vel);

            // check if we want to accelerate or decelerate
            if (Math.abs(a) < Math.PI/4) {

                //System.out.println("Mote " + mote.getID() + " is accelerating");
                // we start to accelerate
                if (vel.length() < maxSpeed) {

                    Vector2D dir = new Vector2D(vel);
                    dir.normalize();

                    dir.scale(delta * acc);

                    // accelerate!
                    vel.translate(dir);
                }

                if (vel.length() > maxSpeed) {
                    vel.scale(maxSpeed/vel.length());
                }
            } else {
                //System.out.println("Mote " + mote.getID() + " is decelerating");
                // we start to decelerate
                // TODO: a bit more realistic?
                if (vel.length() > 0) {
                    vel.scale(Math.pow(0.5, delta));
                }
            }
        } else {
            // just stop
            //System.out.println("Mote " + mote.getID() + " is stopping");
            // we start to decelerate
            // TODO: a bit more realistic?
            if (vel.length() > 0) {
                vel.scale(Math.pow(0.5, delta));
            }
        }
    }



    private void initPosition() {
        // init the wanted position

        int nodeId = this.mote.getID();

        int offset = (nodeId-1)%3;

        switch((int)(nodeId-1)/3) {
            case 0:
                body.setCenter(new Vector2D(-1, 3+offset));
                odx = 1; ody = 0;
                break;
            case 1:
                body.setCenter(new Vector2D(3+offset, 6));
                odx = 0; ody = -1;
                break;
            case 2:
                body.setCenter(new Vector2D(6, 2-offset));
                odx = -1; ody = 0;
                break;
            case 3:
                body.setCenter(new Vector2D(2-offset, -1));
                odx = 0; ody = 1;
                break;
        }

        body.getCenter().translate(new Vector2D(0.5, 0.5)); // move to center of tile

        opx = body.getCenter().getX();
        opy = body.getCenter().getY();
    }



    private void initReservation() {

        waypoints = new ArrayList<>();

        // we will distinguish three cases

        int nodeId = this.mote.getID();

        int offset = (nodeId-1)%3;

        if (offset == 1 || !OTHER_DIRECTIONS) {
            {
                // we will try to move straight

                double x = opx;
                double y = opy;

                // Move TILES_WIDTH tiles straight
                for(int i = 0; i < mapHandler.getWidth(); ++i) {
                    x += odx;
                    y += ody;

                    waypoints.add(new Vector2D(x, y));
                }

                x += odx;
                y += ody;
                waypoints.add(new Vector2D(x, y));
            }
        } else if (offset == 0) {
            // we will try to go left

            double x = opx;
            double y = opy;

            // Move four tiles straight
            for(int i = 0; i < 4; ++i) {
                x += odx;
                y += ody;

                waypoints.add(new Vector2D(x, y));
            }

            // Move three tiles to the left
            for(int i = 0; i < 3; ++i) {
                x += ody;
                y += -odx;

                waypoints.add(new Vector2D(x, y));

            }

            // and set the target which is another tile to the left

            x += ody;
            y += -odx;
            waypoints.add(new Vector2D(x, y));
        } else if (offset == 2) {
            // we will try to go right
            // so we move one into our original position
            // and just one to the right, which is our target position

            waypoints.add(new Vector2D(opx + odx, opy + ody));
            waypoints.add(new Vector2D(opx + odx - ody, opy + ody + odx));
        }
    }
}