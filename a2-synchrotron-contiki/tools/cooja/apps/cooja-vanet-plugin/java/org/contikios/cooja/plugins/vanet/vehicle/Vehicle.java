package org.contikios.cooja.plugins.vanet.vehicle;

import org.contikios.cooja.Mote;
import org.contikios.cooja.plugins.vanet.log.Logger;
import org.contikios.cooja.plugins.vanet.vehicle.physics.VehicleBody;

import java.nio.charset.StandardCharsets;

public class Vehicle {
    private Mote mote; // unique identifier
    private MessageProxy messageProxy; // used for communication with cooja motes
    private VehicleBody body; // our physics model

    public Vehicle(Mote mote, MessageProxy messageProxy, VehicleBody body) {
        this.mote = mote;
        this.messageProxy = messageProxy;
        this.body = body;
    }

    public Mote getMote() {
        return mote;
    }

    public VehicleBody getBody() {
        return body;
    }

    public void step(double delta) {
        // we can do

        byte[] msg = null;

        msg = messageProxy.receive();
        while (msg != null) {
            messageProxy.send(msg); // just resend it
            msg = messageProxy.receive();
        }
    }
}