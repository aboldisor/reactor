package com.example;

import io.netty.buffer.ByteBufAllocator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.reactive.function.server.*;
import reactor.core.publisher.Mono;
import reactor.ipc.netty.http.server.HttpServer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.List;

/**
 * 测试spring5的reactor server
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class ReactorServerTests {

    @Test
    public void testServer() throws IOException {
        DataBufferFactory factory = new NettyDataBufferFactory(ByteBufAllocator.DEFAULT);

        RouterFunction<?> router = RouterFunctions
                .route(RequestPredicates.GET("/file"), serverRequest -> {
                    File file = new File("/Users/dd/Downloads/avator.png");

                    List<HttpRange> ranges = serverRequest.headers().range();
                    if (ranges == null || ranges.isEmpty()) {
                        return SomeHelper.serverWholeFile(factory, file);
                    }else if (ranges.size() == 1) {
                        HttpRange range = ranges.get(0);
                        return SomeHelper.serverChunkFile(factory, file, range);
                    }else {
                        return ServerResponse.badRequest().body(null);
                    }

                }).andRoute(RequestPredicates.GET("/test"), serverRequest -> {
                    DataBuffer buffer = factory.allocateBuffer().write("Test!".getBytes());
                    return ServerResponse.ok().body(((serverHttpResponse, context) -> {
                        return serverHttpResponse.writeWith(Mono.just(buffer));
                    }));
                });

        HttpHandler handler = RouterFunctions.toHttpHandler(router);
        HttpServer.create(8686).newHandler(new ReactorHttpHandlerAdapter(handler)).block();

        //blocking
        System.in.read();
    }


    static class SomeHelper {
        /**
         * The MIME mappings for this web application, keyed by extension.
         */
        private static final HashMap<String, String> mimeMappings = new HashMap<String, String>();

        static{
            mimeMappings.put("jpg", "image/jpeg");
            mimeMappings.put("jpeg", "image/jpeg");
            mimeMappings.put("png", "image/png");
            mimeMappings.put("gif", "image/gif");
            mimeMappings.put("mp4", "video/mp4");
            mimeMappings.put("exe", "application/octet-stream");
        }

        /**
         * Return the MIME type of the specified file, or <code>null</code> if
         * the MIME type cannot be determined.
         *
         * @param file Filename for which to identify a MIME type
         */
        static String getMimeType(String file) {
            if (file == null)
                return (null);
            int period = file.lastIndexOf(".");
            if (period < 0)
                return (null);
            String extension = file.substring(period + 1);
            if (extension.length() < 1)
                return (null);
            return (mimeMappings.get(extension));
        }

        static boolean checkIfHeaders(ServerRequest request){
            return false;
        }

        static Mono<ServerResponse> serverWholeFile(DataBufferFactory factory, File file) {
            return ServerResponse.ok()
                    .contentType(MediaType.parseMediaType(getMimeType(file.getName())))
                    //.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename="+file.getName())
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename="+file.getName())
                    .contentLength(file.length())
                    .body((resp, context) -> {
                        try {
                            FileChannel channel = new FileInputStream(file).getChannel();
                            System.out.println(channel.size());
                            return resp.writeWith(DataBufferUtils.read(channel, factory, 1000));//channel会自动关闭
                        } catch (IOException e) {
                            e.printStackTrace();
                            return resp.writeWith(Mono.just(factory.allocateBuffer().write(e.getMessage().getBytes())));
                        }
                    });
        }

        static Mono<ServerResponse> serverChunkFile(DataBufferFactory factory, File file, HttpRange range) {
            long start = range.getRangeStart(0);
            long end = range.getRangeEnd(Integer.MAX_VALUE);
            return ServerResponse.status(HttpStatus.PARTIAL_CONTENT)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes "+start+"-"+end)
                    .contentLength(end-start)
                    .body((resp, context) -> {
                        try {
                            FileChannel channel = new FileInputStream(file).getChannel();
                            System.out.println(range);
                            return resp.writeWith(
                                    DataBufferUtils.takeUntilByteCount(
                                            DataBufferUtils.skipUntilByteCount(DataBufferUtils.read(channel, factory, 1000),
                                                    start),
                                            end-start));
                        } catch (IOException e) {
                            e.printStackTrace();
                            return resp.writeWith(Mono.just(factory.allocateBuffer().write(e.getMessage().getBytes())));
                        }
                    });
        }
    }
}
