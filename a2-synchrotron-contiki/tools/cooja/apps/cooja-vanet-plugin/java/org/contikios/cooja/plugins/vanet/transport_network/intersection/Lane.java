package org.contikios.cooja.plugins.vanet.transport_network.intersection;

import org.contikios.cooja.plugins.vanet.world.physics.Vector2D;

import java.util.ArrayList;

public class Lane {

    protected Intersection startIntersection;
    protected Vector2D startPos;
    protected int startId;

    protected Intersection endIntersection;
    protected Vector2D endPos;
    protected int endId;


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
        } else if (d.getX() < 0.5) {
            return DIR_LEFT;
        } else if (d.getY() > 0.5) {
            return DIR_DOWN;
        } else  {
            return DIR_UP;
        }
    }

    public int getTurnType(int otherDir) {
        int dir = getDirection();

        if (dir == otherDir) {
            return Intersection.STRAIGHT; // same direction
        }

        switch (dir) {
            case DIR_UP:
                return otherDir == DIR_LEFT ? Intersection.TURN_LEFT : Intersection.TURN_RIGHT;
            case DIR_DOWN:
                return otherDir == DIR_RIGHT ? Intersection.TURN_LEFT : Intersection.TURN_RIGHT;
            case DIR_LEFT:
                return otherDir == DIR_DOWN ? Intersection.TURN_LEFT : Intersection.TURN_RIGHT;
            case DIR_RIGHT:
                return otherDir == DIR_UP ? Intersection.TURN_LEFT : Intersection.TURN_RIGHT;
        }

        return Intersection.STRAIGHT;
    }

    public int getIntersectionId(Intersection intersection) {
        if (intersection == endIntersection){
            return endId;
        } else if (intersection == startIntersection) {
            return startId;
        } else {
            return 0;
        }
    }

    public ArrayList<Vector2D> getWayPoints(TiledMapHandler mapHandler, Lane targetLane) {

        Vector2D dirStep = new Vector2D(getDirectionVector());
        dirStep.scale(mapHandler.getTileScaling());

        ArrayList<Vector2D> waypoints = new ArrayList<>();

        Vector2D p = new Vector2D(this.endPos);

        int turnType = getTurnType(targetLane.getDirection());

        // we will distinguish three cases
        if (turnType == Intersection.STRAIGHT) {
            {
                // we will try to move straight
                // Move TILES_WIDTH+1 tiles straight
                for(int i = 0; i <= mapHandler.getWidth(); ++i) {
                    p.add(dirStep);
                    waypoints.add(new Vector2D(p));
                }
            }
        } else if (turnType == Intersection.TURN_LEFT) {
            // we will try to go left

            // Move four tiles straight
            for(int i = 0; i < 4; ++i) {
                p.add(dirStep);
                waypoints.add(new Vector2D(p));
            }

            // Move four tiles to the left
            dirStep.rotate(-Math.PI/2.0);

            for(int i = 0; i < 4; ++i) {

                p.add(dirStep);
                waypoints.add(new Vector2D(p));
            }

        } else if (turnType == Intersection.TURN_RIGHT) {
            // we will try to go right
            // so we move one into our original position
            p.add(dirStep);
            waypoints.add(new Vector2D(p));
            // and just one to the right, which is our target position

            dirStep.rotate(Math.PI/2.0);

            p.add(dirStep);
            waypoints.add(new Vector2D(p));
        }

        for(int i = 0; i < 2; ++i) {
            p.add(dirStep);
            waypoints.add(new Vector2D(p));
        }

        return waypoints;
    }


    public boolean hasNoEnd() {
        return endIntersection == null;
    }

    public boolean hasNoStart() {
        return startIntersection == null;
    }

    public boolean isStartLane() {
        return endIntersection != null && startIntersection == null;
    }
}
