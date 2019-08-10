package org.contikios.cooja.plugins.vanet.vehicle;

import org.contikios.cooja.Mote;
import org.contikios.cooja.plugins.vanet.transport_network.intersection.Lane;
import org.contikios.cooja.plugins.vanet.vehicle.physics.DirectionalDistanceSensor;
import org.contikios.cooja.plugins.vanet.world.World;
import org.contikios.cooja.plugins.vanet.world.physics.Computation.LineIntersection;
import org.contikios.cooja.plugins.vanet.world.physics.Vector2D;

abstract class BaseOrderVehicle extends BaseVehicle implements OrderAwareVehicle {

    public BaseOrderVehicle(World world, Mote m, int id) {
        super(world, m, id);
    }

    protected OrderAwareVehicle predecessor;
    protected OrderAwareVehicle successor;

    protected boolean predecessorWasMoving = false;

    public OrderAwareVehicle getPredecessor() {
        return predecessor;
    }

    public void setPredecessor(OrderAwareVehicle predecessor) {
        this.predecessor = predecessor;
    }

    public OrderAwareVehicle getSuccessor() {
        return successor;
    }

    public void setSuccessor(OrderAwareVehicle successor) {
        this.successor = successor;
    }

    public Lane getTargetLane() {
        return targetLane;
    }

    public Lane getStartLane() {
        return currentLane;
    }

    @Override
    protected void drive(double delta, Vector2D wantedPos, double maxBrakeDistance) {
        if (predecessor != null) {
            // This is just an approximation, we do not use the direction of the movement here...
            double predecessorMaxBrakeDist = calculateBreakDist(predecessor.getBody().getVel().length());
            predecessorMaxBrakeDist += Vector2D.distance(predecessor.getBody().getCenter(), body.getCenter());
            predecessorMaxBrakeDist -= 2.5*body.getRadius();

            predecessorMaxBrakeDist = Math.max(0, predecessorMaxBrakeDist);

            if (maxBrakeDistance < 0 || predecessorMaxBrakeDist < maxBrakeDistance) {
                maxBrakeDistance = predecessorMaxBrakeDist;
            }
        }
        super.drive(delta, wantedPos, maxBrakeDistance);
    }


    protected OrderAwareVehicle checkForPredecessor() {

        double senseDist = calculateBreakDist(body.getVel().length())+2.5*body.getRadius();

        LineIntersection li = DirectionalDistanceSensor.computeNearestBodyCollisions(
                world.getPhysics(),
                body.getCenter(),
                body.getDir(),
                body,
                senseDist
        );

        if (li != null) {
            VehicleInterface sensedVehicle = world.getVehicleByPhysicsBody(li.body);
            if (sensedVehicle instanceof OrderAwareVehicle) {
                return (OrderAwareVehicle) sensedVehicle;
            }
        }
        return null;
    }

    boolean sharesLane(OrderAwareVehicle vehicle) {
        return vehicle.getStartLane() == getStartLane() && vehicle.getTargetLane() == getTargetLane();
    }

    /**
     * @return if a new predecessor was set
     */
    protected boolean updatePredecessor() {

        boolean foundNew = false;

        if (predecessor == null) {
            predecessorWasMoving = false;
            OrderAwareVehicle potPred = checkForPredecessor();

            if (potPred != null
                    && potPred.getState() < VehicleInterface.STATE_LEFT
                    && !(getState() == STATE_QUEUING && potPred.getState() == STATE_MOVING)) {
                if (potPred.getSuccessor() == null && sharesLane(potPred)) {
                    potPred.setSuccessor(this);
                    this.setPredecessor(potPred);
                    foundNew = true;
                }
            }
        }

        if (predecessor != null) {
            // We check if we have to wait while the predecessor is already passing the intersection
            // we need to check with another variable that the predecessor is still moving, since the vehicles are updated sequentially...
            if (getState() == STATE_QUEUING && predecessor.getState() == STATE_MOVING) {
                if(predecessorWasMoving) {
                    predecessor.setSuccessor(null);
                    predecessor = null;
                    foundNew = false;
                }
                predecessorWasMoving = true;
            } else {
                predecessorWasMoving = false;
            }


            if (predecessor != null && predecessor.getState() >= VehicleInterface.STATE_LEFT) {
                predecessor.setSuccessor(null);
                predecessor = null;
                foundNew = false;
            }
        }

        return foundNew;
    }
}