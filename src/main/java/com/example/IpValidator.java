package com.example;

import com.example.util.ContentCollector;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.timeout.IdleStateHandler;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Schedulers;
import reactor.ipc.netty.http.client.HttpClient;

import java.net.*;
import java.time.Duration;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 验证ip是否可用
 */
@Component
public class IpValidator {

    private static Logger logger = Logger.getLogger(IpValidator.class);

    @Autowired
    private ReactiveMongoTemplate reactiveMongoTemplate;

    public void validate() {
        Query query = Query.query(new Criteria().orOperator(Criteria.where("state").is("grab"),
                                    Criteria.where("validate_date").lt(new Date())));
        AtomicInteger procCount = new AtomicInteger(0);

        reactiveMongoTemplate.find(query, Map.class, "ip_pool_reactor")//这里必须指明collection,否则会根据entityClass去确定,导致查不到数据
                .publishOn(Schedulers.elastic())
                .subscribeOn(Schedulers.elastic())
//                .take(1)
                .subscribe(record -> {
                    String ip = String.valueOf(record.get("ip"));
                    String port = String.valueOf(record.get("port"));
                    HttpClient.create(opt -> opt
                            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 50*1000)
//                            .proxy(ip, Integer.parseInt(port)))//直接connect代理服务器，proxy类似netty的HttpProxyHandler
                            .connect(new InetSocketAddress(ip, Integer.parseInt(port))))
                            .request(HttpMethod.GET, "http://httpbin.org/ip", request ->
                                    request.context(ctx -> ctx.addHandlerFirst(new IdleStateHandler(0,0,50)))
                            )
                            .timeout(Duration.ofSeconds(50))
                            .doOnError((e) -> {
                                record.put("state", "invalid");
                                record.put("validate_date", new Date());
                                logger.info(ip+":"+port+"...validation failed [http://httpbin.org/ip]");
                                reactiveMongoTemplate.save(record, "ip_pool_reactor").subscribe();
                                procCount.incrementAndGet();
                            })
                            .filter(resp -> resp!=null && resp.status()!=null && resp.status().equals(HttpResponseStatus.OK))
                            .subscribe(res -> {
                                procCount.incrementAndGet();
                                Date s = new Date();
                                HttpClient.create(opt -> opt.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 50*1000)
                                                .connect(ip, Integer.parseInt(port)))
                                        .request(HttpMethod.GET, "https://www.baidu.com", request ->
                                                request.context(ctx -> ctx.addHandlerFirst(new IdleStateHandler(0,0,50))))
                                        .doOnError((e) -> {
                                            record.put("state", "invalid");
                                            record.put("validate_date", new Date());
                                            logger.info(ip+":"+port+"...validation failed [http://www.baidu.com]");
                                            reactiveMongoTemplate.save(record, "ip_pool_reactor").subscribe();
                                        })
                                        .timeout(Duration.ofSeconds(50))
                                        .filter(resp -> resp!=null && resp.status()!=null && resp.status().equals(HttpResponseStatus.OK))
                                        .subscribe(resp -> {
                                            record.put("state", "valid");
                                            record.put("timing", new Date().getTime() - s.getTime());
                                            record.put("validate_date", new Date());
                                            logger.info(ip+":"+port+"...valid");
                                            reactiveMongoTemplate.save(record, "ip_pool_reactor").subscribe();
                                        });
                            });
                });


        while (true){
            System.out.println(procCount.get());
            try {
                Thread.sleep(60*1000);
            } catch (InterruptedException e) {
            }
        }
    }

}
