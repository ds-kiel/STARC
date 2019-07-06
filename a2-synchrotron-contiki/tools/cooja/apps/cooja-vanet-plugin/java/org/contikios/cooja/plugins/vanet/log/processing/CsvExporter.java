package org.contikios.cooja.plugins.vanet.log.processing;

import org.contikios.cooja.plugins.vanet.log.LogEvent;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class CsvExporter implements LogEventProcessorInterface {


    private File basePath;

    private Map<String, Writer> writerCache = new HashMap<>();

    public CsvExporter(String basePath) {
        this.basePath = new File(basePath);
        this.basePath.mkdirs();
    }

    @Override
    public boolean supports(LogEvent logEvent) {
        return true;
    }

    @Override
    public void process(LogEvent logEvent) {

        String fileName = logEvent.getName() + ".csv";
        try  {

            Writer writer = null;

            if (writerCache.containsKey(fileName)) {
                writer = writerCache.get(fileName);
            } else {
                File file = new File(basePath, fileName);
                writer = new BufferedWriter(new FileWriter(file, true));
                writerCache.put(fileName, writer);
            }

            String line = String.format("%d, %s\n", logEvent.getSimulationTime(), logEvent.getData());
            writer.write(line);
            writer.flush(); //TODO: This is a slow down!
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }


    @Override
    public void flush() {
        writerCache.values().forEach(
                w -> {
                    try {
                        w.close();
                    } catch (IOException e) {}
                }
        );
        writerCache.clear();
    }
}