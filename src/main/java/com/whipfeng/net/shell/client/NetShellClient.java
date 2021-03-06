package com.whipfeng.net.shell.client;

import com.whipfeng.net.heart.CustomHeartbeatEncoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * 网络外壳客户端，访问内部网络和外壳网络
 * Created by fz on 2018/11/20.
 */
public class NetShellClient {

    private static final Logger logger = LoggerFactory.getLogger(NetShellClient.class);

    private String nsHost;
    private int nsPort;

    private String inHost;
    private int inPort;

    private volatile long stopTime = 0;

    private boolean running = true;

    private BlockingQueue<NetShellClientDecoder> blockingQueue = new ArrayBlockingQueue(1);

    public NetShellClient(String nsHost, int nsPort, String inHost, int inPort) {
        this.nsHost = nsHost;
        this.nsPort = nsPort;
        this.inHost = inHost;
        this.inPort = inPort;
    }

    public void run() throws Exception {
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            while (running) {
                final NetShellClientDecoder netShellClientCodec = new NetShellClientDecoder(blockingQueue, inHost, inPort);
                blockingQueue.put(netShellClientCodec);
                Bootstrap nsBootstrap = new Bootstrap();
                nsBootstrap.group(workerGroup)
                        .channel(NioSocketChannel.class)
                        .handler(new ChannelInitializer<SocketChannel>() {
                            public void initChannel(SocketChannel ch) throws Exception {
                                ch.pipeline()
                                        .addLast(new IdleStateHandler(0, 0, 5))
                                        .addLast(netShellClientCodec)
                                        .addLast(new CustomHeartbeatEncoder());
                            }
                        });

                ChannelFuture future = nsBootstrap.remoteAddress(nsHost, nsPort).connect();
                future.addListener(new ChannelFutureListener() {
                    public void operationComplete(ChannelFuture future) {
                        if (future.isSuccess()) {
                            stopTime = 0;
                            logger.info("Connect OK:" + future);
                        } else {
                            stopTime = 30000;//睡30秒再来
                            boolean result = blockingQueue.remove(netShellClientCodec);
                            logger.info(result + " Lost Connect:" + future);
                            logger.error("Connect fail, will try again.", future.cause());
                        }
                    }
                });
                if (stopTime > 0) {
                    Thread.sleep(stopTime);
                }
            }
        } finally {
            workerGroup.shutdownGracefully();
        }
    }
}
