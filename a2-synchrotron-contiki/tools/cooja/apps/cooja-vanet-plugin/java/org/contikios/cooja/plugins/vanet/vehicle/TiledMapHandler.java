package org.contikios.cooja.plugins.vanet.vehicle;

import org.contikios.cooja.plugins.vanet.world.physics.Vector2D;

import java.util.HashSet;

public class TiledMapHandler {

    private int width = 0;
    private int height = 0;

    private double tileWidth = 0;
    private double tileHeight = 0;

    public TiledMapHandler(int width, int height, double tileWidth, double tileHeight) {
        this.width = width;
        this.height = height;
        this.tileWidth = tileWidth;
        this.tileHeight = tileHeight;
    }

    public int getNumTiles() {
        return width*height;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }


    PathHelper createPathHelper() {
        return new PathHelper(this);
    }

    public class PathHelper {

        private TiledMapHandler mapHandler;

        private HashSet<Integer> reserved = new HashSet<>();

        public PathHelper(TiledMapHandler mapHandler) {
            this.mapHandler = mapHandler;
        }

        public void reservePos(Vector2D pos) {
            int index = mapHandler.posToIndex(pos);
            reserved.add(index);
        }

        public byte[] getByteIndices() {

            int[] temp = reserved.stream().mapToInt(Integer::intValue).toArray();;

            byte[] bytes = new byte[temp.length];

            // ugly byte arr copying...
            for(int i = 0; i < temp.length; ++i) {
                bytes[i] = (byte) (temp[i]&0xFF);
            }
            return bytes;
        }
    }

    private int posToIndex(Vector2D pos) {
        int x = Math.max(0, Math.min((int) (pos.getX() / tileWidth), width));
        int y = Math.max(0, Math.min((int) (pos.getY() / tileHeight), height));
        return y*width+x;
    }
}
