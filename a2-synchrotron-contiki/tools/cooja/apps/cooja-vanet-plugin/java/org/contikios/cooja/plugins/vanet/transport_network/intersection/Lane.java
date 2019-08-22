package org.contikios.cooja.plugins.vanet.transport_network.intersection;

import org.contikios.cooja.plugins.Vanet;
import org.contikios.cooja.plugins.vanet.transport_network.intersection.layout.IntersectionLayout;
import org.contikios.cooja.plugins.vanet.world.physics.Physics;
import org.contikios.cooja.plugins.vanet.world.physics.Vector2D;

import java.util.ArrayList;

public class Lane {

    protected Intersection startIntersection;
    protected Vector2D startPos;
    protected int startId;

    protected Intersection endIntersection;
    protected Vector2D endPos;
    protected int endId;

    public static final int STEPS_INTO_LANE = 3;

    static final int DIR_UP = 0;
    static final int DIR_DOWN = 1;
    static final int DIR_LEFT = 2;
    static final int DIR_RIGHT = 3;

    public Lane() {}

    public Intersection getStartIntersection() {
        return startIntersection;
    }

    public void setStartIntersection(Intersection startIntersection) {
        this.startIntersection = startIntersection;
    }

    public Vector2D getStartPos() {
        return startPos;
    }

    public void setStartPos(Vector2D startPos) {
        this.startPos = startPos;
    }

    public int getStartId() {
        return startId;
    }

    public void setStartId(int startId) {
        this.startId = startId;
    }

    public Intersection getEndIntersection() {
        return endIntersection;
    }

    public void setEndIntersection(Intersection endIntersection) {
        this.endIntersection = endIntersection;
    }

    public Vector2D getEndPos() {
        return endPos;
    }

    public void setEndPos(Vector2D endPos) {
        this.endPos = endPos;
    }

    public int getEndId() {
        return endId;
    }

    public void setEndId(int endId) {
        this.endId = endId;
    }

    public Vector2D getDirectionVector() {
        Vector2D dir = Vector2D.diff(endPos, startPos);
        dir.normalize();
        return dir;
    }

    public int getDirection() {
        Vector2D d = getDirectionVector();
        if(d.getX() > 0.5) {
            return DIR_RIGHT;
        } else if (d.getX() < -0.5) {
            return DIR_LEFT;
        } else if (d.getY() > 0.5) {
            return DIR_DOWN;
        } else  {
            return DIR_UP;
        }
    }

    public int computeTurn(Lane other) {

        Vector2D ownDir = getDirectionVector();
        Vector2D otherDir = other.getDirectionVector();

        if (ownDir.equals(otherDir)) {
            return IntersectionLayout.STRAIGHT;
        } else if (ownDir.getX() == -otherDir.getY() && ownDir.getY() == otherDir.getX()) {
            return IntersectionLayout.TURN_LEFT;
        } else {
            return IntersectionLayout.TURN_RIGHT;
        }
    }


    public int getId(Intersection intersection) {
        if (intersection == endIntersection){
            return endId;
        } else if (intersection == startIntersection) {
            return startId;
        } else {
            return 0;
        }
    }

    public ArrayList<Vector2D> getWayPoints(Lane targetLane) {

        Vector2D dirStep = new Vector2D(getDirectionVector());
        dirStep.scale(Vanet.SCALE);

        ArrayList<Vector2D> waypoints = new ArrayList<>();

        Vector2D p = new Vector2D(this.endPos);

        // we will distinguish three cases
        if (getDirection() == targetLane.getDirection()) {
            // we will try to move straight
            // Move TILES_WIDTH+1 tiles straight
            for(int i = 0; i < 6; ++i) {
                p.add(dirStep);
                waypoints.add(new Vector2D(p));
            }
            // add step into the lane
            p.add(dirStep);
        } else {
            // we compute the "intersection" points between our lane direction and the other one!
            // since we know that they are orthogonal, we can just use the closest point on line
            Vector2D closestPoint = Physics.closestPointOnLine(targetLane.getStartPos(), targetLane.getDirectionVector(), endPos);
            // Move straight until we have reached the closestPoint
            while(Vector2D.distance(closestPoint, p) > 0.0001*Vanet.SCALE) {
                p.add(dirStep);
                waypoints.add(new Vector2D(p));
            }

            dirStep = new Vector2D(targetLane.getDirectionVector());
            dirStep.scale(Vanet.SCALE);

            // Move to the start of the lane
            p.add(dirStep);
            while(Vector2D.distance(targetLane.getStartPos(), p) > 0.001*Vanet.SCALE) {
                waypoints.add(new Vector2D(p));
                p.add(dirStep);
            }
        }

        // add more waypoints into the lane
        for(int i = 0; i < STEPS_INTO_LANE ; ++i) {
            waypoints.add(new Vector2D(p));
            p.add(dirStep);
        }

        return waypoints;
    }


    public boolean hasNoEnd() {
        return endIntersection == null;
    }

    public boolean hasNoStart() {
        return startIntersection == null;
    }

    public boolean isInitialStart() {
        return endIntersection != null && startIntersection == null;
    }

    public boolean isFinalEndLane() {
        return endIntersection == null && startIntersection != null;
    }
}
