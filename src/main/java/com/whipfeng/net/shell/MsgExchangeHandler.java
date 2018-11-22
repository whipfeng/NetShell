package com.whipfeng.net.shell;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 交换
 * Created by fz on 2018/11/22.
 */
public class MsgExchangeHandler extends ChannelHandlerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(MsgExchangeHandler.class);

    private Channel channel;

    public MsgExchangeHandler(Channel channel) {
        this.channel = channel;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        channel.writeAndFlush(msg);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        channel.close();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.debug("Be forced to close:" + ctx.channel().remoteAddress(), cause);
        channel.close();
        ctx.close();
    }
}