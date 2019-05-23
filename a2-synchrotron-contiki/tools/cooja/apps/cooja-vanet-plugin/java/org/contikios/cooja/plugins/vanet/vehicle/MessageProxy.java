package org.contikios.cooja.plugins.vanet.vehicle;

import org.contikios.cooja.Mote;
import org.contikios.cooja.interfaces.SerialPort;

import java.io.*;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Stream;

public class MessageProxy {
    private LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>();

    private DataOutputStream outputStream;

    public MessageProxy(Mote mote) {

        SerialPort motePort = (SerialPort) mote.getInterfaces().getLog();

        // input from the mote
        MoteDataObserver dataObserver = new MoteDataObserver(motePort);
        motePort.addSerialDataObserver(dataObserver);
        DataInputStream inputStream = new DataInputStream(dataObserver.getInputStream());

        // output to the mote
        MoteDataHandler dataHandler = new MoteDataHandler(motePort);
        outputStream = new DataOutputStream(dataHandler.getOutputStream());


        BufferedReader in = new BufferedReader(new InputStreamReader(inputStream));

        // we start a thread to receive messages from the serial port!
        Stream<String> stream = in.lines().filter(line -> line.startsWith("#VANET ")).map(line -> line.substring("#VANET ".length()));

        // TODO: Clear this thread!
        new Thread(new Runnable() {
            @Override
            public void run() {
                stream.forEach(q -> queue.add(q));
            }
        }).start();
    }

    public String receive() {
        return queue.poll();
    };

    public void send(String msg) {
        String msgWithNewline = "#VANET " + msg;

        if (msg.charAt(msg.length() - 1) != '\n') {
            msgWithNewline = msgWithNewline + "\n";
        }

        try {
            outputStream.write(msgWithNewline.getBytes());
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
                @Override
                public int read() throws IOException {
                    try {
                        return byteQueue.take().intValue();
                    } catch (InterruptedException e) {
                        return read();
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
