package com.example;

import org.springframework.util.Assert;
import reactor.core.publisher.FluxSink;

import java.util.List;

/**
 * emitter for FluxSink
 * @param <T>
 */
public class EmitterHelper<T> {
    private boolean started = false;
    private FluxSink<T> emitter;

    public void start(FluxSink<T> sink, List<T> initTasks) {
        emitter = sink;
        //emit init tasks
        for (int i = 0; i < initTasks.size(); i++) {
            emitter.next(initTasks.get(i));
        }
        started = true;
    }

    public void next(T newTask) {
        Assert.isTrue(started, "EmitterHelper还没有启动!");
        emitter.next(newTask);
    }

    public void fin() {
        Assert.isTrue(started, "EmitterHelper还没有启动!");
        emitter.complete();
        started = false;
    }
}