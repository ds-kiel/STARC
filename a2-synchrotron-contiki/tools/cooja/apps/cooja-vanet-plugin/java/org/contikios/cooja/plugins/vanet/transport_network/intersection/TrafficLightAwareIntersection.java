package org.contikios.cooja.plugins.vanet.transport_network.intersection;

import java.util.Map;

public interface TrafficLightAwareIntersection {

    public static final int PHASE_RED = 0;
    public static final int PHASE_YELLOW = 1;
    public static final int PHASE_GREEN = 2;
    public Map<Lane, Integer> getTrafficLightStates(long ms);
}
