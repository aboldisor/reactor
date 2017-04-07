package com.example;

import com.example.util.EmitterHelper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.*;

@SpringBootApplication
public class ReactorApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReactorApplication.class, args);
    }

    @Component
    class Bootstrap implements CommandLineRunner {

        @Autowired
        private IpValidator ipValidator;
        @Autowired
        private XiciProxy xiciProxy;

        @Override
        public void run(String... strings) throws Exception {

            ipValidator.validate();

            List<String> seedUrl = Arrays.asList(
                "http://www.xicidaili.com/nn/1",
                "http://www.xicidaili.com/nt/1",
                "http://www.xicidaili.com/wn/1",
                "http://www.xicidaili.com/wt/1"
            );
//            xiciProxy.fetch(seedUrl, 1000, 2);

        }
    }
}
