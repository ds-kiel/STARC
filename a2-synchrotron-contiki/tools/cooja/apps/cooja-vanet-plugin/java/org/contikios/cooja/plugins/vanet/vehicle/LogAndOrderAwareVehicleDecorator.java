package org.contikios.cooja.plugins.vanet.vehicle;

import org.contikios.cooja.plugins.vanet.transport_network.intersection.Lane;

public class LogAndOrderAwareVehicleDecorator extends LogAwareVehicleDecorator implements OrderAwareVehicle {

    OrderAwareVehicle impl;

    public LogAndOrderAwareVehicleDecorator(OrderAwareVehicle impl) {
        super(impl);
        this.impl = impl;
    }

    @Override
    public void setPredecessor(OrderAwareVehicle vehicle) {
        impl.setPredecessor(vehicle);
    }

    @Override
    public OrderAwareVehicle getPredecessor() {
        return impl.getPredecessor();
    }

    @Override
    public void setSuccessor(OrderAwareVehicle vehicle) {
        impl.setSuccessor(vehicle);
    }

    @Override
    public OrderAwareVehicle getSuccessor() {
        return impl.getSuccessor();
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