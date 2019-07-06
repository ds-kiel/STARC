package org.contikios.cooja.plugins.vanet.log.processing;

import org.contikios.cooja.plugins.vanet.log.LogEvent;

public class IdAwareProcessorDecorator implements LogEventProcessorInterface {

    LogEventProcessorInterface inner;

    public IdAwareProcessorDecorator(LogEventProcessorInterface inner) {
        this.inner = inner;
    }

    @Override
    public boolean supports(LogEvent logEvent) {
        return inner.supports(logEvent) && logEvent.getID() != null;
    }

    @Override
    public void process(LogEvent logEvent) {
        String newName = String.format("%s-%s", logEvent.getName(), logEvent.getID());
        inner.process(new LogEvent(newName, logEvent.getSimulationTime(), logEvent.getData(), logEvent.getID()));
    }

    @Override
    public void flush() {
        inner.flush();
    }
}