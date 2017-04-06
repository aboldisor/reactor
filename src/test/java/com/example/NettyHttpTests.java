package com.example;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.proxy.HttpProxyHandler;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.InetSocketAddress;
import java.net.URI;

/**
 * Created by dd on 17/4/5.
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class NettyHttpTests {

    @Test
    public void testHttpClient() throws Exception {
        NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup();
        try {
            Bootstrap bs = new Bootstrap().group(eventLoopGroup)
                    .channel(NioSocketChannel.class).handler(new ChannelInitializer() {
                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            ch.pipeline()
                                    .addLast(new HttpClientCodec())
                                    .addLast(new ChannelInboundHandlerAdapter() {
                                        @Override
                                        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                            if (msg instanceof HttpResponse) {
                                                HttpResponse response = (HttpResponse) msg;
                                                System.out.println("CONTENT_TYPE:" + response.headers().get(HttpHeaders.CONTENT_TYPE));
                                            }
                                            if (msg instanceof HttpContent) {
                                                HttpContent content = (HttpContent) msg;
                                                System.out.println(content.content().toString(io.netty.util.CharsetUtil.UTF_8));

                                                if (content instanceof LastHttpContent) {
                                                      ctx.close();
                                                }
                                            }
                                        }
                                    });
                        }
                    });

            URI uri = new URI("http://www.baidu.com:80");
            Channel ch = bs.connect(uri.getHost(), uri.getPort()).sync().channel();

            DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri.getRawPath());
            request.headers().set(HttpHeaders.HOST, uri.getHost())
                    .set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);

            ch.writeAndFlush(request);
            ch.closeFuture().sync();
        }finally {
            eventLoopGroup.shutdownGracefully();
        }
    }


    @Test
    public void testHttpProxy() throws Exception {

        NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup();
        try {
            Bootstrap bs = new Bootstrap().group(eventLoopGroup)
                    .channel(NioSocketChannel.class).handler(new ChannelInitializer() {
                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            ch.pipeline()
                                    //.addLast(new HttpProxyHandler(new InetSocketAddress("www.baidu.com",80)))//[注意]不要加这个,这个可能是for proxy server的
                                    .addLast(new HttpClientCodec())
                                    .addLast(new ChannelInboundHandlerAdapter() {
                                        @Override
                                        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                            if (msg instanceof HttpResponse) {
                                                HttpResponse response = (HttpResponse) msg;
                                                System.out.println("CONTENT_TYPE:" + response.headers().get(HttpHeaders.CONTENT_TYPE));
                                            }
                                            if (msg instanceof HttpContent) {
                                                HttpContent content = (HttpContent) msg;
                                                ByteBuf buf = content.content();
                                                System.out.println(buf.toString(io.netty.util.CharsetUtil.UTF_8));
                                                buf.release();
                                            }
                                        }
                                    });
                        }
                    });

            URI uri = new URI("http://httpbin.org:80/ip");
            Channel ch = bs.connect("120.52.72.58", 80).sync().channel();

            DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET,
                    uri.getRawPath());
            request.headers().set(HttpHeaders.HOST, uri.getHost())
                    .set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
            ch.writeAndFlush(request);

            ch.closeFuture().sync();
        }finally {
            eventLoopGroup.shutdownGracefully();
        }
    }
}
