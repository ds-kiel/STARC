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
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import org.apache.log4j.Logger;

import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Mote;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.interfaces.Position;
import org.contikios.cooja.plugins.Vanet;
import org.contikios.cooja.plugins.Visualizer;
import org.contikios.cooja.plugins.VisualizerSkin;
import org.contikios.cooja.plugins.vanet.vehicle.Vehicle;
import org.contikios.cooja.plugins.vanet.world.World;
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

    private Visualizer visualizer = null;

    private JPanel canvas = null;
    private BufferedImage img;

    private boolean showTileReservations = false;


    public VanetVisualizerSkin() {
        img = loadFromFile("img/intersection-big.png");
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

        /* 100px equals 1m */
        double scale = 1.0 / 100.0;

        double width = img.getWidth() * scale;
        double height = img.getHeight() * scale;

        // we will center the image for now

        double centerX = width/2.0;
        double centerY = height/2.0;

        // and add an offset
        double offsetX = 3.0;
        double offsetY = 3.0;


        Point tl = visualizer.transformPositionToPixel(-centerX+offsetX, -centerY+offsetY, 0.0);
        Point br = visualizer.transformPositionToPixel(centerX+offsetX, centerY+offsetY, 0.0);

        g.drawImage(img, tl.x, tl.y, br.x-tl.x, br.y-tl.y, new ImageObserver() {
            @Override
            public boolean imageUpdate(Image img, int infoflags, int x, int y, int width, int height) {
                return false;
            }
        });

        // TODO: Use the static world of our Vanet plugin

        World world = Vanet.world;

        if (world != null) {
            drawCollisionBoundaries(g, world);
            drawWaypointsForSelection(g, world);
        }

    }


    private void render1mBackgroundGrid(Graphics g) {
        /* Background grid every 1 meters */

        double meters = 1.0;

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
                Vehicle v = world.getVehicle(mote);

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

                    float r = 0.075f;
                    drawCircle(g, p, r, c);
                }
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
                Vehicle v = world.getVehicle(mote);

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

    private void drawTileReservations(Graphics g, World world) {
        if (!showTileReservations) {
            return;
        }
        // TODO
    }

    public void setActive(Simulation simulation, Visualizer vis) {
        this.visualizer = vis;

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
        return null;
    }

    public void paintAfterMotes(Graphics g) {
    }

    public Visualizer getVisualizer() {
        return visualizer;
    }
}
