package com.example;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.timeout.IdleStateHandler;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Schedulers;
import reactor.ipc.netty.http.client.HttpClient;

import java.net.*;
import java.time.Duration;
import java.util.Date;
import java.util.Map;

/**
 * 验证ip是否可用
 */
@Component
public class IpValidator {

    private static Logger logger = Logger.getLogger(IpValidator.class.getName());

    @Autowired
    private ReactiveMongoTemplate reactiveMongoTemplate;

    public void validate() {
        Query query = Query.query(new Criteria().orOperator(Criteria.where("state").is("grab"),
                                    Criteria.where("validate_date").lt(new Date())));

        reactiveMongoTemplate.find(query, Map.class, "ip_pool")//这里必须指明collection,否则会根据entityClass去确定,导致查不到数据
                .publishOn(Schedulers.elastic())
                .subscribeOn(Schedulers.elastic())
                .take(1)
                .subscribe(record -> {
                    String ip = String.valueOf(record.get("ip"));
                    String port = String.valueOf(record.get("port"));
                    HttpClient.create(opt -> opt
//                            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 50*1000)
//                            .proxy(ip, Integer.parseInt(port)))
//                            .proxy(new InetSocketAddress(ip, Integer.parseInt(port))))
                            .connect(new InetSocketAddress("120.52.72.58", 80)))
                            .request(HttpMethod.GET, "http://httpbin.org/ip", request ->
                                    request.context(ctx -> ctx.addHandlerFirst(new IdleStateHandler(0,0,50)))
                            )
//                            .timeout(Duration.ofSeconds(50*1000))
                            .doOnError((e) -> {
                                e.printStackTrace();
                                record.put("state", "invalid");
                                record.put("validate_date", new Date());
                                System.out.println(ip+":"+port+"...invalid1");
                                reactiveMongoTemplate.save(record, "ip_pool_reactor").subscribe();
                            })
                            .filter(resp -> resp!=null && resp.status()!=null && resp.status().equals(HttpResponseStatus.OK))
                            .subscribe(res -> {
                                res.receiveContent().collect(new ContentCollector()).subscribe(System.out::println);
                                Date s = new Date();
                                HttpClient.create(opt -> opt.connect(ip, Integer.parseInt(port)))
                                        .request(HttpMethod.GET, "http://www.baidu.com", request ->
                                                request.context(ctx -> ctx.addHandlerFirst(new IdleStateHandler(0,0,20))))
                                        .doOnError((e) -> {
                                            record.put("state", "invalid");
                                            record.put("validate_date", new Date());
                                            System.out.println(ip+":"+port+"...invalid2");
                                            reactiveMongoTemplate.save(record, "ip_pool_reactor").subscribe();
                                        })
                                        .filter(resp -> resp!=null && resp.status()!=null && resp.status().equals(HttpResponseStatus.OK))
                                        .subscribe(resp -> {
                                            record.put("state", "valid");
                                            record.put("timing", new Date().getTime() - s.getTime());
                                            record.put("validate_date", new Date());
                                            System.out.println(ip+":"+port+"...valid");
                                            reactiveMongoTemplate.save(record, "ip_pool_reactor").subscribe();
                                        });
                            });
                });
    }

}
