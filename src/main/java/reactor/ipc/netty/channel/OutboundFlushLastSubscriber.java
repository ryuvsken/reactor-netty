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
package reactor.ipc.netty.channel;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelPromise;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.Cancellation;
import reactor.core.Exceptions;
import reactor.core.Loopback;
import reactor.core.publisher.Operators;
import reactor.util.Logger;
import reactor.util.Loggers;

/**
 * @author Stephane Maldini
 */
final class OutboundFlushLastSubscriber
		implements Subscriber<Object>, Runnable, Loopback {

	final ChannelPromise          promise;
	final ChannelOperations<?, ?> parent;

	ChannelFuture lastWrite;
	Subscription  subscription;
	Cancellation  c;

	public OutboundFlushLastSubscriber(ChannelOperations<?, ?> parent,
			ChannelPromise promise) {
		this.parent = parent;
		this.promise = promise;
	}

	@Override
	public Object connectedInput() {
		return parent;
	}

	@Override
	public void onComplete() {
		if (subscription == null) {
			return;
		}
		subscription = null;

		Cancellation c = this.c;
		if(c != null) {
			c.dispose();
			this.c = null;
		}


		parent.channel.flush();
		parent.channel
				.eventLoop()
				.execute(() -> parent.onTerminatedSend(lastWrite, promise, null));
	}

	@Override
	public void onError(Throwable t) {
		if (t == null) {
			throw Exceptions.argumentIsNullException();
		}
		if (subscription == null) {
			throw new IllegalStateException("already flushed", t);
		}

		log.error("Write error", t);
		subscription = null;
		Cancellation c = this.c;
		if(c != null) {
			c.dispose();
			this.c = null;
		}

		parent.channel.flush();
		parent.channel
				.eventLoop()
				.execute(() -> parent.onTerminatedSend(lastWrite, promise, t));
	}

	@Override
	public void onNext(final Object w) {
		if (w == null) {
			throw Exceptions.argumentIsNullException();
		}
		if (subscription == null) {
			throw Exceptions.failWithCancel();
		}
		try {
			ChannelFuture cf = parent.sendNext(w);
			lastWrite = cf;
			if (cf != null && log.isDebugEnabled()) {
				cf.addListener((ChannelFutureListener) future -> {
					if (!future.isSuccess()) {
						log.error("write error :" + w, future.cause());
						if (ByteBuf.class.isAssignableFrom(w.getClass())) {
							((ByteBuf) w).resetReaderIndex();
						}
					}
				});
			}
		}
		catch (Throwable t) {
			log.error("Write error for " + w, t);
			onError(t);
		}
	}

	@Override
	public void onSubscribe(final Subscription s) {
		if (Operators.validate(subscription, s)) {

			if(parent.channel.isOpen()){
				this.subscription = s;

				parent.onInactive.subscribe(null, null, this);

				s.request(Long.MAX_VALUE);
			}
			else{
				s.cancel();
			}

		}
	}

	@Override
	public void run() {
		Subscription subscription = this.subscription;
		this.subscription = null;
		c = null;
		if (subscription != null) {
			if (log.isDebugEnabled()) {
				log.debug("Cancel from remotely closed connection");
			}
			subscription.cancel();
		}
	}

	static final Logger log = Loggers.getLogger(OutboundFlushLastSubscriber.class);
}
