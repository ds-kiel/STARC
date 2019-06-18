package org.contikios.cooja.plugins.vanet.log.processing;

import org.contikios.cooja.plugins.vanet.log.LogEvent;

public class NullProcessor implements LogEventProcessorInterface {
    @Override
    public boolean supports(LogEvent logEvent) {
        return false;
    }

    @Override
    public void process(LogEvent logEvent) {

    }

    @Override
    public void finish() {

    }
}
