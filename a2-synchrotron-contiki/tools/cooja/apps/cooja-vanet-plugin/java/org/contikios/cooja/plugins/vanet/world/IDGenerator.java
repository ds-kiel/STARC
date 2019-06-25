package org.contikios.cooja.plugins.vanet.world;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;
import java.util.stream.*;

public class IDGenerator {

    HashSet<Integer> freed = new HashSet<>();
    HashSet<Integer> used;
    Supplier<Stream<Integer>> idStreamSupplier;

    public IDGenerator(int start, Integer end, Collection<Integer> used) {
        this.used = new HashSet<>(used);


        AtomicInteger n = new AtomicInteger(start);
        idStreamSupplier = new Supplier<Stream<Integer>>() {
            @Override
            public Stream<Integer> get() {
                Stream<Integer> s = Stream.generate(n::getAndIncrement);
                if (end != null) {
                    s = s.limit(end-start+1);
                }
                return s;
            }
        };
    }

    public IDGenerator(int start, Integer endExclusive) {
        this(start, endExclusive, new ArrayList<>());
    }

    void free(int id) {
        freed.add(id);
        used.remove(id);
    }

    Integer next() {
        Stream<Integer> stream = Stream.concat(freed.stream(), idStreamSupplier.get());
        Integer val = stream
                        .filter(f -> !used.contains(f))
                        .findFirst()
                        .orElse(null);
        if (val != null) {
            freed.remove(val);
            used.add(val);
        }

        return val;
    }
}
