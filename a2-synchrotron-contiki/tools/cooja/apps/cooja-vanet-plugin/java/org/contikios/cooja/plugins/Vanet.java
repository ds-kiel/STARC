package org.contikios.cooja.plugins;

import org.apache.log4j.Logger;
import org.contikios.cooja.*;

import org.contikios.cooja.plugins.skins.VanetVisualizerSkin;
import org.contikios.cooja.plugins.vanet.vehicle.LogAwareVehicleDecorator;
import org.contikios.cooja.plugins.vanet.vehicle.MessageProxy;
import org.contikios.cooja.plugins.vanet.vehicle.Vehicle;
import org.contikios.cooja.plugins.vanet.vehicle.VehicleInterface;
import org.contikios.cooja.plugins.vanet.vehicle.physics.DirectionalDistanceSensor;
import org.contikios.cooja.plugins.vanet.vehicle.physics.VehicleBody;
import org.contikios.cooja.plugins.vanet.world.World;
import org.contikios.cooja.plugins.vanet.world.physics.Vector2D;

import java.util.Observable;
import java.util.Observer;
import java.util.Random;

@ClassDescription("Vanet")
@PluginType(PluginType.SIM_PLUGIN)
public class Vanet extends VisPlugin {
    private static Logger logger = Logger.getLogger(Vanet.class);

    private static final boolean QUIET = false;
    private static final long TICKS = 50;
    private static final long TICK_MS = 1000/TICKS;

    private Simulation simulation;

    private Observer millisecondObserver;

    public static World world;


    private long nextUpdate = 0;

    public Vanet(Simulation simulation, final Cooja Cooja) {
        super("Vanet", Cooja, false);
        this.simulation = simulation;

        if (!QUIET) {
            logger.info("Vanet plugin started at (ms): " + simulation.getSimulationTimeMillis());
        }
    }

    public void startPlugin() {
        super.startPlugin();

        simulation.invokeSimulationThread(
            new Runnable() {
                @Override
                public void run() {
                    Vanet.this.initConnections();
                }
            }
        );

        millisecondObserver = new Observer() {
            @Override
            public void update(Observable o, Object arg) {
                if (simulation.getSimulationTimeMillis() >= nextUpdate) {
                    Vanet.this.update(TICK_MS); // one s
                    nextUpdate += TICK_MS;
                }
            }
        };
        simulation.addMillisecondObserver(millisecondObserver);
    }

    public void closePlugin() {
        simulation.deleteMillisecondObserver(millisecondObserver);
    }

    // Initialize the connections to each Mote
    private void initConnections() {
        // first we try to get a connection to all nodes

        if (!QUIET) {
            logger.info("Vanet init connections at (ms): " + simulation.getSimulationTimeMillis());
        }

        try {
            World.RAND = new Random(simulation.getRandomSeed()+124);
            world = new World(simulation);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void update(long deltaMS) {
        // first update the world with the physics!
        // then update all the nodes#+
        if (world != null) {
            world.simulate(deltaMS);
            VanetVisualizerSkin.saveImage(simulation.getSimulationTimeMillis());
        }
    }
}
