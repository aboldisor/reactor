package com.example;

import com.mongodb.reactivestreams.client.MongoClients;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.timeout.IdleStateHandler;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.ReplayProcessor;
import reactor.core.publisher.WorkQueueProcessor;
import reactor.core.scheduler.Schedulers;
import reactor.ipc.netty.http.client.HttpClient;
import reactor.ipc.netty.http.client.HttpClientException;
import reactor.ipc.netty.http.client.HttpClientResponse;
import reactor.ipc.netty.http.server.HttpServer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;

@SpringBootApplication
public class ReactorApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReactorApplication.class, args);
    }

    @Component
    class Bootstrap implements CommandLineRunner {
        @Autowired
        private ReactiveMongoTemplate reactiveMongoTemplate;

        @Autowired
        private IpValidator ipValidator;

        @Override
        public void run(String... strings) throws Exception {

            ipValidator.validate();

//            List<String> seedUrl = Arrays.asList(
////                    "http://www.xicidaili.com/nn/1",
////                    "http://www.xicidaili.com/nt/1",
////                    "http://www.xicidaili.com/wn/1",
//                    "http://www.xicidaili.com/wt/1"
//            );
//
//            EmitterHelper helper = new EmitterHelper<>();
//
//            Flux.<String>create((emitter) -> helper.start(emitter, seedUrl))
//                    .onBackpressureDrop(System.err::println)
//                    .publishOn(Schedulers.elastic())
//                    .subscribeOn(Schedulers.elastic())
//                    .doOnComplete(()->System.out.println("finished!"))
//                    .delayElements(Duration.ofSeconds(1))
//                    .subscribe(url -> {
//                        HttpClient.create()
//                                .request(HttpMethod.GET, url, request ->
//                                        request.context(ctx -> ctx.addHandlerFirst(new IdleStateHandler(0,0,5))))
//                                .doOnError(HttpClientException.class, e -> e.printStackTrace())
//                                .filter(resp -> resp!=null && resp.status()!=null && resp.status().equals(HttpResponseStatus.OK))
//                                .subscribe(resp -> resp.receiveContent().collect(new ContentCollector())
//                                        .subscribe(httpContent -> this.process(url, httpContent, helper)) );
//                    });
        }

        void process(String currentUrl, String htmlStr, EmitterHelper helper){
            System.out.println("proc page " + currentUrl);

            Flux.fromIterable(Jsoup.parse(htmlStr).select("#ip_list > tbody > tr"))
                    .filter(element -> element.select("td").size() > 0)
                    .map(XiciDaili::convertToMap)
                    .doOnNext(map -> XiciDaili.saveToMongo(map, reactiveMongoTemplate))
                    .subscribe();

            int totalPage = Integer.parseInt(Jsoup.parse(htmlStr).select("#body > div.pagination > :nth-last-child(2)").text());
            int currentPage = Integer.parseInt(currentUrl.substring(currentUrl.lastIndexOf("/")+1));
            if (currentPage < totalPage){
                String newUrl = currentUrl.substring(0, currentUrl.lastIndexOf("/")+1) + (currentPage+1);
                System.out.println("new job: " + newUrl);
                helper.next(newUrl);
            }else {
                helper.fin();
            }
        }

    }


    static class XiciDaili {
        public static Map<String, Object> convertToMap(Element element) {
            Map<String, Object> map = new HashMap<>();
            String ip = element.child(1).text();
            String port = element.child(2).text();
            String addr = element.child(3).text();
            String type = element.child(4).text();
            map.put("_id", ip + ":" + port);
            map.put("ip", ip);
            map.put("port", port);
            map.put("addr", addr);
            map.put("type", type);
            map.put("state", "grab");
            map.put("source", "http://www.xicidaili.com/");
            map.put("fetch_date", new Date());
            return map;
        }

        public static void insertMany(List<Map<String, Object>> list, ReactiveMongoTemplate mongoTemplate) {
            mongoTemplate.insert(list, "test").subscribe();

//            mongoTemplate.find(Query.query(Criteria.where("s").))
        }

        public static void saveToMongo(Map<String, Object> map, ReactiveMongoTemplate mongoTemplate) {
//            Document document = new Document();
//            document.putAll(map);
//            mongoTemplate.upsert(Query.query(Criteria.where("_id").is(map.get("_id"))),
//                    Update.fromDocument(document), "test").subscribe();
            mongoTemplate.save(map, "ip_pool_reactor").subscribe();
        }
    }
}
