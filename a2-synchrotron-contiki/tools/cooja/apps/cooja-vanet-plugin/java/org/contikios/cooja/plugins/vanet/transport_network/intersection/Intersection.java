package org.contikios.cooja.plugins.vanet.transport_network.intersection;

import org.contikios.cooja.plugins.Vanet;
import org.contikios.cooja.plugins.vanet.transport_network.intersection.layout.IntersectionLayout;
import org.contikios.cooja.plugins.vanet.transport_network.intersection.layout.ThreeLaneIntersectionLayout;
import org.contikios.cooja.plugins.vanet.world.physics.Vector2D;

import java.util.*;

public class Intersection {
    private Vector2D offset;
    protected int id;
    IntersectionLayout layout;
    TiledMapHandler mapHandler;

    public Intersection(int id, Vector2D offset) {
        this.id = id;
        this.offset = offset;
        this.mapHandler = new TiledMapHandler(6,6, this.offset);
        this.layout = new ThreeLaneIntersectionLayout();
        this.layout.init(this, offset);
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

    public Collection<Lane> getStartLanes() {
        return layout.getStartLanes();
    }
    public Collection<Lane> getEndLanes() {
        return layout.getEndLanes();
    }

    public void replaceLane(Lane original, Lane replacement) {
        int laneId = original.getId(this);

        if (laneId > 0) {
            this.layout.replaceLane(laneId, replacement);
        } else {
            System.out.println("Could not find lane!");
        }
    }

    public Collection<Lane> getPossibleLanes(Lane arrivalLane) {
        int id = arrivalLane.getId(this);
        return layout.getPossibleLanes(id);
    }

    public TiledMapHandler getMapHandler() {
        return mapHandler;
    }
}
