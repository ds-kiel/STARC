package org.contikios.cooja.plugins.vanet.transport_network.intersection;

import org.contikios.cooja.plugins.vanet.world.physics.Vector2D;

import java.util.HashMap;
import java.util.Map;

public class TrafficLightIntersection extends Intersection {

    public static final int PHASE_RED = 0;
    public static final int PHASE_YELLOW = 1;
    public static final int PHASE_GREEN = 2;

    public TrafficLightIntersection(int id, Vector2D offset) {
        super(id, offset);
    }


    protected int computeState(int dir, int ms) {

        int offet = 0;

        switch (dir) {
            case Lane.DIR_UP:
                offet++;
            case Lane.DIR_DOWN:
                offet++;
            case Lane.DIR_LEFT:
                offet++;
            case Lane.DIR_RIGHT:
                break;
        }
        return PHASE_RED;
    }

    protected Map<Lane, Integer> getStates(int ms) {
        HashMap<Lane, Integer> states = new HashMap<>();

        getStartLanes().forEach(l -> {
            states.put(l, computeState(l.getDirection(), ms));
        });

        return states;
    }


    public int getPhase(Lane lane, int ms) {
        Map<Lane, Integer> states = getStates(ms);
        return states.getOrDefault(lane, PHASE_RED);
    }
}
