package org.contikios.cooja.plugins.vanet.vehicle;

import org.contikios.cooja.Mote;
import org.contikios.cooja.plugins.Vanet;
import org.contikios.cooja.plugins.vanet.transport_network.intersection.Lane;
import org.contikios.cooja.plugins.vanet.transport_network.intersection.TiledMapHandler;
import org.contikios.cooja.plugins.vanet.world.World;
import org.contikios.cooja.plugins.vanet.world.physics.Vector2D;

import java.util.*;

public class Vehicle extends BaseVehicle {
    protected MessageProxy messageProxy; // used for communication with cooja motes

    // Configure if the Tiles should be freed when not needed anymore
    static final boolean TILE_FREEDOM = true;

    byte[] wantedRequest = new byte[0];
    byte[] currentRequest = new byte[0];

    protected int requestState = REQUEST_STATE_INIT;

    public Vehicle(World world, Mote m, int id) {
        super(world, m, id);
        messageProxy = new MessageProxy(m);
    }

    public void step(double delta) {
        // handle messages first
        byte[] msg = null;

        msg = messageProxy.receive();
        while (msg != null) {
            handleMessage(msg);
            msg = messageProxy.receive();
        }

        super.step(delta);
        handleReservation();
    }

    // Update the state, return value will be the next state
    protected int handleStates(int state) {

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
                // free part of reservation
                requestReservation();
            }

            if (curWayPointIndex >= waypoints.size()-Lane.STEPS_INTO_LANE+1) {
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
                if (targetLane.isFinalEndLane()) {
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

    protected void handleReservation() {
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

    protected void requestReservation() {
        TiledMapHandler.PathHelper pathHandler = currentIntersection.getMapHandler().createPathHelper();
        for(int i = Math.max(0, curWayPointIndex-1); i < waypoints.size(); ++i) {
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

    protected void handleMessage(byte[] msg) {
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
}