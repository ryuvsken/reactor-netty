/*
 * Copyright (c) 2011-2016 Pivotal Software Inc, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package reactor.ipc.netty.http.server;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import reactor.ipc.netty.http.HttpOperations;

/**
 * Conversion between Netty types  and Reactor types ({@link HttpOperations}
 *
 * @author Stephane Maldini
 */
final class HttpServerWSOperations extends HttpServerOperations {

	final WebSocketServerHandshaker handshaker;
	final ChannelFuture             handshakerResult;
	final boolean                   plainText;

	public HttpServerWSOperations(String wsUrl,
			String protocols,
			HttpServerOperations replaced,
			boolean plainText) {
		super(replaced.channel(), replaced);
		this.plainText = plainText;

		Channel channel = replaced.channel();

		// Handshake
		WebSocketServerHandshakerFactory wsFactory =
				new WebSocketServerHandshakerFactory(wsUrl, protocols, true);
		handshaker = wsFactory.newHandshaker(replaced.nettyRequest);
		if (handshaker == null) {
			WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(channel);
			handshakerResult = null;
		}
		else {
			HttpUtil.setTransferEncodingChunked(replaced.nettyResponse, false);
			handshakerResult = handshaker.handshake(channel,
					replaced.nettyRequest,
					replaced.nettyRequest.headers(),
					channel.newPromise())
			                             .addListener(f -> channel.read());
			onClose(() -> {
				if (channel.isOpen()) {
					if (channel.pipeline()
					           .context("wsencoder") != null) {
						channel.pipeline()
						       .remove("wsencoder");
					}
					if (channel.pipeline()
					           .context("wsdecoder") != null) {
						channel.pipeline()
						       .remove("wsdecoder");
					}
				}
			});
		}
	}

	@Override
	public void onInboundNext(ChannelHandlerContext ctx, Object frame) {
		if (frame instanceof CloseWebSocketFrame) {
			onInboundComplete();
			return;
		}
		if (frame instanceof PingWebSocketFrame) {
			ctx.writeAndFlush(new PongWebSocketFrame(((PingWebSocketFrame) frame).content()
			                                                                           .retain()));
			return;
		}
		super.onInboundNext(ctx, frame);
	}

	@Override
	protected ChannelFuture sendNext(Object data) {
		if (data instanceof ByteBuf) {
			if (plainText) {
				return channel().write(new TextWebSocketFrame((ByteBuf) data));
			}
			return channel().write(new BinaryWebSocketFrame((ByteBuf) data));
		}
		else if (data instanceof String) {
			return channel().write(new TextWebSocketFrame((String) data));
		}
		return channel().write(data);
	}

	@Override
	public boolean isWebsocket() {
		return true;
	}
}
