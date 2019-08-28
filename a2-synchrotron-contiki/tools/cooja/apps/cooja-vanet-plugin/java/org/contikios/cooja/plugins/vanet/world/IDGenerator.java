package org.contikios.cooja.plugins.vanet.world;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;
import java.util.stream.*;

public class IDGenerator {

    HashSet<Integer> freed = new HashSet<>();
    Supplier<Stream<Integer>> idStreamSupplier;


    public IDGenerator(int start, Integer end) {

        AtomicInteger n = new AtomicInteger(start);
        idStreamSupplier = new Supplier<Stream<Integer>>() {
            @Override
            public Stream<Integer> get() {
                Stream<Integer> s = Stream.generate(n::getAndIncrement);
                if (end != null) {
                    s = s.limit(Math.max(end-n.get()+1,0));
                }
                return s;
            }
        };
    }

    void free(int id) {
        freed.add(id);
    }

    Integer next() {

        Stream<Integer> stream = Stream.concat(freed.stream(), idStreamSupplier.get());
        Integer val = stream.findFirst().orElse(null);

        if (val != null) {
            freed.remove(val);
            return val;
        }
        return null;
    }
}
