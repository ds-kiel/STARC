package org.contikios.cooja.plugins.vanet.vehicle;


import org.contikios.cooja.plugins.vanet.log.Logger;
import org.contikios.cooja.plugins.vanet.world.World;

import java.util.ArrayList;
import java.util.List;

public class ChaosStatsHandler {

    protected int id;

    protected int round;
    protected int expectedSlots;
    protected int receivedSlots;
    protected int slotsPerMessage;


    protected ArrayList<String> buffer = new ArrayList<>();

    ChaosStatsHandler(int id) {
        this.id = id;
    }

    boolean supports(byte[] msg) {
        return new String(msg).startsWith("MC-STATS");
    }

    void handle(byte[] msg) {

        String stringMsg = new String(msg);

        ArrayList<Byte> strippedMsg = new ArrayList<>();
        for (byte b: msg) {
            strippedMsg.add(b);
        }

        if (stringMsg.startsWith("MC-STATS-START")) {
            handleStartMsg(strippedMsg.subList("MC-STATS-START".length(),strippedMsg.size()));
        } else if (stringMsg.startsWith("MC-STATS-SLOTS")) {
            handleSlotsMsg(strippedMsg.subList("MC-STATS-SLOTS".length(),strippedMsg.size()));
        } else if (stringMsg.startsWith("MC-STATS-END")) {
            handleEndMsg(strippedMsg.subList("MC-STATS-END".length(),strippedMsg.size()));
        }
    }

    private void handleStartMsg(List<Byte> msg) {

        round = ((msg.get(0) & 0xff) << 8) | (msg.get(1) & 0xff);
        expectedSlots = ((msg.get(2) & 0xff) << 8) | (msg.get(3) & 0xff);
        slotsPerMessage = msg.get(4) & 0xff;
        receivedSlots = 0;
        //System.out.println(String.format("Start %d, %d, %d",  round, expectedSlots, slotsPerMessage));
    }

    private void handleSlotsMsg(List<Byte> msg) {
        final int sizePerSlot = 9;

        int msgSlots = msg.size() / sizePerSlot;

        while(msgSlots > 0) {

            int node_count = Byte.toUnsignedInt(msg.remove(0));
            int flag_progress = Byte.toUnsignedInt(msg.remove(0));
            int phase = Byte.toUnsignedInt(msg.remove(0));
            int has_node_index = Byte.toUnsignedInt(msg.remove(0));
            int node_index = Byte.toUnsignedInt(msg.remove(0));
            int type = Byte.toUnsignedInt(msg.remove(0));
            int isInitiator = Byte.toUnsignedInt(msg.remove(0));
            int config = ((msg.remove(0) & 0xff) << 8) | (msg.remove(0) & 0xff);

            String logMsg = String.format("%d, %d, %d, %d, %d, %d, %d, %d, %d, %d",  round, receivedSlots, phase, node_count, flag_progress, has_node_index, node_index, type, isInitiator, config);
            buffer.add(
                logMsg
            );

            receivedSlots++;
            msgSlots--;
        }
    }

    private void handleEndMsg(List<Byte> msg) {

        // Write everything at once so we are sure, we get the full stats everytime!
        String idStr = String.format("%06d", id);
        buffer.forEach(
            logMsg -> Logger.event("chaos", World.getCurrentMS(), logMsg,  idStr)
        );

        buffer.clear();
    }
}
