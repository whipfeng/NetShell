package com.whipfeng.net.shell.client.proxy;

import com.whipfeng.net.heart.CustomHeartbeatConst;
import com.whipfeng.net.heart.CustomHeartbeatDecoder;
import com.whipfeng.net.shell.MsgExchangeHandler;
import com.whipfeng.net.shell.RC4Transfer;
import com.whipfeng.util.RC4Util;
import com.whipfeng.util.RSAUtil;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.concurrent.BlockingQueue;

/**
 * 网络外壳客户端编解码器
 * Created by user on 2018/11/22.
 */
public class NetShellProxyClientDecoder extends CustomHeartbeatDecoder {

    private static final Logger logger = LoggerFactory.getLogger(NetShellProxyClientDecoder.class);

    private static final byte CONN_PRE_MSG = 4;
    private static final byte CONN_REQ_MSG = 5;
    private static final byte CONN_ACK_MSG = 6;
    private static final byte PW_EX_REQ_MSG = 7;
    private static final byte PW_EX_ACK_MSG = 8;
    private static final byte PW_EX_REQ_MSG_V2 = 9;

    private BlockingQueue<NetShellProxyClientDecoder> blockingQueue;

    private int networkCode;
    private int subMaskCode;

    private byte[] secretKey;
    private boolean isV2 = false;

    public NetShellProxyClientDecoder(BlockingQueue<NetShellProxyClientDecoder> blockingQueue, int networkCode, int subMaskCode) {
        this.blockingQueue = blockingQueue;
        this.networkCode = networkCode;
        this.subMaskCode = subMaskCode;
    }

    @Override
    protected void decode(final ChannelHandlerContext ctx, byte flag) throws Exception {
        //交换密码应答
        if (PW_EX_ACK_MSG == flag) {
            ctx.pipeline().addLast(new RC4Transfer(secretKey).getIOHandlers());
            return;
        }
        super.decode(ctx, flag);
    }

    @Override
    protected void decode(final ChannelHandlerContext nsCtx, byte flag, ByteBuf in, int len) throws Exception {
        //连接請求
        if (CONN_REQ_MSG == flag && len > 2) {
            logger.debug("Received 'CONN_REQ' from: " + nsCtx);

            byte[] buf = new byte[len];
            in.readBytes(buf);
            if (isV2) {
                RC4Util.transfer(this.secretKey, buf);
            }
            //获取主机名和端口
            final int inPort = ((255 & buf[0]) << 8) | (255 & buf[1]);
            final String inHost = new String(buf, 2, len - 2, CharsetUtil.UTF_8);

            boolean result = blockingQueue.remove(this);
            logger.info(result + " Connect Request(P):" + inHost + ":" + inPort + nsCtx);

            Bootstrap inBootstrap = new Bootstrap();
            inBootstrap.group(nsCtx.channel().eventLoop().parent())
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .option(ChannelOption.AUTO_READ, false)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        public void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline()
                                    .addLast(new MsgExchangeHandler(nsCtx.channel()));
                        }
                    });

            ChannelFuture inFuture = inBootstrap.remoteAddress(inHost, inPort).connect();

            //异步等待连接结果
            inFuture.addListener(new ChannelFutureListener() {
                public void operationComplete(ChannelFuture inFuture) {
                    if (inFuture.isSuccess()) {
                        logger.info("Connect Finish(P):" + inHost + ":" + inPort);
                        Channel nsChannel = nsCtx.channel();
                        Channel inChannel = inFuture.channel();
                        nsCtx.pipeline().addLast(new MsgExchangeHandler(inChannel));
                        //响应连接
                        sendFlagMsg(nsCtx, CONN_ACK_MSG);
                        inChannel.config().setAutoRead(true);
                        inChannel.read();

                        //如果外壳网络已经挂了，则直接关闭内部网络
                        if (!nsChannel.isActive()) {
                            inChannel.close();
                        }
                    } else {
                        logger.info("Connect FAIL(P):" + inHost + ":" + inPort);
                        //如果内部网络没成功，则直接关闭外壳网络
                        nsCtx.close();
                    }
                }
            });
            return;
        }
        super.decode(nsCtx, flag);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        sendPassword(ctx);//发送密码
        sendPreMsg(ctx);
        ctx.fireChannelActive();
    }

    private void sendPassword(ChannelHandlerContext ctx) throws Exception {
        if (RSAUtil.noPublicKey()) {
            return;
        }
        SecureRandom random = new SecureRandom();
        int numBytes = 128 + random.nextInt(128);
        secretKey = random.generateSeed(numBytes);
        byte[] key = RSAUtil.publicEncrypt(secretKey);
        //随机生成并写出密码
        ByteBuf out = ctx.alloc().buffer(CustomHeartbeatConst.HEAD_LEN + key.length);
        out.writeInt(key.length);
        isV2 = true;
        out.writeByte(PW_EX_REQ_MSG_V2);
        out.writeBytes(key);
        ctx.pipeline().context(this).writeAndFlush(out);
    }

    private void sendPreMsg(ChannelHandlerContext ctx) {
        //连接成功发送网络号和子网掩码
        ByteBuf out = ctx.alloc().buffer(CustomHeartbeatConst.HEAD_LEN + 8);
        out.writeInt(8);
        out.writeByte(CONN_PRE_MSG);
        out.writeInt(networkCode);
        out.writeInt(subMaskCode);
        ctx.pipeline().context(this).writeAndFlush(out);
    }


    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        boolean result = blockingQueue.remove(this);
        logger.info(result + " Lost Connect:" + ctx);
        ctx.fireChannelInactive();
    }
}
