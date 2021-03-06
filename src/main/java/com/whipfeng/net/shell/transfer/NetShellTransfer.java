package com.whipfeng.net.shell.transfer;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 网络外壳代理器，用于转发
 * Created by fz on 2018/11/19.
 */
public class NetShellTransfer {

    private static final Logger logger = LoggerFactory.getLogger(NetShellTransfer.class);

    private int tsfPort;
    private String dstHost;
    private int dstPort;
    private EventLoopGroup bossGroup = new NioEventLoopGroup(1);
    private EventLoopGroup workerGroup = new NioEventLoopGroup();

    public NetShellTransfer(int tsfPort, String dstHost, int dstPort) {
        this.tsfPort = tsfPort;
        this.dstHost = dstHost;
        this.dstPort = dstPort;
    }

    public void run() throws Exception {
        try {
            ServerBootstrap tsfBootstrap = new ServerBootstrap();
            tsfBootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.AUTO_READ, false)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        public void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline()
                                    .addLast(new NetShellTransferHandler(dstHost, dstPort));
                        }
                    });

            ChannelFuture tsfFuture = tsfBootstrap.bind(tsfPort).sync();
            tsfFuture.channel().closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
