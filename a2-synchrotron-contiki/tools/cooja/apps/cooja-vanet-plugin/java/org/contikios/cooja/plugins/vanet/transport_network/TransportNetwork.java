package org.contikios.cooja.plugins.vanet.transport_network;

import org.contikios.cooja.plugins.vanet.transport_network.junction.Junction;
import org.contikios.cooja.plugins.vanet.transport_network.junction.Lane;

import java.util.Collection;
import java.util.stream.Collectors;

public class TransportNetwork {

    private int width;
    private int height;

    private Junction junction;

    public TransportNetwork(int width, int height) {
        this.width = width;
        this.height = height;

        this.junction = new Junction();
    }

    public Junction getJunction() {
        return junction;
    }

    public Lane getRandomStartLane() {
        Collection<Lane> startLanes = junction.getLanes().stream().filter(Lane::hasEnd).collect(Collectors.toList());
        return startLanes.stream()./*skip((int) (startLanes.size() * Math.random())).*/findAny().get();
    }
}
