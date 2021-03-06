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

package reactor.ipc.netty;

import java.util.Objects;

/**
 * Constant for names used when adding/removing {@link io.netty.channel.ChannelHandler}.
 *
 * Order of placement :
 * <p>
 * {@code
 * -> proxy ? [ProxyHandler]
 * -> ssl ? [SslHandler]
 * -> ssl & trace log ? [SslLoggingHandler]
 * -> ssl ? [SslReader]
 * -> log ? [LoggingHandler]
 * -> http server & keep-alive ? [HttpKeepAlive]
 * => [BridgeSetup]
 * -> http ws ? [HttpAggregator]
 * -> http ? [HttpCodecHandler]
 * -> onWriteIdle ? [OnChannelWriteIdle]
 * -> onReadIdle ? [OnChannelReadIdle]
 * -> http form/multipart ? [ChunkedWriter]
 * => [ReactiveBridge]
 * }
 *
 * @author Stephane Maldini
 * @since 0.6
 */
public interface NettyHandlerNames {

	String SslHandler         = "sslHandler";
	String SslReader          = "sslReader";
	String SslLoggingHandler  = "sslLoggingHandler";
	String ProxyHandler       = "proxyHandler";
	String ReactiveBridge     = "reactiveBridge";
	String BridgeSetup        = "bridgeSetup";
	String HttpCodecHandler   = "httpCodecHandler";
	String HttpAggregator     = "reactorHttpAggregator";
	String HttpKeepAlive      = "httpKeepAlive";
	String OnChannelWriteIdle = "onChannelWriteIdle";
	String OnChannelReadIdle  = "onChannelReadIdle";
	String ChunkedWriter      = "chunkedWriter";
	String LoggingHandler     = "loggingHandler";
}
