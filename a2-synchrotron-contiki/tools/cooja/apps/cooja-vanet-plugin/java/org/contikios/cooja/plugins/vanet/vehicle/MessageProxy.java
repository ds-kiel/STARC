package org.contikios.cooja.plugins.vanet.vehicle;

import org.contikios.cooja.Mote;
import org.contikios.cooja.interfaces.Log;
import org.contikios.cooja.interfaces.SerialPort;

import java.io.*;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Stream;

public class MessageProxy {
    private LinkedBlockingQueue<byte[]> queue;

    private OutputStream outputStream;

    private Thread thread;

    public MessageProxy(Mote mote) {

        SerialPort motePort = (SerialPort) mote.getInterfaces().getLog();

        // input from the mote
        MoteDataObserver dataObserver = new MoteDataObserver(motePort);
        motePort.addSerialDataObserver(dataObserver);

        // output to the mote
        MoteDataHandler dataHandler = new MoteDataHandler(motePort);
        outputStream = dataHandler.getOutputStream();
        queue = dataObserver.getLineQueue();
    }

    private byte[] encode(byte[] source) {
        ArrayList<Byte> temp = new ArrayList<>();

        for(int i = 0; i < source.length; ++i) {
            byte c = source[i];
            if (c == 0x11) {
                temp.add((byte)0x11);
                temp.add((byte)0x11);
            } else if (c == 0x0A) {  // \n
                temp.add((byte)0x11);
                temp.add((byte)0x12);
            } else if (c == 0x0D) {// \r
                temp.add((byte)0x11);
                temp.add((byte)0x13);
            } else if (c == 0x00) {// \0
                temp.add((byte)0x11);
                temp.add((byte)0x14);
            } else {
                temp.add(c);
            }
        }

        byte[] bytes = new byte[temp.size()];

        // TODO: improve copying...
        for(int i = 0; i < temp.size(); ++i) {
            bytes[i] = temp.get(i);
        }

        return bytes;
    }

    private byte[] decode(byte[] source) {

        ArrayList<Byte> temp = new ArrayList<>();

        for(int i = 0; i < source.length; ++i) {
            byte c = source[i];
            if (c == 0x11) {
                i++;
                if (i < source.length) {
                    c = source[i];

                    if (c == 0x11) {
                        // just add this
                    } else if(c == 0x12) {
                        c = 0x0A;
                    } else if(c == 0x13) {
                        c = 0x0D;
                    } else if(c == 0x14) {
                        c = 0x00;
                    }
                    temp.add(c);
                }
            } else {
                temp.add(c);
            }
        }

        byte[] bytes = new byte[temp.size()];

        // ugly byte arr copying...
        for(int i = 0; i < temp.size(); ++i) {
            bytes[i] = temp.get(i);
        }
        return bytes;
    }

    public byte[] receive() {

        byte[] data = queue.poll();

        if (data == null) {
            return null;
        } else {
            return decode(data);
        }
    };

    public void send(byte[] data) {

        byte[] encoded = encode(data);

        try {
            outputStream.write(encoded);
            outputStream.write((byte) '\n');
            outputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /* Based on SerialSocketServer code */
    private class MoteDataHandler {

        SerialPort motePort;
        OutputStream outputStream;

        LinkedBlockingQueue<Byte> byteQueue = new LinkedBlockingQueue<>();

        int numBytes = 0;

        MoteDataHandler(SerialPort motePort) {
            this.motePort = motePort;
            this.outputStream = new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    motePort.writeByte((byte)b);
                    numBytes++;
                }
            };
        }

        public int getNumBytes() {
            return numBytes;
        }

        public OutputStream getOutputStream() {
            return outputStream;
        }
    }

    private class MoteDataObserver implements Observer {

        SerialPort motePort;
        LinkedBlockingQueue<byte[]> lineQueue = new LinkedBlockingQueue<>();;

        ArrayList<Byte> byteBuffer = new ArrayList<>();

        int numBytes = 0;

        MoteDataObserver(SerialPort motePort) {
            this.motePort = motePort;
        }

        public LinkedBlockingQueue<byte[]> getLineQueue() {
            return lineQueue;
        }

        @Override
        public void update(Observable obs, Object obj) {

            byte b = (byte) (motePort.getLastSerialData() & 0xFF);

            if (b != 0x0A) {
                // we add all bytes except newlines
                byteBuffer.add(b);
            } else {

                final byte[] identifierBytes = "#VANET ".getBytes(StandardCharsets.ISO_8859_1);

                if (byteBuffer.size() >= identifierBytes.length) {

                    boolean matching = true;

                    for(int i = 0; i < identifierBytes.length; ++i) {
                        if (identifierBytes[i] != byteBuffer.get(i)) {
                            matching = false;
                            break;
                        }
                    }

                    if (matching) {

                        byte[] line = new byte[byteBuffer.size()-identifierBytes.length];

                        for(int i = 0; i < byteBuffer.size()-identifierBytes.length; ++i) {
                            line[i] = byteBuffer.get(i+identifierBytes.length);
                        }
                        lineQueue.add(line);
                    }
                }

                byteBuffer.clear();
            }
            numBytes++;
        }

        public int getNumBytes() {
            return numBytes;
        }
    }
}
