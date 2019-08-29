package org.contikios.cooja.plugins.vanet.log;

import org.contikios.cooja.plugins.vanet.log.processing.CsvExporter;
import org.contikios.cooja.plugins.vanet.log.processing.LogEventProcessorInterface;
import org.contikios.cooja.plugins.vanet.log.processing.IdAwareProcessorDecorator;

public class Logger {

    private static org.apache.log4j.Logger apacheLogger = org.apache.log4j.Logger.getLogger(Logger.class);

    private LogEventProcessorInterface[] logEventProcessors;

    private static Logger loggerInstance;

    private static String logDir;


    public static void setLogDir(String logDir) {
        Logger.logDir = logDir;
        loggerInstance = null; // reset logger! TODO: This is not the nices way
    }

    public Logger() {
        loggerInstance = this;

        if (Logger.logDir != null && Logger.logDir.length() > 0) {
            logEventProcessors = new LogEventProcessorInterface[2];
            logEventProcessors[0] = new IdAwareProcessorDecorator(new CsvExporter(Logger.logDir));
            logEventProcessors[1] = new CsvExporter(Logger.logDir); // log id unaware data
        } else {
            logEventProcessors = new LogEventProcessorInterface[0];
        }
    }

    public static Logger getInstance() {
        if (loggerInstance == null) {
            loggerInstance = new Logger();
        }
        return loggerInstance;
    }

    public static void event(String name, long ms, String data, String id) {
        log(new LogEvent(name, ms, data, id));
    }

    public static void log(LogEvent logEvent) {
        Logger logger = getInstance();
        for (LogEventProcessorInterface logEventProcessor: logger.logEventProcessors) {
            if (logEventProcessor.supports(logEvent)) {
                logEventProcessor.process(logEvent);
                break; // only handle one for now ;)
            }
        }
    }

    public static void flush() {
        Logger logger = getInstance();
        for (LogEventProcessorInterface logEventProcessor: logger.logEventProcessors) {
            logEventProcessor.flush();
        }
    }
}