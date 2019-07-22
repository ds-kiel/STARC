package org.contikios.cooja.plugins.vanet.transport_network.intersection.layout;

import org.contikios.cooja.plugins.vanet.transport_network.intersection.Intersection;
import org.contikios.cooja.plugins.vanet.transport_network.intersection.Lane;
import org.contikios.cooja.plugins.vanet.world.physics.Vector2D;

import java.util.*;
import java.util.stream.Collectors;

public abstract class IntersectionLayout {

    public static final int LANE_LENGTH = 3;
    public static final Vector2D LANE_DIR_UP = new Vector2D(0, -1);
    public static final Vector2D LANE_DIR_RIGHT = new Vector2D(1, 0);
    public static final Vector2D LANE_DIR_DOWN = new Vector2D(0, 1);
    public static final Vector2D LANE_DIR_LEFT = new Vector2D(-1, 0);

    protected HashMap<Integer, Lane> startLanes = new HashMap<>();
    protected HashMap<Integer, Lane> endLanes = new HashMap<>();
    protected HashMap<Integer, Collection<Integer>> possibleDirs = new HashMap();
    protected int nextLaneID = 1;

    public IntersectionLayout() {}

    protected int generateLaneID() {
        return nextLaneID++;
    }

    public Collection<Lane> getStartLanes() {
        return this.startLanes.values();
    }
    public Collection<Lane> getEndLanes() {
        return this.endLanes.values();
    }

    public Collection<Lane> getStartLaneWithDirection(Vector2D dir) {
        return getStartLanes().stream().filter(l -> l.getDirectionVector().equals(dir)).collect(Collectors.toList());
    }
    public Collection<Lane> getEndLaneWithDirection(Vector2D dir) {
        return getEndLanes().stream().filter(l -> l.getDirectionVector().equals(dir)).collect(Collectors.toList());
    }

    public abstract void replaceLane(int originalId, Lane replacement);

    public abstract void init(Intersection intersection, Vector2D offset);

    public abstract Collection<Lane> getPossibleLanes(int arrivalLaneID);
}
