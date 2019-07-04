package org.contikios.cooja.plugins.vanet.transport_network.intersection;

import org.contikios.cooja.plugins.Vanet;
import org.contikios.cooja.plugins.vanet.world.physics.Vector2D;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

public class Intersection {
    private Vector2D offset;
    private TiledMapHandler mapHandler;
    HashMap<Integer, Lane> lanes = new HashMap<>();

    public static final int TURN_LEFT = -1;
    public static final int STRAIGHT = 0;
    public static final int TURN_RIGHT = 1;

    public static final int LANE_LENGTH = 3;

    protected int id;

    public Intersection(int id, Vector2D offset) {
        this.id = id;
        this.offset = offset;
        this.mapHandler = new TiledMapHandler(6,6, this.offset);

        Vector2D up = new Vector2D(0, -1);
        Vector2D right = new Vector2D(1, 0);
        Vector2D down = new Vector2D(0, 1);
        Vector2D left = new Vector2D(-1, 0);

        // End lanes
        this.addEndLane(new Vector2D(-0.5, 0.5), left);
        this.addEndLane(new Vector2D(-0.5, 1.5), left);
        this.addEndLane(new Vector2D(-0.5, 2.5), left);

        this.addEndLane(new Vector2D(3.5, -0.5), up);
        this.addEndLane(new Vector2D(4.5, -0.5), up);
        this.addEndLane(new Vector2D(5.5, -0.5), up);

        this.addEndLane(new Vector2D(0.5, 6.5), down);
        this.addEndLane(new Vector2D(1.5, 6.5), down);
        this.addEndLane(new Vector2D(2.5, 6.5), down);

        this.addEndLane(new Vector2D(6.5, 3.5), right);
        this.addEndLane(new Vector2D(6.5, 4.5), right);
        this.addEndLane(new Vector2D(6.5, 5.5), right);


        // Starting lanes
        this.addStartLane( new Vector2D(0.5, -0.5), down, TURN_RIGHT);
        this.addStartLane( new Vector2D(1.5, -0.5), down, STRAIGHT);
        this.addStartLane( new Vector2D(2.5, -0.5), down, TURN_LEFT);

        this.addStartLane( new Vector2D(6.5, 0.5), left, TURN_RIGHT);
        this.addStartLane( new Vector2D(6.5, 1.5), left, STRAIGHT);
        this.addStartLane( new Vector2D(6.5, 2.5), left, TURN_LEFT);

        this.addStartLane( new Vector2D(-0.5, 3.5), right, TURN_LEFT);
        this.addStartLane( new Vector2D(-0.5, 4.5), right, STRAIGHT);
        this.addStartLane( new Vector2D(-0.5, 5.5), right, TURN_RIGHT);

        this.addStartLane( new Vector2D(3.5, 6.5), up, TURN_LEFT);
        this.addStartLane( new Vector2D(4.5, 6.5), up, STRAIGHT);
        this.addStartLane( new Vector2D(5.5, 6.5), up, TURN_RIGHT);
    }

    public int getId() {
        return id;
    }

    public Vector2D getOffset() {
        return offset;
    }

    public Vector2D getCenter() {
        Vector2D c = new Vector2D(3, 3);
        c.scale(Vanet.SCALE);
        c.add(offset);
        return c;
    }

    public Collection<Lane> getLanes() {
        return lanes.values();
    }
/*
    TODO: The scaling should happen before!
 */
    private void addStartLane(Vector2D relativeEndPos, Vector2D direction, int possDir) {

        relativeEndPos.scale(Vanet.SCALE);
        relativeEndPos.add(this.offset);

        Vector2D startPos = new Vector2D(direction);
        startPos.scale(-Vanet.SCALE*LANE_LENGTH);
        startPos.add(relativeEndPos);
        int id = this.lanes.size()+1;

        Lane l = new Lane();
        l.setEndIntersection(this);
        l.setEndId(id);
        l.setStartPos(startPos);
        l.setEndPos(relativeEndPos);
        this.lanes.put(id, l);
    }

    private void addEndLane(Vector2D relativeStartPos, Vector2D direction) {
        relativeStartPos.scale(Vanet.SCALE);
        relativeStartPos.add(this.offset);

        Vector2D endPos = new Vector2D(direction);
        endPos.scale(Vanet.SCALE*LANE_LENGTH);
        endPos.add(relativeStartPos);

        int id = this.lanes.size()+1;
        Lane l = new Lane();
        l.setStartIntersection(this);
        l.setStartId(id);
        l.setStartPos(relativeStartPos);
        l.setEndPos(endPos);
        this.lanes.put(id, l);
    }

    public void replaceLane(Lane original, Lane replacement) {
        int laneId = original.getIntersectionId(this);

        if (laneId > 0) {
            this.lanes.put(laneId, replacement);
        }
    }

    public Collection<Lane> getPossibleWays(Lane arrivalLane) {
        // TODO: Filter the possible ways for that specific arrival lane
        return new ArrayList<>();
    }

    public TiledMapHandler getMapHandler() {
        return this.mapHandler;
    }
}
