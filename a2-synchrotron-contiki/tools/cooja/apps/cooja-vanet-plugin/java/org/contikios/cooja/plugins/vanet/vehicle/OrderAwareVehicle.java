package org.contikios.cooja.plugins.vanet.vehicle;

import org.contikios.cooja.plugins.vanet.transport_network.intersection.Lane;

public interface OrderAwareVehicle extends VehicleInterface {

    void setPredecessor(OrderAwareVehicle vehicle);
    OrderAwareVehicle getPredecessor();

    void setSuccessor(OrderAwareVehicle vehicle);
    OrderAwareVehicle getSuccessor();

    Lane getStartLane();
    Lane getTargetLane();
}