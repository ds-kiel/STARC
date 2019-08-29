package org.contikios.cooja.plugins;

import org.contikios.cooja.*;

import org.contikios.cooja.plugins.skins.VanetVisualizerSkin;
import org.contikios.cooja.plugins.vanet.config.VanetConfig;
import org.contikios.cooja.plugins.vanet.log.Logger;
import org.contikios.cooja.plugins.vanet.world.World;

import java.util.Observable;
import java.util.Observer;
import java.util.Random;
import org.jdom.Element;
import java.util.Collection;

@ClassDescription("Vanet")
@PluginType(PluginType.SIM_CONTROL_PLUGIN)
public class Vanet extends VisPlugin {

    private static final boolean QUIET = false;
    private static final long TICKS = 50;
    private static final long TICK_MS = 1000/TICKS;

    private Simulation simulation;

    private Observer millisecondObserver;

    public static World world;
    private long nextUpdate = 0;

    public static final double SCALE = 3.0;

    private VanetConfig vanetConfig = new VanetConfig();

    private long timeout = 0;


    public Vanet(Simulation simulation, final Cooja Cooja) {
        super("Vanet", Cooja, false);

        this.simulation = simulation;

        vanetConfig.addSettingsObserver(new Observer() {
            @Override
            public void update(Observable o, Object arg) {
                world = new World(Vanet.this.simulation, new Random(simulation.getRandomSeed()+124), vanetConfig);
                Logger.setLogDir(((String) vanetConfig.getParameterValue(VanetConfig.Parameter.log_dir)));
                VanetVisualizerSkin.setScreenExportDir(((String) vanetConfig.getParameterValue(VanetConfig.Parameter.screen_export_dir)));

                timeout = vanetConfig.getParameterLongValue(VanetConfig.Parameter.timeout);
            }
        });

        // Create a dummy thread to be able to start the simulation although no mote was added
        simulation.invokeSimulationThread(new Runnable() {
            @Override
            public void run() {
                System.out.println("");
            }
        });
    }

    public void startPlugin() {
        super.startPlugin();

        millisecondObserver = (Observable o, Object arg) -> {
            long ms = simulation.getSimulationTimeMillis();

            if (timeout > 0 && ms > timeout) {
                shutdown();
                return;
            }


            if (ms >= nextUpdate) {
                Vanet.this.update(TICK_MS); // one s
                nextUpdate += TICK_MS;
            }

            if (ms % TICK_MS == 0) {
                Logger.flush();
            }
        };
        simulation.addMillisecondObserver(millisecondObserver);

        if (timeout > 0) {
            simulation.startSimulation();
        }
    }

    protected void shutdown() {
        simulation.invokeSimulationThread(new Runnable() {
            public void run() {
                simulation.stopSimulation();
                Logger.flush();
                VanetVisualizerSkin.waitForImages();
                simulation.getCooja().doQuit(false, 0);
            }
        });
    }

    public void closePlugin() {
        simulation.deleteMillisecondObserver(millisecondObserver);
    }

    private void update(long deltaMS) {
        // first update the world with the physics!
        // then update all the nodes#+
        if (world != null) {
            world.simulate(simulation.getSimulationTimeMillis(), deltaMS);
            VanetVisualizerSkin.saveImage(simulation.getSimulationTimeMillis());
        }
    }

    public Collection<Element> getConfigXML() {
        return vanetConfig.getConfigXML();
    }
    public boolean setConfigXML(Collection<Element> configXML,
                                boolean visAvailable) {
        return vanetConfig.setConfigXML(configXML);
    }
}
