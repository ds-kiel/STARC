package org.contikios.cooja.plugins.vanet.transport_network.intersection.layout;

import org.contikios.cooja.plugins.Vanet;
import org.contikios.cooja.plugins.vanet.transport_network.intersection.Intersection;
import org.contikios.cooja.plugins.vanet.transport_network.intersection.Lane;
import org.contikios.cooja.plugins.vanet.world.physics.Vector2D;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class ThreeLaneIntersectionLayout extends IntersectionLayout {

    public ThreeLaneIntersectionLayout() {

        Vector2D up = LANE_DIR_UP;
        Vector2D right = LANE_DIR_RIGHT;
        Vector2D down = LANE_DIR_DOWN;
        Vector2D left = LANE_DIR_LEFT;


        final int INDEX_LEFT = 0;
        final int INDEX_STRAIGHT = 1;
        final int INDEX_RIGHT= 2;

        // End lanes
        List<Integer> allLeft = Arrays.asList(
                this.addEndLane(new Vector2D(-0.5, 2.5), left),
                this.addEndLane(new Vector2D(-0.5, 1.5), left),
                this.addEndLane(new Vector2D(-0.5, 0.5), left)
        );

        List<Integer> allUp = Arrays.asList(
                this.addEndLane(new Vector2D(3.5, -0.5), up),
                this.addEndLane(new Vector2D(4.5, -0.5), up),
                this.addEndLane(new Vector2D(5.5, -0.5), up)
        );

        List<Integer> allDown = Arrays.asList(
                this.addEndLane(new Vector2D(2.5, 6.5), down),
                this.addEndLane(new Vector2D(1.5, 6.5), down),
                this.addEndLane(new Vector2D(0.5, 6.5), down)
        );


        List<Integer> allRight = Arrays.asList(
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


    public void init(Intersection intersection, Vector2D offset) {

        getStartLanes().forEach(
                l -> {
                    l.setEndIntersection(intersection);
                    l.getStartPos().add(offset);
                    l.getEndPos().add(offset);
                }
        );

        getEndLanes().forEach(
                l -> {
                    l.setStartIntersection(intersection);
                    l.getStartPos().add(offset);
                    l.getEndPos().add(offset);
                }
        );
    }

    private Integer addStartLane(Vector2D relativeEndPos, Vector2D direction, Integer mainDirectionID, List<Integer> otherDirectionsIDs) {
        relativeEndPos.scale(Vanet.SCALE);


        Vector2D startPos = new Vector2D(direction);
        startPos.scale(-Vanet.SCALE*LANE_LENGTH);
        startPos.add(relativeEndPos);
        int id = generateLaneID();

        Lane l = new Lane();
        l.setEndId(id);
        l.setStartPos(startPos);
        l.setEndPos(relativeEndPos);
        this.startLanes.put(id, l);

        ArrayList<Integer> dirs = new ArrayList<>();
        // we update the possible directions here
        if (mainDirectionID != null) {
            dirs.add(mainDirectionID);
        }
        if (otherDirectionsIDs != null) {
            dirs.addAll(otherDirectionsIDs);
        }
        possibleDirs.put(id, dirs);
        return id;
    }

    private Integer addEndLane(Vector2D relativeStartPos, Vector2D direction) {
        relativeStartPos.scale(Vanet.SCALE);

        Vector2D endPos = new Vector2D(direction);
        endPos.scale(Vanet.SCALE*LANE_LENGTH);
        endPos.add(relativeStartPos);

        int id = generateLaneID();

        Lane l = new Lane();
        l.setStartId(id);
        l.setStartPos(relativeStartPos);
        l.setEndPos(endPos);
        this.endLanes.put(id, l);
        return id;
    }

    public void replaceLane(int originalID, Lane replacement) {
        startLanes.replace(originalID, replacement);
        endLanes.replace(originalID, replacement);
    }

    public Collection<Lane> getPossibleLanes(int arrivalLaneID) {
        return possibleDirs.get(arrivalLaneID)
                .stream().map(this.endLanes::get)
                .collect(Collectors.toList());
    }
}
