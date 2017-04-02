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
import reactor.ipc.netty.http.client.HttpClient;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@RunWith(SpringRunner.class)
@SpringBootTest
/**
 * 对reactor进行一些功能性使用测试
 */
public class FunctionalTests {

    @Test
    public void testFlatmap(){
        List<String> elements = Arrays.asList("1-A","2-B","3-C");

        Flux.fromIterable(elements)
                .flatMap(ele -> Flux.fromArray(ele.split("-")))
                .subscribe(System.out::println);
    }

    @Test
    public void testGroupBy(){
        List<Object> elements = Arrays.asList(1,2,3,"A","B","C");

        Flux.fromIterable(elements)
                .groupBy(data -> data instanceof Integer?"int":"str")//分组
                .doOnNext(groupedList -> {
                    //groupedList.collectList().subscribe(System.out::println);//GroupedFlux只能有一个订阅者
                    String myKey = groupedList.key();//本组的key
                    Flux.from(groupedList)
                            .doOnNext(data -> System.out.println("consuming..."+myKey+" - "+data))
                            .subscribe();
                })
                .subscribe();
    }


    @Test
    public void httpClient() throws IOException {
        HttpClient.create()
                .get("https://www.baidu.com",
                        c -> c.followRedirect()
                                .sendHeaders())
                .flatMap(r -> r.receive()
                        .retain()
                        .asString()
                        .limitRate(1))
                .reduce(String::concat)
                .log()
                .subscribe(u->System.out.println(u));
        //block for http result
        System.in.read();
    }
}
