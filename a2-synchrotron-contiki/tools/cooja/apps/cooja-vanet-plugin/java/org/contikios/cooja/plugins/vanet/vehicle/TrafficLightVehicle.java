package org.contikios.cooja.plugins.vanet.vehicle;


import org.contikios.cooja.Mote;
import org.contikios.cooja.plugins.Vanet;
import org.contikios.cooja.plugins.vanet.transport_network.intersection.Lane;
import org.contikios.cooja.plugins.vanet.transport_network.intersection.TrafficLightAwareIntersection;
import org.contikios.cooja.plugins.vanet.world.World;
import org.contikios.cooja.plugins.vanet.world.physics.Physics;
import org.contikios.cooja.plugins.vanet.world.physics.Vector2D;

public class TrafficLightVehicle extends BaseOrderVehicle {

    public TrafficLightVehicle(World world, Mote m, int id) {
        super(world, m, id);
    }

    /**
     * One might wonder why this is declared here.
     * The reason for this is to keep driving once we decided to go for it ;)
     */
    protected boolean driveThrough = false;
    protected boolean predecessorWasMoving = false;

    @Override
    protected int handleStates(int state) {

        int trafficLightState = TrafficLightAwareIntersection.PHASE_RED;

        if (currentIntersection instanceof  TrafficLightAwareIntersection) {
            trafficLightState = ((TrafficLightAwareIntersection) currentIntersection)
                                    .getTrafficLightStates(world.getCurrentMS()).get(currentLane);
        }

        if (state == STATE_INIT) {
            init();
            return STATE_INITIALIZED;
        } else if (state == STATE_INITIALIZED) {
            return STATE_QUEUING;
        } else if (state == STATE_QUEUING) {

            // we try to build up a platoon
            updatePredecessor();

            if (trafficLightState == TrafficLightAwareIntersection.PHASE_GREEN) {
                return STATE_MOVING;
            } else {
                return STATE_QUEUING;
            }
        } else if (state == STATE_WAITING) {
            // NOOP: We are either queuing or moving
        }
        else if (state == STATE_MOVING) {
            predecessorWasMoving = false;
            // we try to build up a platoon
            updatePredecessor();

            if (curWayPointIndex >= waypoints.size() - 1) {
                return STATE_LEAVING;
            } else {
                if (trafficLightState == TrafficLightAwareIntersection.PHASE_GREEN) {
                    return STATE_MOVING;
                } else {
                    // we need to check if we could stop before the traffic lights
                    // driveThrough might be true from previous evaluations too...

                    if (curWayPointIndex > 0) {
                        driveThrough = true;
                    } else if (Vector2D.distance(body.getCenter(), waypoints.get(0)) < Vector2D.distance(startPos, waypoints.get(0))) {
                        driveThrough = true;
                    } else {
                        double maxVel = calculateMaxVel(Vector2D.distance(startPos,body.getCenter()));
                        if (body.getVel().length() > maxVel) {
                            driveThrough = true;
                        }
                    }

                    if (driveThrough) {
                        return STATE_MOVING;
                    } else {
                        return STATE_QUEUING;
                    }
                }
            }
        } else if (state == STATE_LEAVING) {

            // we remove ourself from the platoon
            if (successor != null) {
                successor.setPredecessor(null);
            }

            successor = this; // set to ourself to prevent new connections
            predecessor = null;

            return STATE_LEFT;
        }
        else if (state == STATE_LEFT)  {
            if (targetLane.isFinalEndLane()) {
                return STATE_FINISHED;
            } else {
                initLane(targetLane);
                return STATE_QUEUING;
            }
        } else if (state == STATE_FINISHED) {
            return STATE_FINISHED;
        }
        return state;
    }


    @Override
    public Vector2D getNextWaypoint() {
        Vector2D originalWP = null;
        Vector2D nextWP = null;

        if (curWayPointIndex < waypoints.size()) {
            originalWP = waypoints.get(curWayPointIndex);
            nextWP = originalWP;

            Vector2D originDir = Vector2D.diff(body.getCenter(), originalWP);

            if (originDir.length() > 0) {
                double threshold = 0.1 * Vanet.SCALE;
                originDir.normalize();

                int i = curWayPointIndex+1;

                // minus 1 since we do not want the endpoint to be our direct target
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

    @Override
    protected void initLane(Lane lane) {
        super.initLane(lane);
        waypoints.add(targetLane.getEndPos());
        driveThrough = false;
    }

    @Override
    protected boolean updatePredecessor() {
        if (predecessor == null) {
            predecessorWasMoving = false;
        }
        boolean foundNew = super.updatePredecessor();

        if (foundNew) {
            // If we are still queuing we do not want to join passing platoons...
            if (predecessor != null && getState() == STATE_QUEUING && predecessor.getState() == STATE_MOVING) {
                predecessor.setSuccessor(null);
                predecessor = null;
                foundNew = false;
            }
        }
        return foundNew;
    }
}