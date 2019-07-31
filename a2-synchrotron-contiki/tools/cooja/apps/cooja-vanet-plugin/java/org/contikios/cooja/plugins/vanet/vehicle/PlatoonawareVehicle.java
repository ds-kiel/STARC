package org.contikios.cooja.plugins.vanet.vehicle;

import org.contikios.cooja.plugins.vanet.transport_network.intersection.Lane;

public interface PlatoonawareVehicle extends VehicleInterface {

    void setPlatoonPredecessor(PlatoonawareVehicle vehicle);
    PlatoonawareVehicle getPlatoonPredecessor();

    void setPlatoonSuccessor(PlatoonawareVehicle vehicle);
    PlatoonawareVehicle getPlatoonSuccessor();

    Lane getStartLane();
    Lane getTargetLane();
}
