package com.whipfeng.net.shell.server.proxy;

import com.whipfeng.net.shell.ContextRouter;
import com.whipfeng.net.shell.MsgExchangeHandler;
import io.netty.channel.*;
import io.netty.handler.codec.socksx.v5.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by fz on 2018/11/26.
 */
public class Socks5CommandRequestHandler extends SimpleChannelInboundHandler<DefaultSocks5CommandRequest> {

    private static final Logger logger = LoggerFactory.getLogger(Socks5CommandRequestHandler.class);

    private NetShellProxyServerQueue bondQueue;

    public Socks5CommandRequestHandler(NetShellProxyServerQueue bondQueue) {
        this.bondQueue = bondQueue;
    }

    @Override
    protected void messageReceived(final ChannelHandlerContext outCtx, final DefaultSocks5CommandRequest commandRequest) throws Exception {
        logger.info("Dest Server:" + commandRequest);
        if (commandRequest.decoderResult().isSuccess() && Socks5CommandType.CONNECT.equals(commandRequest.type())) {
            ContextRouter outRouter = new ContextRouter(outCtx, commandRequest);
            ContextRouter nsRouter = bondQueue.matchNetShell(outRouter);
            if (null == nsRouter) {
                return;
            }
            ChannelHandlerContext nsCtx = nsRouter.getCtx();
            logger.info("Match Net:" + nsCtx.channel().remoteAddress());
            Channel nsChannel = nsCtx.channel();
            outCtx.pipeline().addLast(new MsgExchangeHandler(nsCtx.channel()));
            nsCtx.pipeline().addLast(new MsgExchangeHandler(outCtx.channel()));
            nsCtx.pipeline().get(NetShellProxyServerCodec.class).sendReqMsg(nsCtx, commandRequest);
            if (!nsChannel.isActive()) {
                outCtx.close();
            }
            return;
        }

        logger.warn("Wrong command type:" + commandRequest);
        outCtx.close();
    }
}