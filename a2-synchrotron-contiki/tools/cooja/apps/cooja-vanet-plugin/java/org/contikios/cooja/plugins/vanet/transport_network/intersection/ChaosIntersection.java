package org.contikios.cooja.plugins.vanet.transport_network.intersection;


import org.contikios.cooja.plugins.vanet.world.physics.Vector2D;

public class ChaosIntersection extends Intersection {

    long lastInitiatorRound = 0;

    public ChaosIntersection(int id, Vector2D offset) {
        super(id, offset);
    }

    public long getLastInitiatorRound() {
        return lastInitiatorRound;
    }

    public void setLastInitiatorRound(long lastInitiatorRound) {
        this.lastInitiatorRound = lastInitiatorRound;
    }
}
