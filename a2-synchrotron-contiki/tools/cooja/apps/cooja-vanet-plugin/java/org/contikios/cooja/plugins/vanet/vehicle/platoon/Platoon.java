package org.contikios.cooja.plugins.vanet.vehicle.platoon;

import java.util.LinkedList;

public abstract class Platoon {

    Platoon(PlatoonAwareVehicle platoonAwareVehicle) {
            this.members.add(platoonAwareVehicle);
    }

    LinkedList<PlatoonAwareVehicle> members = new LinkedList<>();

    public boolean isHead(PlatoonAwareVehicle platoonAwareVehicle) {
        return platoonAwareVehicle.getID() == getHead().getID();
    }

    public boolean isTail(PlatoonAwareVehicle platoonAwareVehicle) {
        return platoonAwareVehicle.getID() == getTail().getID();
    }

    public PlatoonAwareVehicle getHead() {
        return members.getFirst();
    }

    public PlatoonAwareVehicle getTail() {
        return members.getLast();
    }

    public LinkedList<PlatoonAwareVehicle> getMembers() {
        return members;
    }

    public void setMembers(LinkedList<PlatoonAwareVehicle> members) {
        this.members = members;
    }

    public abstract void leave(PlatoonAwareVehicle vehicle);
}
