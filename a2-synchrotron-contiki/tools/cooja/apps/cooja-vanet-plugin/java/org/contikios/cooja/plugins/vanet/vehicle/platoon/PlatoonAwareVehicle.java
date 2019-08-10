package org.contikios.cooja.plugins.vanet.vehicle.platoon;

import org.contikios.cooja.plugins.vanet.vehicle.OrderAwareVehicle;

public interface PlatoonAwareVehicle extends OrderAwareVehicle {
    void setPlatoon(Platoon platoon);
    Platoon getPlatoon();
}
