package com.example;

import com.mongodb.reactivestreams.client.MongoClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;

//@Configuration
public class MongoConfiguration {

//    @Bean
//    public ReactiveMongoDatabaseFactory mongoDatabaseFactory() {
//        return new SimpleReactiveMongoDatabaseFactory(MongoClients.create("mongodb://172.16.6.112:32633"), "proxy");
//    }
//
//    @Bean
//    public ReactiveMongoTemplate reactiveMongoTemplate() {
//        return new ReactiveMongoTemplate(mongoDatabaseFactory());
//    }
}