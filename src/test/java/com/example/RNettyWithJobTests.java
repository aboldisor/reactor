package com.example;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.timeout.IdleStateHandler;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.stereotype.Component;
import org.springframework.test.context.junit4.SpringRunner;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Schedulers;
import reactor.ipc.netty.http.client.HttpClient;
import reactor.ipc.netty.http.client.HttpClientRequest;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

@RunWith(SpringRunner.class)
@SpringBootTest
/**
 * 基本达到目的
 * 注意 [注意] [maybe bug] 标签
 */
public class RNettyWithJobTests {

    @Test
    public void run() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        List<String> seedUrl = Arrays.asList(
                "http://www.xicidaili.com/wt/1"
        );

        EmitterHelper helper = new EmitterHelper<>();

        Flux.<String>create((emitter) -> helper.start(emitter, seedUrl))
                .onBackpressureDrop(System.err::println)
                .publishOn(Schedulers.elastic())//[注意]在使用emitter情况下要加这个,否则publisher会卡住,导致subscriber拿不到任务
                .subscribeOn(Schedulers.elastic())
                .doOnComplete(()->{
                    System.out.println("finished!");
                    latch.countDown();
                })
//                .take(6)
                .subscribe(url -> {
                    HttpClient.create()
                            //.get(url, request -> this.playWithReq(request)) //另一种写法
                            .request(HttpMethod.GET, url, request -> this.playWithReq(request))
//                            .log()
                            .filter(resp -> resp!=null && resp.status()!=null && resp.status().equals(HttpResponseStatus.OK))
                            .subscribe(resp -> resp.receiveContent().collect(new ContentCollector()).subscribe(httpContent -> this.process(url, httpContent, helper)) );
                            //.subscribe(resp -> resp.receive().collect(new ContentCollector2()).subscribe(httpContent -> this.process(url, httpContent,helper)) );
                            //[maybe bug] 用上面的resp.receive()的方式, ByteBuf合并会报引用计数错误
                });

        //blocking
        latch.await();
    }

    HttpClientRequest playWithReq(HttpClientRequest request){
        //add header
//        request.requestHeaders()
//                .add("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/57.0.2987.110 Safari/537.36")
//                //.add("Cookie", ClientCookieEncoder.LAX.encode("key","value"));//另一种写法
//                .add("Cookie", "key=value; key2=val2");

        //request.cookies().clear();// [maybe bug] request.cookies()==null
        //request.addCookie((ClientCookieDecoder.LAX.decode("_free_proxy_session=999")));

        request.context(ctx -> ctx.addHandlerFirst(new IdleStateHandler(0,0,5)));
        return request;
    }

    class ContentCollector2 implements Collector<ByteBuf, CompositeByteBuf, String> {

        @Override
        public Supplier<CompositeByteBuf> supplier() {
           return () -> {return Unpooled.compositeBuffer();};
        }

        @Override
        public BiConsumer<CompositeByteBuf, ByteBuf> accumulator() {
           return (comp, buf)->{comp.addComponent( buf);};
        }

        @Override
        public BinaryOperator<CompositeByteBuf> combiner() {
           return (comp1, comp2) -> comp1.addComponents(comp2);
        }

        @Override
        public Function<CompositeByteBuf, String> finisher() {
           return (comp) -> comp.retain().toString(Charset.defaultCharset());
        }


        @Override
        public Set<Characteristics> characteristics() {
           return Collections.singleton(Characteristics.CONCURRENT);
       }
   }

    class ContentCollector implements Collector<HttpContent, StringBuilder, String> {

        @Override
        public Supplier<StringBuilder> supplier() {
            return () -> new StringBuilder();
        }

        @Override
        public BiConsumer<StringBuilder, HttpContent> accumulator() {
            return (sb, httpContent) -> sb.append(httpContent.content().toString(Charset.forName("UTF-8")));
        }

        @Override
        public BinaryOperator<StringBuilder> combiner() {
            return (comb1, comb2) -> comb1.append(comb2);
        }

        @Override
        public Function<StringBuilder, String> finisher() {
            return sb -> sb.toString();
        }

        @Override
        public Set<Characteristics> characteristics() {
            return Collections.singleton(Characteristics.CONCURRENT);
        }
    }

    void process(String currentUrl, String content, EmitterHelper helper){
//        System.out.println(content);

        int currentPage = Integer.parseInt(currentUrl.substring(currentUrl.lastIndexOf("/")+1));
        System.out.println("processing page " + currentPage);

        //process content
        if (currentPage < 20){
            String newUrl = currentUrl.substring(0, currentUrl.lastIndexOf("/")+1) + (currentPage+1);
            System.out.println("new job: " + newUrl);
            helper.next(newUrl);
        }else {
            helper.fin();
        }
    }

}
