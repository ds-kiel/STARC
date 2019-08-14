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
    private LinkedBlockingQueue<byte[]> queue = new LinkedBlockingQueue<>();

    private InputStream inputStream;
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
        inputStream = dataObserver.getInputStream();

        thread = new Thread(new Runnable() {

            @Override
            public void run() {

                ArrayList<Byte> line = new ArrayList<>();

                InputStream in = inputStream;
                final byte[] identifierBytes = "#VANET ".getBytes(StandardCharsets.ISO_8859_1);

                try {
                    while (!Thread.interrupted()) {
                        line.clear();

                        byte c = (byte) (in.read() & 0xFF);

                        boolean matching = true;

                        while (c != 0x0A) { // newline char

                            int l = line.size();

                            //check if the newline is really wanted...
                            if (matching && (l >= identifierBytes.length) || c == identifierBytes[l]) {
                                line.add(c);
                            } else {
                                matching = false;
                            }
                            c = (byte) (in.read() & 0xFF);
                        }

                        if (matching) {
                            byte[] bytes = new byte[line.size() - identifierBytes.length];

                            for (int i = identifierBytes.length; i < line.size(); ++i) {
                                bytes[i - identifierBytes.length] = line.get(i);
                            }
                            // TODO: improve copying...

                            queue.add(bytes);
                        }
                        line.clear();
                    }
                } catch (IOException e) {}
            }
        });
        thread.start();
    }


    public void clear() {
        try {
            inputStream.close();
        } catch (Exception e) {}
        thread.interrupt(); // stop the message thread
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
        InputStream inputStream;

        LinkedBlockingQueue<Byte> byteQueue = new LinkedBlockingQueue<>();

        int numBytes = 0;

        MoteDataObserver(SerialPort motePort) {
            this.motePort = motePort;

            this.inputStream = new InputStream() {
                boolean closed = false;

                @Override
                public void close() throws IOException {
                    closed = true;
                }

                @Override
                public int available() throws IOException {
                    return byteQueue.size();
                }
                @Override
                public int read() throws IOException {

                    if (closed) {
                        throw new IOException();
                    }

                    try {
                        return byteQueue.take().intValue() & 0xff;
                    } catch (InterruptedException e) {
                        throw new InterruptedIOException();
                    }
                }
            };
        }

        public InputStream getInputStream() {
            return inputStream;
        }

        @Override
        public void update(Observable obs, Object obj) {
            byteQueue.add(motePort.getLastSerialData());
            numBytes++;
        }

        public int getNumBytes() {
            return numBytes;
        }
    }
}
