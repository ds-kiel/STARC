package org.contikios.cooja.plugins;

import org.apache.log4j.Logger;
import org.contikios.cooja.*;

import org.contikios.cooja.plugins.vanet.vehicle.MessageProxy;
import org.contikios.cooja.plugins.vanet.vehicle.Vehicle;
import org.contikios.cooja.plugins.vanet.vehicle.physics.VehicleBody;
import org.contikios.cooja.plugins.vanet.world.World;
import org.contikios.cooja.plugins.vanet.world.physics.Vector2D;

import java.util.Observable;
import java.util.Observer;

@ClassDescription("Vanet")
@PluginType(PluginType.SIM_PLUGIN)
public class Vanet extends VisPlugin {
    private static Logger logger = Logger.getLogger(Vanet.class);

    private static final boolean QUIET = false;
    private static final long TICKS = 50;
    private static final long TICK_MS = 1000/TICKS;

    private Simulation simulation;

    private Observer millisecondObserver;

    private World world;


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

        // TODO: Each millisecond is a little bit excessive isn't it? ;)
        // maybe we should do like 50 Ticks per second?
        millisecondObserver = new Observer() {
            @Override
            public void update(Observable o, Object arg) {
                if (simulation.getSimulationTimeMillis() >= nextUpdate) {
                    System.out.println("VANET");
                    Vanet.this.update(TICK_MS / 1000.0); // one s
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
            world = new World();
            Mote[] motes = simulation.getMotes();

            // for each mote add a new vehicle
            for (Mote m : motes) {

                MessageProxy mp = new MessageProxy(m);
                VehicleBody body = new VehicleBody(String.valueOf(m.getID()));

                body.setCenter(
                    new Vector2D(
                        m.getInterfaces().getPosition().getXCoordinate(),
                        m.getInterfaces().getPosition().getYCoordinate()
                    )
                );

                Vehicle v = new Vehicle(m, mp, body);
                world.addVehicle(v);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void update(double delta) {
        // first update the world with the physics!
        // then update all the nodes
        if (world != null) {
            world.simulate(delta);
        }
    }
}
