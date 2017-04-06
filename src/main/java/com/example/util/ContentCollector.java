package com.example.util;

import io.netty.handler.codec.http.HttpContent;

import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

/**
 * httpContent collector for ReactorNetty
 */
public class ContentCollector implements Collector<HttpContent, StringBuilder, String> {
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