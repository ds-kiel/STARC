package org.contikios.cooja.plugins.vanet.log;

public class Logger {

    private static org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(Logger.class);

    public static void event(String event, String data) {
        // NOOP for now
        logger.info("Got Event: " + event + " (" + data + ")");
    }
}