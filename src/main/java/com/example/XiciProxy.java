package com.example;

import com.example.util.ContentCollector;
import com.example.util.EmitterHelper;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.timeout.IdleStateHandler;
import org.apache.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import reactor.ipc.netty.http.client.HttpClient;
import reactor.ipc.netty.http.client.HttpClientException;

import java.time.Duration;
import java.util.*;

/**
 * Created by dd on 2017/4/6.
 */
@Component
public class XiciProxy {

    private static Logger logger = Logger.getLogger(XiciProxy.class);

    @Autowired
    private ReactiveMongoTemplate reactiveMongoTemplate;

    public void fetch(List<String> seedUrl, int limit, int delay) {

        EmitterHelper helper = new EmitterHelper<>();

        Flux.<String>create((emitter) -> helper.start(emitter, seedUrl))
                .onBackpressureDrop(logger::error)
                .publishOn(Schedulers.elastic())
                .subscribeOn(Schedulers.elastic())
                .doOnComplete(() -> logger.info("XiciProxy fetch finished!"))
                .take(limit)
                .delayElements(Duration.ofSeconds(delay))
                .subscribe(url -> {
                    HttpClient.create()
                            .request(HttpMethod.GET, url, request ->
                                    request.context(ctx -> ctx.addHandlerFirst(new IdleStateHandler(0, 0, 20))))
                            .doOnError(HttpClientException.class, e -> logger.error("", e))
                            .filter(resp -> resp != null && resp.status() != null && resp.status().equals(HttpResponseStatus.OK))
                            .subscribe(resp -> resp.receiveContent().collect(new ContentCollector())
                                    .subscribe(httpContent -> this.process(url, httpContent, helper)));
                });
    }

    void process(String currentUrl, String htmlStr, EmitterHelper helper){
        logger.info("proc page " + currentUrl);

        Flux.fromIterable(Jsoup.parse(htmlStr).select("#ip_list > tbody > tr"))
                .filter(element -> element.select("td").size() > 0)
                .map(XiciProxy::convertToMap)
                .doOnNext(map -> saveToMongo(map, reactiveMongoTemplate))
                .subscribe();

        int totalPage = Integer.parseInt(Jsoup.parse(htmlStr).select("#body > div.pagination > :nth-last-child(2)").text());
        int currentPage = Integer.parseInt(currentUrl.substring(currentUrl.lastIndexOf("/")+1));
        if (currentPage < totalPage){
            String newUrl = currentUrl.substring(0, currentUrl.lastIndexOf("/")+1) + (currentPage+1);
            helper.next(newUrl);
            logger.info("new job: " + newUrl);
        }else {
            helper.fin();
        }
    }

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
