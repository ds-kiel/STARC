package org.contikios.cooja.plugins.vanet.log.processing;

import org.contikios.cooja.plugins.vanet.log.LogEvent;

public interface LogEventProcessorInterface {
    boolean supports(LogEvent logEvent);
    void process(LogEvent logEvent);
    void flush();
}
