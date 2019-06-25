package org.contikios.cooja.plugins.vanet.log;

import org.contikios.cooja.plugins.vanet.log.processing.CsvExporter;
import org.contikios.cooja.plugins.vanet.log.processing.LogEventProcessorInterface;
import org.contikios.cooja.plugins.vanet.log.processing.IdAwareProcessorDecorator;

public class Logger {

    private static org.apache.log4j.Logger apacheLogger = org.apache.log4j.Logger.getLogger(Logger.class);

    private LogEventProcessorInterface[] logEventProcessors;

    private static Logger loggerInstance;

    public Logger() {
        loggerInstance = this;

        logEventProcessors = new LogEventProcessorInterface[1];
        // TODO: Use other path here. Maybe make it configurable?
        // TODO: Add the processors at some other place...
        logEventProcessors[0] = new IdAwareProcessorDecorator(new CsvExporter("/Users/rathje/Desktop/export/stats"));
    }

    public static Logger getInstance() {
        if (loggerInstance == null) {
            loggerInstance = new Logger();
        }
        return loggerInstance;
    }

    public static void event(String name, long ms, String data, Integer id) {
        log(new LogEvent(name, ms, data, id));
    }

    public static void log(LogEvent logEvent) {
        Logger logger = getInstance();
        for (LogEventProcessorInterface logEventProcessor: logger.logEventProcessors) {
            if (logEventProcessor.supports(logEvent)) {
                logEventProcessor.process(logEvent);
            }
        }
    }

    public void flush() {
        Logger logger = getInstance();
        for (LogEventProcessorInterface logEventProcessor: logger.logEventProcessors) {
            logEventProcessor.flush();
        }
    }
}