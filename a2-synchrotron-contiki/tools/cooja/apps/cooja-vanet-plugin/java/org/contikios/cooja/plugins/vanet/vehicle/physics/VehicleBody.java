package org.contikios.cooja.plugins.vanet.vehicle.physics;

import org.contikios.cooja.plugins.vanet.world.physics.CircleBody;
import org.contikios.cooja.plugins.vanet.world.physics.Vector2D;

public class VehicleBody extends CircleBody {
    public VehicleBody(String name) {
        super(name, 0.33);
    }

    private Vector2D dir = new Vector2D();

    public Vector2D getDir() {
        return dir;
    }

    public void setDir(Vector2D dir) {
        this.dir = dir;
    }
}
