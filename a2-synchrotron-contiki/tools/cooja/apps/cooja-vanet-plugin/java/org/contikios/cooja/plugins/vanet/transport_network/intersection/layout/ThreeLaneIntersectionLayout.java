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

    private boolean restrictedDirections;


    public ThreeLaneIntersectionLayout(boolean restrictedDirections) {
        this.restrictedDirections = restrictedDirections;
    }

    private void initLanes() {

        final int INDEX_LEFT = 0;
        final int INDEX_STRAIGHT = 1;
        final int INDEX_RIGHT= 2;

        // End lanes
        List<Integer> allLeft = Arrays.asList(
                this.addEndLane(new Vector2D(-0.5, 2.5), LANE_DIR_LEFT),
                this.addEndLane(new Vector2D(-0.5, 1.5), LANE_DIR_LEFT),
                this.addEndLane(new Vector2D(-0.5, 0.5), LANE_DIR_LEFT)
        );

        List<Integer> allUp = Arrays.asList(
                this.addEndLane(new Vector2D(3.5, -0.5), LANE_DIR_UP),
                this.addEndLane(new Vector2D(4.5, -0.5), LANE_DIR_UP),
                this.addEndLane(new Vector2D(5.5, -0.5), LANE_DIR_UP)
        );

        List<Integer> allDown = Arrays.asList(
                this.addEndLane(new Vector2D(2.5, 6.5), LANE_DIR_DOWN),
                this.addEndLane(new Vector2D(1.5, 6.5), LANE_DIR_DOWN),
                this.addEndLane(new Vector2D(0.5, 6.5), LANE_DIR_DOWN)
        );


        List<Integer> allRight = Arrays.asList(
                this.addEndLane(new Vector2D(6.5, 3.5), LANE_DIR_RIGHT),
                this.addEndLane(new Vector2D(6.5, 4.5), LANE_DIR_RIGHT),
                this.addEndLane(new Vector2D(6.5, 5.5), LANE_DIR_RIGHT)
        );

        // Starting lanes
        // TODO: a clearer setup would be nice :)
        if (restrictedDirections) {
            this.addStartLane( new Vector2D(2.5, -0.5), LANE_DIR_DOWN, allRight.get(INDEX_LEFT), null);
            this.addStartLane( new Vector2D(1.5, -0.5), LANE_DIR_DOWN, allDown.get(INDEX_STRAIGHT), null);
            this.addStartLane( new Vector2D(0.5, -0.5), LANE_DIR_DOWN, allLeft.get(INDEX_RIGHT), null);

            this.addStartLane( new Vector2D(6.5, 2.5), LANE_DIR_LEFT, allDown.get(INDEX_LEFT), null);
            this.addStartLane( new Vector2D(6.5, 1.5), LANE_DIR_LEFT, allLeft.get(INDEX_STRAIGHT), null);
            this.addStartLane( new Vector2D(6.5, 0.5), LANE_DIR_LEFT, allUp.get(INDEX_RIGHT), null);

            this.addStartLane( new Vector2D(-0.5, 3.5), LANE_DIR_RIGHT, allUp.get(INDEX_LEFT), null);
            this.addStartLane( new Vector2D(-0.5, 4.5), LANE_DIR_RIGHT, allRight.get(INDEX_STRAIGHT), null);
            this.addStartLane( new Vector2D(-0.5, 5.5), LANE_DIR_RIGHT, allDown.get(INDEX_RIGHT), null);

            this.addStartLane( new Vector2D(3.5, 6.5), LANE_DIR_UP, allLeft.get(INDEX_LEFT), null);
            this.addStartLane( new Vector2D(4.5, 6.5), LANE_DIR_UP, allUp.get(INDEX_STRAIGHT), null);
            this.addStartLane( new Vector2D(5.5, 6.5), LANE_DIR_UP, allRight.get(INDEX_RIGHT), null);

        } else {
            this.addStartLane( new Vector2D(2.5, -0.5), LANE_DIR_DOWN, allDown.get(INDEX_LEFT), allRight);
            this.addStartLane( new Vector2D(1.5, -0.5), LANE_DIR_DOWN, allDown.get(INDEX_STRAIGHT), null);
            this.addStartLane( new Vector2D(0.5, -0.5), LANE_DIR_DOWN, allDown.get(INDEX_RIGHT), allLeft);

            this.addStartLane( new Vector2D(6.5, 2.5), LANE_DIR_LEFT, allLeft.get(INDEX_LEFT), allDown);
            this.addStartLane( new Vector2D(6.5, 1.5), LANE_DIR_LEFT, allLeft.get(INDEX_STRAIGHT), null);
            this.addStartLane( new Vector2D(6.5, 0.5), LANE_DIR_LEFT, allLeft.get(INDEX_RIGHT), allUp);

            this.addStartLane( new Vector2D(-0.5, 3.5), LANE_DIR_RIGHT, allRight.get(INDEX_LEFT), allUp);
            this.addStartLane( new Vector2D(-0.5, 4.5), LANE_DIR_RIGHT, allRight.get(INDEX_STRAIGHT), null);
            this.addStartLane( new Vector2D(-0.5, 5.5), LANE_DIR_RIGHT, allRight.get(INDEX_RIGHT), allDown);

            this.addStartLane( new Vector2D(3.5, 6.5), LANE_DIR_UP, allUp.get(INDEX_LEFT), allLeft);
            this.addStartLane( new Vector2D(4.5, 6.5), LANE_DIR_UP, allUp.get(INDEX_STRAIGHT), null);
            this.addStartLane( new Vector2D(5.5, 6.5), LANE_DIR_UP, allUp.get(INDEX_RIGHT), allRight);
        }
    }


    public void init(Intersection intersection, Vector2D offset) {

        initLanes();

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
