package com.example;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Arrays;

@RunWith(SpringRunner.class)
@SpringBootTest
/**
 * 这个主要是想测试,在一个list每个元素都进行异步处理,能否最后再collect回来一个处理好的list
 */
public class JobTests {

    Iterable<String> list;

    @Before
    public void setup() {
        list = Arrays.asList("1", "2", "3", "4", "5", "6", "7");
    }


    @Test
    public void bad() throws Exception {
        Flux.fromIterable(list)
                .subscribeOn(Schedulers.elastic())
                .doOnNext(doc -> {
                    Mono.just(doc)
                            .subscribe(d -> {
                                d = "-subProcess-lv1-" + d;
                            });
                })
                .collectList()//在这里收集到的是还未处理完的结果
                .subscribe(System.out::println);
    }

    @Test
    public void good() throws Exception {

        //这个需要写在前面,先保存em
        Flux.create(e -> {
            em = e;
        })
        .collectList()//有这个一定会等fin事件
        .subscribe(System.out::println);

        Flux.fromIterable(list)
                .subscribeOn(Schedulers.elastic())
                .doOnNext(doc -> {
                    Mono.just(doc)
                            .map(d -> {//process
                                return "-subProcess-lv1-" + d;
                            })
//                                .doOnNext(System.out::println)
                            .doOnNext(d -> {
                                fire(d);
                                if (d.equals("-subProcess-lv1-5"))//这个停止条件有些理想化,现实可能没有显示的停止条件
                                    fin();
                            })
                            .subscribe();

                })
                .subscribe();


    }

    private void fin() {
        em.complete();
    }

    FluxSink em;

    private void fire(String d) {
        em.next(d);
    }


    boolean oneShot = true;
    @Test
    /**
     * 其实使用emitter的方式不一定要发射onComplete, 事件没有了就会停止
     */
    public void testOneShot(){
        Flux.create((emt) -> {em = emt;emt.next("go");})
                .doOnNext((e)-> {if(oneShot) em.next("die"); oneShot = false;})
                .subscribe(System.out::println);
    }
}
