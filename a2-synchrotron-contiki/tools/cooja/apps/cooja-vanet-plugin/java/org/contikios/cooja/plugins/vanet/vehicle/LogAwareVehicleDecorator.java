package org.contikios.cooja.plugins.vanet.vehicle;

import org.contikios.cooja.Mote;
import org.contikios.cooja.plugins.vanet.log.Logger;
import org.contikios.cooja.plugins.vanet.vehicle.physics.DirectionalDistanceSensor;
import org.contikios.cooja.plugins.vanet.vehicle.physics.VehicleBody;
import org.contikios.cooja.plugins.vanet.world.World;
import org.contikios.cooja.plugins.vanet.world.physics.Vector2D;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class LogAwareVehicleDecorator implements VehicleInterface {
    protected VehicleInterface impl;

    protected Map<Integer, String> stateMap = new HashMap<Integer, String>() {{
        put(STATE_INIT, "init");
        put(STATE_INITIALIZED, "initalized");
        put(STATE_QUEUING, "queuing");
        put(STATE_WAITING, "waiting");
        put(STATE_MOVING, "moving");
        put(STATE_LEAVING, "leaving");
        put(STATE_FINISHED, "finished");
    }};

    public LogAwareVehicleDecorator(VehicleInterface impl) {
        this.impl = impl;
    }

    @Override
    public World getWorld() {
        return impl.getWorld();
    }

    @Override
    public DirectionalDistanceSensor getDistanceSensor() {
        return impl.getDistanceSensor();
    }

    @Override
    public Mote getMote() {
        return impl.getMote();
    }

    @Override
    public VehicleBody getBody() {
        return impl.getBody();
    }

    @Override
    public int getState() {
        return impl.getState();
    }

    protected String getStateName(int state) {
        return stateMap.get(state);
    }

    @Override
    public ArrayList<Vector2D> getWaypoints() {
        return impl.getWaypoints();
    }

    @Override
    public int getCurWayPointIndex() {
        return impl.getCurWayPointIndex();
    }

    @Override
    public void step(double delta) {
        impl.step(delta);
        int state = impl.getState();
        Logger.event("state", impl.getWorld().getCurrentMS(), getStateName(state), impl.getMote().getID());
        Logger.event("speed", impl.getWorld().getCurrentMS(), String.valueOf(impl.getBody().getVel().length()), impl.getMote().getID());
    }
}