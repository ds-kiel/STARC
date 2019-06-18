package org.contikios.cooja.plugins.vanet.vehicle;

import org.contikios.cooja.Mote;
import org.contikios.cooja.plugins.vanet.vehicle.physics.DirectionalDistanceSensor;
import org.contikios.cooja.plugins.vanet.vehicle.physics.VehicleBody;
import org.contikios.cooja.plugins.vanet.world.World;
import org.contikios.cooja.plugins.vanet.world.physics.Vector2D;

import java.util.ArrayList;

public interface VehicleInterface {

    int STATE_INIT = 0;
    int STATE_INITIALIZED = 1;
    int STATE_QUEUING = 2;
    int STATE_WAITING = 3;
    int STATE_MOVING = 4;
    int STATE_LEAVING = 5;
    int STATE_FINISHED = 6;

    int REQUEST_STATE_INIT = 0;
    int REQUEST_STATE_SENT = 1;
    int REQUEST_STATE_ACKNOWLEDGED = 2;
    int REQUEST_STATE_ACCEPTED = 3;
    
    World getWorld();
    DirectionalDistanceSensor getDistanceSensor();
    Mote getMote();
    VehicleBody getBody();
    int getState();
    void step(double delta);

    ArrayList<Vector2D> getWaypoints();
    int getCurWayPointIndex();
}