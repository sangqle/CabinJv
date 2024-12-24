package com.cabin.express;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;

public class CabinJNetty {
    public static void main(String[] args) throws InterruptedException {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1); // Accepts connections
        EventLoopGroup workerGroup = new NioEventLoopGroup(); // Handles I/O

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup).channel(NioServerSocketChannel.class).childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    ch.pipeline().addLast(new HttpServerCodec());
                    ch.pipeline().addLast(new HttpObjectAggregator(65536));
                    ch.pipeline().addLast(new SimpleChannelInboundHandler<FullHttpRequest>() {
                        @Override
                        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) {
                            // Handle HTTP request
                            String responseContent = "Hello, Netty!";
                            FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, ctx.alloc().buffer().writeBytes(responseContent.getBytes()));
                            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
                            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, responseContent.length());
                            ctx.writeAndFlush(response);
                        }
                    });
                }
            });

            ChannelFuture future = bootstrap.bind(8080).sync();
            System.out.println("Netty server started on port 8080");
            future.channel().closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
