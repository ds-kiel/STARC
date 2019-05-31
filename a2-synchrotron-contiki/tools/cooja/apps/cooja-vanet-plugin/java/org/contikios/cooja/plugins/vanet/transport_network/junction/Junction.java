package org.contikios.cooja.plugins.vanet.transport_network.junction;

import org.contikios.cooja.plugins.vanet.world.physics.Vector2D;

import java.util.ArrayList;

public class Junction {
    private Vector2D offset;
    private TiledMapHandler mapHandler;
    ArrayList<Lane> lanes = new ArrayList<>();

    public Junction() {
        this.offset = new Vector2D();
        this.mapHandler = new TiledMapHandler(6,6,1,1, this.offset);


        Vector2D up = new Vector2D(0, -1);
        Vector2D right = new Vector2D(1, 0);
        Vector2D down = new Vector2D(0, 1);
        Vector2D left = new Vector2D(-1, 0);

        // End lanes
        this.addEndLane(new Vector2D(-0.5, 0.5), left);
        this.addEndLane(new Vector2D(-0.5, 1.5), left);
        this.addEndLane(new Vector2D(-0.5, 2.5), left);

        this.addEndLane(new Vector2D(3.5, -0.5), up);
        this.addEndLane(new Vector2D(4.5, -0.5), up);
        this.addEndLane(new Vector2D(5.5, -0.5), up);

        this.addEndLane(new Vector2D(0.5, 6.5), down);
        this.addEndLane(new Vector2D(1.5, 6.5), down);
        this.addEndLane(new Vector2D(2.5, 6.5), down);

        this.addEndLane(new Vector2D(6.5, 3.5), right);
        this.addEndLane(new Vector2D(6.5, 4.5), right);
        this.addEndLane(new Vector2D(6.5, 5.5), right);


        // Starting lanes
        this.addStartLane( new Vector2D(0.5, -0.5), down, Lane.TURN_RIGHT);
        this.addStartLane( new Vector2D(1.5, -0.5), down, Lane.STRAIGHT);
        this.addStartLane( new Vector2D(2.5, -0.5), down, Lane.TURN_LEFT);

        this.addStartLane( new Vector2D(6.5, 0.5), left, Lane.TURN_RIGHT);
        this.addStartLane( new Vector2D(6.5, 1.5), left, Lane.STRAIGHT);
        this.addStartLane( new Vector2D(6.5, 2.5), left, Lane.TURN_LEFT);

        this.addStartLane( new Vector2D(-0.5, 3.5), right, Lane.TURN_LEFT);
        this.addStartLane( new Vector2D(-0.5, 4.5), right, Lane.STRAIGHT);
        this.addStartLane( new Vector2D(-0.5, 5.5), right, Lane.TURN_RIGHT);

        this.addStartLane( new Vector2D(3.5, 6.5), up, Lane.TURN_LEFT);
        this.addStartLane( new Vector2D(4.5, 6.5), up, Lane.STRAIGHT);
        this.addStartLane( new Vector2D(5.5, 6.5), up, Lane.TURN_RIGHT);
    }

    public ArrayList<Lane> getLanes() {
        return lanes;
    }

    private void addStartLane(Vector2D endPos, Vector2D direction, int possDir) {
        Lane l = new Lane(null, null, this, endPos, direction, possDir);
        this.lanes.add(l);
    }

    private void addEndLane(Vector2D startPos, Vector2D direction) {
        Lane l = new Lane(this, startPos, null, null, direction, Lane.NONE);
        this.lanes.add(l);
    }

    public TiledMapHandler getMapHandler() {
        return this.mapHandler;
    }
}
