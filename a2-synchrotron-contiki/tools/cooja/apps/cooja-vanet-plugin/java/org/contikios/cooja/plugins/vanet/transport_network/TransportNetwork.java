package org.contikios.cooja.plugins.vanet.transport_network;

import org.contikios.cooja.plugins.Vanet;
import org.contikios.cooja.plugins.vanet.transport_network.intersection.Intersection;
import org.contikios.cooja.plugins.vanet.transport_network.intersection.Lane;
import org.contikios.cooja.plugins.vanet.transport_network.intersection.TrafficLightIntersection;
import org.contikios.cooja.plugins.vanet.transport_network.intersection.layout.IntersectionLayout;
import org.contikios.cooja.plugins.vanet.world.World;
import org.contikios.cooja.plugins.vanet.world.physics.Vector2D;
import org.contikios.cooja.util.ArrayUtils;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TransportNetwork {

    private int width;
    private int height;

    private Intersection[] intersections;
    private int numStartLanes;

    public static final int INTERSECTION_TYPE_DECENTRALIZED = 0;
    public static final int INTERSECTION_TYPE_TRAFFIC_LIGHTS = 1;

    public TransportNetwork(int width, int height, int intersectionType) {
        this.width = width;
        this.height = height;

        this.intersections = new Intersection[width*height];

        // we initialize left to right and top to bottom
        // we can thus connect the intersections to the one left and one top

        double offsetX = 0;
        double offsetY = 0;

        double size = Vanet.SCALE*(2* IntersectionLayout.LANE_LENGTH+6);
        for(int y = 0; y < height;++y) {
            for(int x = 0; x < width; ++x) {
                int id = y*width+x;
                Intersection newIntersection = null;
                if (intersectionType == INTERSECTION_TYPE_TRAFFIC_LIGHTS) {
                    newIntersection = new TrafficLightIntersection(id, new Vector2D(offsetX, offsetY));
                } else {
                    newIntersection = new Intersection(id, new Vector2D(offsetX, offsetY));
                }
                this.intersections[y*width+x] = newIntersection;
                this.connectLeft(x, y);
                this.connectTop(x, y);
                offsetX += size;
            }
            offsetX = 0;
            offsetY += size;
        }

        this.numStartLanes = (int) Arrays.stream(intersections).flatMap(i -> i.getStartLanes().stream()).filter(Lane::isInitialStart).count();
    }

    public int getNumStartLanes() {
        return numStartLanes;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    private void connectLeft(int x, int y) {
        if (x <= 0) {
            return;
        }
        Intersection intersection = getIntersection(x,y);
        Intersection leftIntersection = getIntersection(x-1, y);
        connectLanes(intersection, leftIntersection);
    }

    public Collection<Intersection> getIntersections() {
        return Arrays.asList(intersections);
    }

    private void connectTop(int x, int y) {
        if (y <= 0) {
            return;
        }
        Intersection intersection = getIntersection(x,y);
        Intersection topIntersection = getIntersection(x, y-1);
        connectLanes(intersection, topIntersection);
    }

    private void connectLanes(Intersection intersectionA, Intersection intersectionB) {
        // we connect the lanes like the following:
        // if a start point of one lane matches the end point of another lane, we connect them
        // we then set the start point
        Stream<Map.Entry<Lane, Lane>> possiblePairs =
                Stream.concat(
                        intersectionA.getEndLanes().stream().filter(Lane::hasNoEnd).flatMap(l1 -> intersectionB.getStartLanes().stream().filter(Lane::hasNoStart).map(l2 -> new AbstractMap.SimpleImmutableEntry<>(l1,l2))),
                        intersectionB.getEndLanes().stream().filter(Lane::hasNoEnd).flatMap(l1 -> intersectionA.getStartLanes().stream().filter(Lane::hasNoStart).map(l2 -> new AbstractMap.SimpleImmutableEntry<>(l1,l2)))
                );

        // filter the matching positions
        possiblePairs = possiblePairs.filter(p -> {
            Vector2D joinPos = new Vector2D(p.getKey().getDirectionVector());
            joinPos.scale(-Vanet.SCALE);
            joinPos.add(p.getKey().getEndPos());
            return Vector2D.distance(joinPos, p.getValue().getStartPos()) < 0.01*Vanet.SCALE;
        });

        // these are the matching pairs
        possiblePairs.forEach(p -> {
            Lane l1 = p.getKey();
            Lane l2 = p.getValue();

            // we keep the lane with the start (l1)
            // so we need to set the correct endpos, which is the endpos of l2
            // we also need to replace the lane with the correct id in the other intersection

            l1.setEndIntersection(l2.getEndIntersection());
            l1.setEndPos(l2.getEndPos());
            l1.setEndId(l2.getEndId());

            l1.getEndIntersection().replaceLane(l2, l1);
        });
    }

    public Intersection getIntersection(int x, int y) {
        return this.intersections[y*width+x];
    }

    public Intersection getIntersection(int id) {
        return this.intersections[id];
    }

    public Lane getRandomStartLaneWithTurn(int turn) {
        Collection<Lane> startLanes = Arrays.stream(intersections).flatMap(i -> i.getStartLaneWithTurn(turn).stream()).filter(Lane::isInitialStart).collect(Collectors.toList());
        return startLanes.stream().skip((int) (startLanes.size() * World.RAND.nextFloat())).findAny().get();
    }
}
