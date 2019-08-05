/*
 * Copyright (c) 2009, Swedish Institute of Computer Science.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the Institute nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE INSTITUTE AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE INSTITUTE OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 *
 */

package org.contikios.cooja.plugins.skins;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Mote;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.interfaces.Position;
import org.contikios.cooja.mote.memory.MemoryInterface;
import org.contikios.cooja.mote.memory.UnknownVariableException;
import org.contikios.cooja.mote.memory.VarMemory;
import org.contikios.cooja.plugins.Vanet;
import org.contikios.cooja.plugins.Visualizer;
import org.contikios.cooja.plugins.VisualizerSkin;
import org.contikios.cooja.plugins.vanet.transport_network.TransportNetwork;
import org.contikios.cooja.plugins.vanet.transport_network.intersection.Intersection;
import org.contikios.cooja.plugins.vanet.transport_network.intersection.Lane;
import org.contikios.cooja.plugins.vanet.transport_network.intersection.TrafficLightAwareIntersection;
import org.contikios.cooja.plugins.vanet.vehicle.LogAwareVehicleDecorator;
import org.contikios.cooja.plugins.vanet.vehicle.PlatoonawareVehicle;
import org.contikios.cooja.plugins.vanet.vehicle.VehicleInterface;
import org.contikios.cooja.plugins.vanet.world.World;
import org.contikios.cooja.plugins.vanet.world.physics.CollisionAwareBody;
import org.contikios.cooja.plugins.vanet.world.physics.Vector2D;

import javax.imageio.ImageIO;
import javax.swing.*;

/**
 * image visualizer skin.
 *
 * @author Patrick Rathje
 */
@ClassDescription("Vanet Visualizer")
public class VanetVisualizerSkin implements VisualizerSkin {
    private static Logger logger = Logger.getLogger(VanetVisualizerSkin.class);

    private static Visualizer visualizer = null;

    private static JPanel canvas = null;
    private BufferedImage img;

    private boolean showTileReservations = false;

    private static String screenExportDir;
    private static Simulation simulation;


    private static ExecutorService executorService;

    public VanetVisualizerSkin() {
        img = loadFromFile("img/intersection-big.png");
        executorService = Executors.newFixedThreadPool(1);
    }

