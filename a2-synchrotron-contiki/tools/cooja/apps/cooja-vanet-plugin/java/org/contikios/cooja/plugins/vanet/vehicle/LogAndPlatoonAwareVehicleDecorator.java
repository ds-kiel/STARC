package org.contikios.cooja.plugins.vanet.vehicle;

import org.contikios.cooja.plugins.vanet.vehicle.platoon.Platoon;
import org.contikios.cooja.plugins.vanet.vehicle.platoon.PlatoonAwareVehicle;

public class LogAndPlatoonAwareVehicleDecorator extends LogAndOrderAwareVehicleDecorator implements PlatoonAwareVehicle {

    PlatoonAwareVehicle impl;

    public LogAndPlatoonAwareVehicleDecorator(PlatoonAwareVehicle impl) {
        super(impl);
        this.impl = impl;
    }

    @Override
    public void setPlatoon(Platoon platoon) {
        impl.setPlatoon(platoon);
    }

    @Override
    public Platoon getPlatoon() {
        return impl.getPlatoon();
    }
}