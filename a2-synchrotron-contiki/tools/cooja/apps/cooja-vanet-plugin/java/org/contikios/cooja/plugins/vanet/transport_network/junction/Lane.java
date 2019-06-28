package org.contikios.cooja.plugins.vanet.transport_network.junction;

import org.contikios.cooja.plugins.vanet.world.physics.Vector2D;

import java.util.ArrayList;

public class Lane {

    protected Junction start;
    protected Vector2D startPos;

    protected Junction end;
    protected Vector2D endPos;

    protected Vector2D direction;

    private int possibleDir;

    public static final int NONE = 0;
    public static final int STRAIGHT = 1;
    public static final int TURN_RIGHT = 2;
    public static final int TURN_LEFT = 3;

    public Lane(Junction start, Vector2D startPos, Junction end, Vector2D endPos, Vector2D direction, int possibleDir) {
        this.start = start;
        this.startPos = startPos;
        this.end = end;
        this.endPos = endPos;
        this.direction = new Vector2D(direction);

        this.possibleDir = possibleDir;

        this.direction.normalize(); // TODO: compute direction by the start and endpos
    }

    public int getPossibleDir() {
        return possibleDir;
    }

    public Junction getStart() {
        return start;
    }

    public Vector2D getStartPos() {
        return startPos;
    }

    public Junction getEnd() {
        return end;
    }

    public Vector2D getEndPos() {
        return endPos;
    }

    public Vector2D getDirection() {
        return direction;
    }

    public boolean hasStart() {
        return this.start != null && this.startPos != null;
    }

    public boolean hasEnd() {
        return this.end != null && this.endPos != null;
    }

    public ArrayList<Vector2D> getWayPoints(TiledMapHandler mapHandler) {

        Vector2D dirStep = new Vector2D(direction);
        dirStep.scale(mapHandler.getTileScaling());

        ArrayList<Vector2D> waypoints = new ArrayList<>();

        Vector2D p = new Vector2D(this.endPos);
        // we will distinguish three cases
        if (possibleDir == STRAIGHT) {
            {
                // we will try to move straight
                // Move TILES_WIDTH+1 tiles straight
                for(int i = 0; i <= mapHandler.getWidth(); ++i) {
                    p.add(dirStep);
                    waypoints.add(new Vector2D(p));
                }
            }
        } else if (possibleDir == TURN_LEFT) {
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

        } else if (possibleDir == TURN_RIGHT) {
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
}
