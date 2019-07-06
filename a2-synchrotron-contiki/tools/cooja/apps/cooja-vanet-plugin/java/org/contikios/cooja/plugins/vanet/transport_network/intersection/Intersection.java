package org.contikios.cooja.plugins.vanet.transport_network.intersection;

import org.contikios.cooja.plugins.Vanet;
import org.contikios.cooja.plugins.vanet.world.physics.Vector2D;

import java.util.*;
import java.util.stream.Collectors;

public class Intersection {
    private Vector2D offset;
    private TiledMapHandler mapHandler;
    HashMap<Integer, Lane> lanes = new HashMap<>();
    HashMap<Integer, Collection<Integer>> possibleDirs = new HashMap();

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


        final int INDEX_LEFT = 0;
        final int INDEX_STRAIGHT = 1;
        final int INDEX_RIGHT= 2;

        // End lanes
        List<Lane> allLeft = Arrays.asList(
            this.addEndLane(new Vector2D(-0.5, 2.5), left),
            this.addEndLane(new Vector2D(-0.5, 1.5), left),
            this.addEndLane(new Vector2D(-0.5, 0.5), left)
        );

        List<Lane> allUp = Arrays.asList(
            this.addEndLane(new Vector2D(3.5, -0.5), up),
            this.addEndLane(new Vector2D(4.5, -0.5), up),
            this.addEndLane(new Vector2D(5.5, -0.5), up)
        );

        List<Lane> allDown = Arrays.asList(
            this.addEndLane(new Vector2D(2.5, 6.5), down),
            this.addEndLane(new Vector2D(1.5, 6.5), down),
            this.addEndLane(new Vector2D(0.5, 6.5), down)
        );


        List<Lane> allRight = Arrays.asList(
            this.addEndLane(new Vector2D(6.5, 3.5), right),
            this.addEndLane(new Vector2D(6.5, 4.5), right),
            this.addEndLane(new Vector2D(6.5, 5.5), right)
        );

        // Starting lanes
        this.addStartLane( new Vector2D(2.5, -0.5), down, allDown.get(INDEX_LEFT), allRight);
        this.addStartLane( new Vector2D(1.5, -0.5), down, allDown.get(INDEX_STRAIGHT), null);
        this.addStartLane( new Vector2D(0.5, -0.5), down, allDown.get(INDEX_RIGHT), allLeft);

        this.addStartLane( new Vector2D(6.5, 2.5), left, allLeft.get(INDEX_LEFT), allDown);
        this.addStartLane( new Vector2D(6.5, 1.5), left, allLeft.get(INDEX_STRAIGHT), null);
        this.addStartLane( new Vector2D(6.5, 0.5), left, allLeft.get(INDEX_RIGHT), allUp);

        this.addStartLane( new Vector2D(-0.5, 3.5), right, allRight.get(INDEX_LEFT), allUp);
        this.addStartLane( new Vector2D(-0.5, 4.5), right, allRight.get(INDEX_STRAIGHT), null);
        this.addStartLane( new Vector2D(-0.5, 5.5), right, allRight.get(INDEX_RIGHT), allDown);

        this.addStartLane( new Vector2D(3.5, 6.5), up, allUp.get(INDEX_LEFT), allLeft);
        this.addStartLane( new Vector2D(4.5, 6.5), up, allUp.get(INDEX_STRAIGHT), null);
        this.addStartLane( new Vector2D(5.5, 6.5), up, allUp.get(INDEX_RIGHT), allRight);
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
    private Lane addStartLane(Vector2D relativeEndPos, Vector2D direction, Lane mainDirection, List<Lane> otherDirections) {


        // TODO: The scaling should happen before?
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

        ArrayList<Integer> dirs = new ArrayList<>();
        // we update the possible directions here
        if (mainDirection != null) {
            dirs.add(mainDirection.getId(this));
        }
        if (otherDirections != null) {
            otherDirections.forEach(od -> dirs.add(od.getId(this)));
        }
        possibleDirs.put(id, dirs);
        return l;
    }

    private Lane addEndLane(Vector2D relativeStartPos, Vector2D direction) {
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
        return l;
    }

    public void replaceLane(Lane original, Lane replacement) {
        int laneId = original.getId(this);

        if (laneId > 0) {
            this.lanes.put(laneId, replacement);
        } else {
            System.out.println("Could not find lane!");
        }
    }

    public Collection<Lane> getPossibleLanes(Lane arrivalLane) {
        int id = arrivalLane.getId(this);
        return possibleDirs.get(id)
                .stream().map(
                    i -> this.lanes.get(i)
                )
                .collect(Collectors.toList());
    }

    public TiledMapHandler getMapHandler() {
        return this.mapHandler;
    }
}
