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
@PluginType(PluginType.SIM_PLUGIN)
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

    public Vanet(Simulation simulation, final Cooja Cooja) {
        super("Vanet", Cooja, false);

        this.simulation = simulation;

        World.RAND = new Random(simulation.getRandomSeed()+124);

        vanetConfig.addSettingsObserver(new Observer() {
            @Override
            public void update(Observable o, Object arg) {
                double vps = vanetConfig.getParameterDoubleValue(VanetConfig.Parameter.vehicles_per_hour);
                int networkWidth = vanetConfig.getParameterIntegerValue(VanetConfig.Parameter.network_width);
                int networkHeight = vanetConfig.getParameterIntegerValue(VanetConfig.Parameter.network_height);
                int intersectionType = vanetConfig.getParameterIntegerValue(VanetConfig.Parameter.intersection_type);
                world = new World(Vanet.this.simulation, networkWidth, networkHeight, intersectionType);
                world.setVehiclesPerSecond(vps);
                Logger.setLogDir(((String) vanetConfig.getParameterValue(VanetConfig.Parameter.log_dir)));
                VanetVisualizerSkin.setScreenExportDir(((String) vanetConfig.getParameterValue(VanetConfig.Parameter.screen_export_dir)));
            }
        });

        simulation.invokeSimulationThread(new Runnable() {
            @Override
            public void run() {
                System.out.println("Dummy action to allow simulation start");
            }
        });
    }

    public void startPlugin() {
        super.startPlugin();

        millisecondObserver = (Observable o, Object arg) -> {
            if (simulation.getSimulationTimeMillis() >= nextUpdate) {
                Vanet.this.update(TICK_MS); // one s
                nextUpdate += TICK_MS;
            }

            if (simulation.getSimulationTimeMillis() % TICK_MS == 0) {
                Logger.flush();
            }
        };
        simulation.addMillisecondObserver(millisecondObserver);
    }

    public void closePlugin() {
        simulation.deleteMillisecondObserver(millisecondObserver);
    }

    private void update(long deltaMS) {
        // first update the world with the physics!
        // then update all the nodes#+
        if (world != null) {
            world.simulate(deltaMS);
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
