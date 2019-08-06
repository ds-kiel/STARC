package org.contikios.cooja.plugins.vanet.transport_network.intersection;

import org.contikios.cooja.plugins.vanet.world.physics.Vector2D;

import java.util.HashMap;
import java.util.Map;

public class TrafficLightIntersection extends Intersection implements TrafficLightAwareIntersection {

    public TrafficLightIntersection(int id, Vector2D offset) {
        super(id, offset);
    }

    protected int computeState(int dir, long ms) {

        int phaseOffset = 0;
        int numPhases = 0;

        numPhases = 4;
        switch (dir) {
            case Lane.DIR_UP:
                phaseOffset++;
            case Lane.DIR_DOWN:
                phaseOffset++;
            case Lane.DIR_LEFT:
                phaseOffset++;
            case Lane.DIR_RIGHT:
                break;
        }

        long greenPhaseDurationMS = 9*1000;
        long yellowPhaseDurationMS = 3*1000;
        long redPhaseDurationMS = 3*1000;

        long overall = greenPhaseDurationMS+yellowPhaseDurationMS+redPhaseDurationMS;
        long phase = ms / overall;

        if (phase % numPhases == phaseOffset) {
            long inPhase = ms-phase*overall;
            if (inPhase < greenPhaseDurationMS) {
                return PHASE_GREEN;
            } else if (inPhase < greenPhaseDurationMS+yellowPhaseDurationMS) {
                return PHASE_YELLOW;
            } else {
                return PHASE_RED;
            }
        } else {
            return PHASE_RED;
        }
    }

    @Override
    public Map<Lane, Integer> getTrafficLightStates(long ms) {
        HashMap<Lane, Integer> states = new HashMap<>();

        getStartLanes().forEach(l -> {
            states.put(l, computeState(l.getDirection(), ms));
        });

        return states;
    }
}
