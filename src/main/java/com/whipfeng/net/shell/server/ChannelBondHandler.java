package com.whipfeng.net.shell.server;

import com.whipfeng.net.shell.MsgExchangeHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 通道纽带
 * Created by fz on 2018/11/22.
 */
public class ChannelBondHandler extends ChannelHandlerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(ChannelBondHandler.class);

    private ChannelBondQueue bondQueue;

    public ChannelBondHandler(ChannelBondQueue bondQueue) {
        this.bondQueue = bondQueue;
    }

    @Override
    public void channelActive(ChannelHandlerContext outCtx) {
        logger.info("Connect OK:" + outCtx.channel().remoteAddress());
        ChannelHandlerContext nsCtx = bondQueue.matchNetShell(outCtx);
        if (null != nsCtx) {
            logger.info("Match Net:" + nsCtx.channel().remoteAddress());
            Channel nsChannel = nsCtx.channel();
            outCtx.pipeline().addLast(new MsgExchangeHandler(nsCtx.channel()));
            nsCtx.pipeline().addLast(new MsgExchangeHandler(outCtx.channel()));
            nsCtx.pipeline().get(NetShellServerCodec.class).sendFlagMsg(nsCtx, NetShellServerCodec.CONN_REQ_MSG);
            if (!nsChannel.isActive()) {
                outCtx.close();
            }
        }
        outCtx.fireChannelActive();
    }
}
