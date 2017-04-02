package com.example;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.publisher.WorkQueueProcessor;
import reactor.core.scheduler.Schedulers;
import reactor.util.concurrent.WaitStrategy;

import java.util.List;
import java.util.Queue;
import java.util.Vector;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAccumulator;

@RunWith(SpringRunner.class)
@SpringBootTest
/**
 * 测试FluxSink作为事件源,手工发射事件
 */
public class FluxSinkTests {

    private LongAccumulator maxRingBufferPending;
    private WorkQueueProcessor<Object> processor;
    private ExecutorService producerExecutor;
    private AtomicLong droppedCount;
    private AtomicLong finishCount;

    private List initTasks;
    private Queue taskQueue;

    @Before
    public void setup() {
        maxRingBufferPending =  new LongAccumulator(Long::max, Long.MIN_VALUE);
        droppedCount = new AtomicLong(0);
        finishCount = new AtomicLong(0);
        producerExecutor = Executors.newSingleThreadExecutor();

        initTasks = new Vector<>();
        initTasks.add("http://www.xicidaili.com/nn/1");
        initTasks.add("http://www.xicidaili.com/nn/2");
        initTasks.add("http://www.xicidaili.com/nn/3");
        initTasks.add("http://www.xicidaili.com/nn/4");

        taskQueue = new ConcurrentLinkedQueue();
        processor = WorkQueueProcessor.create("test-processor", (int) Math.pow(2, 8), WaitStrategy.parking());
    }


    @Test
    public void v1() throws Exception {
        Flux.create((emitter) -> producerExecutor.execute(EmitterH.create(emitter, initTasks, taskQueue)))
                .onBackpressureDrop(this::incrementDroppedMessagesCounter)
                .subscribe(d -> {
                    Mono.just(d)
                            .subscribeOn(Schedulers.elastic())//这个要显示的指定
                            .doOnNext(this::complicatedCalculation)
                            .doOnNext((s)-> {
                                int n = (int) finishCount.getAndIncrement();
                                System.out.println("processing "+s+", generating "+n);
                                EmitterH.repost(taskQueue, "new "+ n);
                            })
                            .subscribe();
                });

        waitForProducerFinish();

        System.out.println("finish count "+ finishCount.get());
        Assert.assertEquals(0, droppedCount.get());
    }


    @Test
    public void v2() throws Exception {
        //其实直接用flux即可,用processor只是多一些控制方法
        processor.create((emitter) -> producerExecutor.execute(EmitterH.create(emitter, initTasks, taskQueue)))
                .onBackpressureDrop(this::incrementDroppedMessagesCounter)
                .subscribe(d -> {
                    Mono.just(d)
                            .subscribeOn(Schedulers.elastic())
                            .doOnNext(this::complicatedCalculation)
                            .doOnNext((s)-> {
                                int n = (int) finishCount.getAndIncrement();
                                System.out.println("processing "+s+", generating "+n);
                                EmitterH.repost(taskQueue, "new "+ n);
                            })
                            .subscribe();
                });

        waitForProducerFinish();

        System.out.println("Max ring buffer pending: " + maxRingBufferPending.get());
        System.out.println("finish count "+ finishCount.get());
        Assert.assertEquals(0, droppedCount.get());
    }

	@Test
	public void v3() throws Exception {
        Flux.create((emitter) -> producerExecutor.execute(EmitterH.create(emitter, initTasks, taskQueue)))
                .onBackpressureDrop(this::incrementDroppedMessagesCounter)
                .subscribe(processor);


        //processor.parallel(4)//与Schedulers.elastic()类似
        Flux.from(processor)
                .subscribeOn(Schedulers.elastic())
                //.doOnNext(this::complicatedCalculation)//如果在这里有耗时操作,会直接影响订阅者从processor取任务的效率,造成每秒只取1个
                .doOnNext((d)-> {
                    System.out.println("可用空位: "+processor.getPending());
                    Mono.just(d)
                            .subscribeOn(Schedulers.elastic())
                            .doOnNext(this::complicatedCalculation)
                            .doOnNext((s)-> {
                                int n = (int) finishCount.getAndIncrement();
                                System.out.println("processing "+s+", generating "+n);
                                EmitterH.repost(taskQueue, "new "+ n);
                            })
                            .subscribe();
                })
                .subscribe();

        waitForProducerFinish();

        System.out.println("Max ring buffer pending: " + maxRingBufferPending.get());
        System.out.println("finish count "+ finishCount.get());
        Assert.assertEquals(0, droppedCount.get());
    }

    /**
     * 此版本的使用了一个单独的runnable线程作为emitter,并且使用taskQueue配合。
     * 其实是有些重的
     */
    static class EmitterH {
        public static Runnable create(final FluxSink emitter, List initTasks, Queue bufferQ){
            return ()->{
                //emit init tasks
                for (int i = 0; i < initTasks.size(); i++) {
                    emitter.next(initTasks.get(i));
                }
                //emitter-loop
                while (true){
                    while (bufferQ.peek() != null) {
                        emitter.next(bufferQ.poll());
                    }
//                    try {
//                        Thread.sleep((long) (0.1*1000));
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
                }
            };
        }
        public static void repost(Queue bufferQ, Object newTask){
            bufferQ.add(newTask);
        }
    }


    private void waitForProducerFinish() throws InterruptedException {
        producerExecutor.shutdown();
        producerExecutor.awaitTermination(20, TimeUnit.SECONDS);
    }

    private Object complicatedCalculation(Object value) {
        maxRingBufferPending.accumulate(processor.getPending());
        try {
            Thread.sleep(1*1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return value;
    }

    private void incrementDroppedMessagesCounter(Object dropped) {
        System.out.println("Dropped: " + dropped+" , now pending: "+processor.getPending());
        droppedCount.incrementAndGet();
    }
}
