package com.example;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.jsoup.Jsoup;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.junit4.SpringRunner;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.ipc.netty.http.client.HttpClient;
import reactor.ipc.netty.http.client.HttpClientResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Vector;
import java.util.stream.Collectors;

/**
 * 简单的尝试,没有结合jobTest的emitter
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class ReactorNettyTests {

    @Test
    public void test() throws IOException {
        List<String> initTasks = new Vector<>();
        initTasks.add("http://www.xicidaili.com/nn/1");
        initTasks.add("http://www.xicidaili.com/nn/2");

        Flux.fromIterable(initTasks)
                .subscribe(url -> {
                    SomeHelper.buildRequest(url)
                            .filter(tester -> tester.status().compareTo(HttpResponseStatus.OK) == 0)
                            .subscribe(resp -> SomeHelper.process(url, resp));
                });

        System.in.read();
    }

    static class SomeHelper {
        public static Mono<HttpClientResponse> buildRequest(String url) {
            return HttpClient.create()
                    .request(HttpMethod.GET, url, httpClientRequest ->
                            httpClientRequest.addHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:40.0) Gecko/20100101 Firefox/40.1")
                    );
        }

        public static void process(String url, HttpClientResponse resp) {
            resp.receive().asString(StandardCharsets.UTF_8)
                    //直接doOnNext或subscribe是不行的,receive的string是一段一段给的,必须先collect整个str再用
                    //.doOnNext(s -> System.out.println(s)).subscribe();
                    .collect(Collectors.joining(""))
                    .doOnNext(html -> {
                        Flux.fromIterable(Jsoup.parse(html).select("#ip_list > tbody > tr"))
                                .filter(element -> element.select("td").size() > 0)
                                //.buffer(10)
                                .subscribe();
                    })
                    .doOnNext(html -> {
                        System.out.println(url + "...ok");
                    })
                    //这样加是不行的,因为iterable已经发射了结束事件,可能可以用generate方法
                    //.doOnNext(s -> initTasks.add("http://www.xicidaili.com/nn/2"))//add new task
                    .subscribe();
        }

    }
}
