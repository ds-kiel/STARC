package org.contikios.cooja.plugins.vanet.vehicle;

import org.contikios.cooja.plugins.vanet.transport_network.intersection.Lane;

public class LogAndPlatoonAwareVehicleDecorator extends LogAwareVehicleDecorator implements PlatoonawareVehicle {

    PlatoonawareVehicle impl;

    public LogAndPlatoonAwareVehicleDecorator(PlatoonawareVehicle impl) {
        super(impl);
        this.impl = impl;
    }

    @Override
    public void setPlatoonPredecessor(PlatoonawareVehicle vehicle) {
        impl.setPlatoonPredecessor(vehicle);
    }

    @Override
    public PlatoonawareVehicle getPlatoonPredecessor() {
        return impl.getPlatoonPredecessor();
    }

    @Override
    public void setPlatoonSuccessor(PlatoonawareVehicle vehicle) {
        impl.setPlatoonSuccessor(vehicle);
    }

    @Override
    public PlatoonawareVehicle getPlatoonSuccessor() {
        return impl.getPlatoonSuccessor();
    }

    @Override
    public Lane getStartLane() {
        return impl.getStartLane();
    }

    @Override
    public Lane getTargetLane() {
        return impl.getTargetLane();
    }
}