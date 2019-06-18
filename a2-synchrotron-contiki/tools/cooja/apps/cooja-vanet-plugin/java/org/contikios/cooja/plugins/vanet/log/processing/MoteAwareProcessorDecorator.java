package org.contikios.cooja.plugins.vanet.log.processing;

import org.contikios.cooja.plugins.vanet.log.LogEvent;

public class MoteAwareProcessorDecorator implements LogEventProcessorInterface {

    LogEventProcessorInterface inner;

    public MoteAwareProcessorDecorator(LogEventProcessorInterface inner) {
        this.inner = inner;
    }

    @Override
    public boolean supports(LogEvent logEvent) {
        return inner.supports(logEvent) && logEvent.getModeID() != null;
    }

    @Override
    public void process(LogEvent logEvent) {
        String newName = String.format("%s-%03d", logEvent.getName(), logEvent.getModeID());
        inner.process(new LogEvent(newName, logEvent.getSimulationTime(), logEvent.getData(), logEvent.getModeID()));
    }

    @Override
    public void finish() {
        inner.finish();
    }
}