    private BufferedImage loadFromFile(String path) {
        try {
            InputStream inp = getClass().getClassLoader().getResourceAsStream(path);
            return ImageIO.read(inp);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return null;
    }

    public void paintBeforeMotes(Graphics g) {

        render1mBackgroundGrid(g);

        World world = Vanet.world;

        if (world != null) {

            drawPlatoonConnections(g, world);


            TransportNetwork transportNetwork = world.getTransportNetwork();

            int width = transportNetwork.getWidth();
            int height = transportNetwork.getHeight();
            for(int y = 0; y < height; ++y) {
                for(int x = 0; x < width; ++x) {
                    Intersection intersection = transportNetwork.getIntersection(x, y);
                    renderIntersection(g, intersection);
                }
            }

            drawCollisionBoundaries(g, world);
            drawWaypointsForSelection(g, world);
        }
    }




    private void renderIntersection(Graphics g, Intersection intersection) {
        /* 100px equals Vanet.SCALE meters */
        double scale = Vanet.SCALE / 100.0;

        double width = img.getWidth() * scale;
        double height = img.getHeight() * scale;

        // we will center the image for now

        double centerX = width/2.0;
        double centerY = height/2.0;

        // and add an offset
        double offsetX = 3.0*Vanet.SCALE + intersection.getOffset().getX();
        double offsetY = 3.0*Vanet.SCALE + intersection.getOffset().getY();

        Point tl = visualizer.transformPositionToPixel(-centerX+offsetX, -centerY+offsetY, 0.0);
        Point br = visualizer.transformPositionToPixel(centerX+offsetX, centerY+offsetY, 0.0);

        g.drawImage(img, tl.x, tl.y, br.x-tl.x, br.y-tl.y, new ImageObserver() {
            @Override
            public boolean imageUpdate(Image img, int infoflags, int x, int y, int width, int height) {
                return false;
            }
        });

        if (intersection instanceof TrafficLightAwareIntersection) {

            Map<Lane, Integer> states = ((TrafficLightAwareIntersection) intersection).getTrafficLightStates(simulation.getSimulationTimeMillis());
            intersection.getStartLanes().forEach(
                l -> {
                    Vector2D sp = l.getStartPos();
                    Vector2D ep = l.getEndPos();

                    float r = 0.1f * (float) Vanet.SCALE;

                    Color color = Color.RED;
                    Integer state = states.get(l);

                    if (state == TrafficLightAwareIntersection.PHASE_GREEN) {
                        color = Color.GREEN;
                    } else if (state == TrafficLightAwareIntersection.PHASE_YELLOW) {
                        color = Color.YELLOW;
                    }

                    Vector2D p = Vector2D.diff(ep, sp);
                    p.normalize();
                    p.rotate(Math.PI/4.0);
                    p.scale(Vanet.SCALE * Math.sqrt(0.5) * 0.5);
                    p.add(ep);
                    drawCircle(g, p, r, color);
                }
            );
        }

        /*FontMetrics fm = g.getFontMetrics();
        intersection.getLanes().forEach(
            l -> {
                Vector2D sp = l.getStartPos();
                Vector2D ep = l.getEndPos();

                float r = 0.075f* (float) Vanet.SCALE;
                drawCircle(g, sp, r, Color.RED);
                drawCircle(g, ep, r*2, Color.BLUE);

                Point pixels = visualizer.transformPositionToPixel(sp.getX(), sp.getY(),0);
                Point pixele = visualizer.transformPositionToPixel(ep.getX(), ep.getY(),0);
                {
                    int y = pixels.y + 2*Visualizer.MOTE_RADIUS + 3;
                    String a = String.valueOf(l.getStartId());
                    int msgWidth = fm.stringWidth(a);
                    g.drawString(a, pixels.x - msgWidth/2, y);
                }

                {
                    int y = pixele.y + 2*Visualizer.MOTE_RADIUS + 3;
                    String a = String.valueOf(l.getEndId());
                    int msgWidth = fm.stringWidth(a);
                    g.drawString(a, pixele.x - msgWidth/2, y);
                }

                Graphics2D g2 = (Graphics2D) g;

                //g2.setStroke(new BasicStroke(10));
                // draw a line for the current movement direction
                g2.setColor(Color.BLACK);
                g2.drawLine(pixels.x, pixels.y, pixele.x, pixele.y);
            }
        );*/
    }


    private void render1mBackgroundGrid(Graphics g) {
        /* Background grid every X meters */

        double meters = Vanet.SCALE;

        Position upperLeft =
                visualizer.transformPixelToPosition(-(int)meters, -(int)meters);
        upperLeft.setCoordinates(
                ((int)(upperLeft.getXCoordinate()/meters))*meters,
                ((int)(upperLeft.getYCoordinate()/meters))*meters,
                0);
        Position lowerRight =
                visualizer.transformPixelToPosition(visualizer.getWidth(), visualizer.getHeight());
        lowerRight.setCoordinates(
                ((int)(lowerRight.getXCoordinate()/meters))*meters,
                ((int)(lowerRight.getYCoordinate()/meters))*meters,
                0);

        if ((lowerRight.getXCoordinate() - upperLeft.getXCoordinate())/meters < 200 &&
                (lowerRight.getYCoordinate() - upperLeft.getYCoordinate())/meters < 200) {
            /* X axis */
            for (double x = upperLeft.getXCoordinate(); x <= lowerRight.getXCoordinate(); x += meters) {
                int pixel = visualizer.transformPositionToPixel(x, 0, 0).x;
                if (x % 100 == 0) {
                    g.setColor(Color.GRAY);
                } else {
                    g.setColor(Color.LIGHT_GRAY);
                }
                g.drawLine(
                        pixel,
                        0,
                        pixel,
                        visualizer.getHeight()
                );
            }

            /* Y axis */
            for (double y = upperLeft.getYCoordinate(); y <= lowerRight.getYCoordinate(); y += meters) {
                int pixel = visualizer.transformPositionToPixel(0, y, 0).y;
                if (y % 100 == 0) {
                    g.setColor(Color.GRAY);
                } else {
                    g.setColor(Color.LIGHT_GRAY);
                }
                g.drawLine(
                        0,
                        pixel,
                        visualizer.getWidth(),
                        pixel
                );
            }
        }
    }

    private void drawWaypointsForSelection(Graphics g, World world) {
        if (visualizer.getSelectedMotes().size() > 0) {
            for (Mote mote: visualizer.getSelectedMotes()) {
                VehicleInterface v = world.getVehicle(mote);

                if (v == null) {
                    continue;
                }

                ArrayList<Vector2D> wps = v.getWaypoints();

                for (int i = 0; i < wps.size(); ++i) {

                    Vector2D p = wps.get(i);
                    // we draw a little circle at the waypoint

                    Color c = Color.RED;

                    if (i < v.getCurWayPointIndex()) {
                        c = Color.BLUE;
                    } else if (i > v.getCurWayPointIndex()) {
                        c = Color.GRAY;
                    }

                    float r = 0.075f* (float) Vanet.SCALE;
                    drawCircle(g, p, r, c);
                }

                Graphics2D g2 = (Graphics2D) g;

                Vector2D targetPos = v.getNextWaypoint();
                if (targetPos != null) {
                    Point lineStart = visualizer.transformPositionToPixel(v.getBody().getCenter().getX(), v.getBody().getCenter().getY(), 0);
                    Point lineEnd = visualizer.transformPositionToPixel(targetPos.getX(), targetPos.getY(),0);

                    // draw a line for the target point
                    g2.setColor(Color.BLACK);
                    g2.drawLine(lineStart.x, lineStart.y, lineEnd.x, lineEnd.y);
                }
            }
        }
    }


    private void drawChannels(Graphics g) {

        FontMetrics fm = g.getFontMetrics();
        g.setColor(Color.BLACK);

        /* Paint attributes below motes */
        Mote[] allMotes = simulation.getMotes();
        for (Mote mote: allMotes) {

            Position pos = mote.getInterfaces().getPosition();
            Point pixel = visualizer.transformPositionToPixel(pos);

            int y = pixel.y + 2*Visualizer.MOTE_RADIUS + 3;

            VarMemory moteMemory = new VarMemory(mote.getMemory());

            try {
                long channel = Integer.toUnsignedLong(moteMemory.getInt16ValueOf("chaos_current_channel"));

                String a = String.valueOf(channel);
                int msgWidth = fm.stringWidth(a);
                g.drawString(a, pixel.x - msgWidth/2, y);
            } catch (UnknownVariableException e) {
                e.printStackTrace();
            }
        }
    }

    private void drawCircle(Graphics g, Vector2D p, double r, Color color) {
        g.setColor(color);
        Point tl = visualizer.transformPositionToPixel(p.getX(), p.getY(), 0);
        Point br = visualizer.transformPositionToPixel(p.getX()+r*2, p.getY()+r*2, 0);

        int dx = br.x-tl.x;
        int dy = br.y-tl.y;

        g.fillOval(tl.x-dx/2, tl.y-dy/2, dx, dy);
    }
    private void drawCollisionBoundaries(Graphics g, World world) {

        if (visualizer.getSelectedMotes().size() > 0) {
            for (Mote mote: visualizer.getSelectedMotes()) {
                VehicleInterface v = world.getVehicle(mote);

                if (v == null) {
                    continue;
                }

                Vector2D p = v.getBody().getCenter();

                double r = v.getBody().getRadius();
                drawCircle(g, p, r, Color.YELLOW);



                Vector2D dir = v.getBody().getDir();

                Vector2D endPos = new Vector2D(dir);
                endPos.scale(r);
                endPos.add(p);

                Point lineStart = visualizer.transformPositionToPixel(p.getX(), p.getY(), 0);
                Point lineEnd = visualizer.transformPositionToPixel(endPos.getX(), endPos.getY(), 0);


                Graphics2D g2 = (Graphics2D) g;

                //g2.setStroke(new BasicStroke(10));
                // draw a line for the current movement direction
                g2.setColor(Color.BLACK);
                g2.drawLine(lineStart.x, lineStart.y, lineEnd.x, lineEnd.y);
            }
        }
    }

    private void drawPlatoonConnections(Graphics g, World world) {

        Set<PlatoonawareVehicle> checked = new HashSet<>();

        world.getVehicles().stream().filter(PlatoonawareVehicle.class::isInstance).forEach(
            pv -> checked.add((PlatoonawareVehicle)pv)
        );

        /*if (visualizer.getSelectedMotes().size() == 0) {
            return;
        }

        ArrayList<PlatoonawareVehicle> toCheck = new ArrayList<>();


        for (Mote mote: visualizer.getSelectedMotes()) {
            VehicleInterface v = world.getVehicle(mote);
            if (v instanceof PlatoonawareVehicle) {
                toCheck.add((PlatoonawareVehicle)v);
            }
        }

        while(toCheck.size() > 0) {
            PlatoonawareVehicle v = toCheck.remove(0);

            if (!checked.contains(v)) {
                checked.add(v);

                PlatoonawareVehicle pred = v.getPlatoonPredecessor();
                if (pred != null) {
                    if (!checked.contains(pred)) {
                        toCheck.add(pred);
                    }
                }

                PlatoonawareVehicle succ = v.getPlatoonSuccessor();
                if (succ != null) {
                    if (!checked.contains(succ)) {
                        toCheck.add(succ);
                    }
                }
            }
        }*/

        Graphics2D g2 = (Graphics2D) g;

        Stroke initialStroke = g2.getStroke();
        g2.setStroke(new BasicStroke(5));
        g2.setColor(Color.LIGHT_GRAY);

        checked.forEach(
            pv -> {
                if (pv.getPlatoonPredecessor() != null) {
                    Vector2D p = pv.getBody().getCenter();
                    Vector2D endPos = pv.getPlatoonPredecessor().getBody().getCenter();
                    Point lineStart = visualizer.transformPositionToPixel(p.getX(), p.getY(), 0);
                    Point lineEnd = visualizer.transformPositionToPixel(endPos.getX(), endPos.getY(), 0);
                    g2.drawLine(lineStart.x, lineStart.y, lineEnd.x, lineEnd.y);
                }
            }
        );

        g2.setStroke(initialStroke);
    }

    private void drawTileReservations(Graphics g, World world) {
        if (!showTileReservations) {
            return;
        }
        // TODO
    }

    public void setActive(Simulation simulation, Visualizer vis) {
        this.visualizer = vis;
        this.simulation = simulation;
        this.canvas = vis.getCurrentCanvas();

        this.canvas.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_T) {
                    showTileReservations = !showTileReservations;
                }
            }

