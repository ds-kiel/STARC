package org.contikios.cooja.plugins.vanet.vehicle;

import org.contikios.cooja.Mote;
import org.contikios.cooja.plugins.Vanet;
import org.contikios.cooja.plugins.vanet.transport_network.intersection.Lane;
import org.contikios.cooja.plugins.vanet.transport_network.intersection.TiledMapHandler;
import org.contikios.cooja.plugins.vanet.vehicle.platoon.ChaosPlatoon;
import org.contikios.cooja.plugins.vanet.vehicle.platoon.Platoon;
import org.contikios.cooja.plugins.vanet.vehicle.platoon.PlatoonAwareVehicle;
import org.contikios.cooja.plugins.vanet.world.World;
import org.contikios.cooja.plugins.vanet.world.physics.Vector2D;

import java.util.*;

public class ChaosVehicle extends BaseOrderVehicle implements PlatoonAwareVehicle {

    public class ChaosNetworkState {
        int chaosIndex = -1;

        public int getChaosIndex() {
            return chaosIndex;
        }

        public boolean hasChaosIndex() {
            return chaosIndex != -1;
        }

        public void setChaosIndex(int chaosIndex) {
            this.chaosIndex = chaosIndex;
        }
    }


    protected MessageProxy messageProxy; // used for communication with cooja motes
    protected ChaosStatsHandler chaosStatsHandler; // handle the stats of the chaos motes

    ChaosPlatoon platoon;

    // Configure if the Tiles should be freed when not needed anymore
    static final boolean TILE_FREEDOM = true;

    byte[] wantedRequest = new byte[0];
    byte[] currentRequest = new byte[0];

    protected int requestState = REQUEST_STATE_INIT;
    protected ChaosNetworkState chaosNetworkState;
    protected ChaosPlatoon chaosPlatoon;

    public ChaosVehicle(World world, Mote m, int id) {
        super(world, m, id);
        messageProxy = new MessageProxy(m);
        chaosStatsHandler = new ChaosStatsHandler(id);
        chaosNetworkState = new ChaosNetworkState();
        chaosPlatoon = new ChaosPlatoon(this, -1, chaosNetworkState);
        setPlatoon(chaosPlatoon);
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

    protected boolean isPlatoonHead() {
        return platoon != null && platoon.isHead(this);
    }



    // Update the state, return value will be the next state
    protected int handleStates(int state) {

        if (state == STATE_INITIALIZED) {
            // Change the channel to the one for the intersection
            byte[] bytes = new byte[2];
            bytes[0] = 'C';
            //TODO: Check that this does not overflow
            bytes[1] = (byte)((currentIntersection.getId()+11)&0xFF);
            messageProxy.send(bytes);
            return STATE_QUEUING;
        } else if (state == STATE_QUEUING) {

            updatePredecessor();

            // we always try to join the platoon in front of us
            if (isPlatoonHead() && predecessor instanceof PlatoonAwareVehicle) {
                Platoon predPlatoon = ((PlatoonAwareVehicle) predecessor).getPlatoon();

                if (predPlatoon != chaosPlatoon &&
                    predPlatoon instanceof ChaosPlatoon &&
                    ((ChaosPlatoon) predPlatoon).mayJoin(this)) {

                    // we leave our own platoon :(
                    if (platoon != null) {
                        platoon.leave(this);
                    }

                    // but we are joining a new one \o/
                    ((ChaosPlatoon) predPlatoon).join(this);
                }
            }

            // if we have joined one, we may directly move if it moves
            if (platoon.isMoving()) {
                return STATE_MOVING;
            }

            // only the head of a platoon may join the network
            if (isPlatoonHead()) {
                // TODO: Allow to join before we reach that point!
                if (Vector2D.distance(startPos, body.getCenter()) < 0.2 * Vanet.SCALE) {
                    // and we try to join the chaos network
                    messageProxy.send("J".getBytes());
                    //requestReservation();
                    return STATE_WAITING;
                } else {
                    return STATE_QUEUING;
                }
            } else {
                return STATE_QUEUING;
            }
        } else if (state == STATE_WAITING) {

            if (chaosNetworkState.hasChaosIndex() && requestState == REQUEST_STATE_INIT) {
                requestReservation();
            }

            if (requestState == REQUEST_STATE_ACCEPTED) {
                return STATE_MOVING;
            } else {
                return STATE_WAITING;
            }
        }
        else if (state == STATE_MOVING) {

            updatePredecessor();

            if (TILE_FREEDOM) {
                // free part of reservation
                requestReservation();
            }

            if (curWayPointIndex >= waypoints.size()-Lane.STEPS_INTO_LANE+1) {
                requestReservation();


                // Try to leave the network
                // TODO: If we are in a platoon, we want to leave it but hand over the chaos network!
                if (chaosNetworkState.hasChaosIndex()) {
                    messageProxy.send("L".getBytes());
                    return STATE_LEAVING;
                } else {
                    return STATE_LEFT;
                }
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

        // we always request the reservation for the whole platoon as a head
        platoon.getMembers().forEach(
            v -> {
                for(int i = Math.max(0, v.getCurWayPointIndex()-1); i < v.getWaypoints().size(); ++i) {
                    pathHandler.reservePos(v.getWaypoints().get(i));
                }
            }
        );

        //System.out.print("ChaosVehicle " + getID() + " TILES: ");
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

        if (chaosStatsHandler.supports(msg)) {
            chaosStatsHandler.handle(msg);
        } else if (state == STATE_INIT && new String(msg).equals("init")) {
            init();
            state = STATE_INITIALIZED;
        } else if (state == STATE_LEAVING && new String(msg).equals("left")) {
            // TODO: Introduce a separate join state just as with the requests
            state = STATE_LEFT;
        } else if (state == STATE_WAITING && new String(msg).equals("joined")) {
            chaosNetworkState.setChaosIndex(1); //TODO: Use the real chaos index!
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

    public Platoon getPlatoon() {
        return platoon;
    }

    @Override
    public void setPlatoon(Platoon platoon) {
        if (platoon instanceof ChaosPlatoon) {
            this.platoon = (ChaosPlatoon) platoon;
        } else {
            this.platoon = null;
        }
    }
}