            @Override
            public void keyPressed(KeyEvent e) {

            }

            @Override
            public void keyReleased(KeyEvent e) {

            }
        });
    }

    public void setInactive() {
    }

    public Color[] getColorOf(Mote mote) {

        Color[] colors = null;

        World world = Vanet.world;

        if (world != null) {
            VehicleInterface v = world.getVehicle(mote);

            if (v != null) {

                if (v.getState() != VehicleInterface.STATE_INIT) {
                    VarMemory moteMemory = new VarMemory(mote.getMemory());

                    try {
                        byte hasNodeIndex = moteMemory.getByteValueOf("chaos_has_node_index");

                        if (hasNodeIndex != 0) {
                            colors = new Color[1];
                            colors[0] = Color.LIGHT_GRAY;
                        }
                    } catch (UnknownVariableException e) {
                        //e.printStackTrace();
                        // TODO check before trying to access the memory...
                    }
                } else {
                    // Set color of unitinialized nodes
                    //Color transparent = new Color(0,0,0,0);
                    //colors = new Color[2];
                    //colors[0] = transparent;
                    //colors[1] = transparent;
                }


                // mark the cars for 10 secs
                if (v.getBody() != null) {
                    if (v.getBody().hasCollision(world.getCurrentMS()- 10 * 1000, world.getCurrentMS())) {
                        colors = new Color[1];
                        colors[0] = Color.RED;
                    }
                }
            }
        }
        return colors;
    }

    public void paintAfterMotes(Graphics g) {
        //drawChannels(g);
    }

    public Visualizer getVisualizer() {
        return visualizer;
    }


    public static void setScreenExportDir(String screenExportDir) {
        VanetVisualizerSkin.screenExportDir = screenExportDir;
    }

    public static void saveImage(long ms) {
        if (VanetVisualizerSkin.screenExportDir != null && VanetVisualizerSkin.screenExportDir.length() > 0 && visualizer != null && canvas != null) {
            JPanel paintPane = canvas;
            visualizer.setSize(768+12,768+54);
            BufferedImage image = new BufferedImage(paintPane.getWidth(), paintPane.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            paintPane.printAll(g);
            g.dispose();

            File directory = new File(VanetVisualizerSkin.screenExportDir);
            if (!directory.exists()){
                directory.mkdirs();
            }

            File f = new File(directory, String.format("img_%06d.jpg", directory.list().length));

            // Use an own thread for this, since this is very io abusive ;)
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    try {
                        ImageIO.write(image, "JPG", f);
                        System.out.println(f.getAbsoluteFile());
                    } catch (IOException exp) {
                        exp.printStackTrace();
                    }
                }
            };
            executorService.submit(r);
        }
    }

    public static void waitForImages() {
        executorService.shutdown();
        try {
            executorService.awaitTermination(1, TimeUnit.HOURS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
