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

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelPromise;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.Cancellation;
import reactor.core.Exceptions;
import reactor.core.Loopback;
import reactor.core.Receiver;
import reactor.core.Trackable;
import reactor.core.publisher.Operators;
import reactor.util.Logger;
import reactor.util.Loggers;

/**
 * @author Stephane Maldini
 */
final class OutboundFlushEachSubscriber
		implements Subscriber<Object>, Loopback, Trackable,
		           Receiver, Runnable {

	final ChannelOperations<?, ?> parent;
	final ChannelPromise          promise;
	final ChannelFutureListener writeListener = new WriteListener();

	volatile Subscription subscription;

	Cancellation c;

	public OutboundFlushEachSubscriber(ChannelOperations<?, ?> parent,
			ChannelPromise promise) {
		this.parent = parent;
		this.promise = promise;
	}

	@Override
	public Object connectedInput() {
		return parent;
	}

	@Override
	public boolean isCancelled() {
		return !parent.channel
		           .isOpen();
	}

	@Override
	public boolean isStarted() {
		return subscription != null;
	}

	@Override
	public boolean isTerminated() {
		return !parent.channel
		           .isOpen();
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

		parent.channel
		   .eventLoop()
		   .execute(() -> parent.onTerminatedSend(null, promise, null));
	}

	@Override
	public void onError(Throwable t) {
		if (t == null) {
			throw Exceptions.argumentIsNullException();
		}
		if (subscription == null) {
			throw new IllegalStateException("already terminated", t);
		}
		log.error("Write error", t);
		subscription = null;
		Cancellation c = this.c;
		if(c != null) {
			c.dispose();
			this.c = null;
		}

		parent.channel
		   .eventLoop()
		   .execute(() -> parent.onTerminatedSend(null, promise, t));
	}

	@Override
	public void onNext(Object w) {
		if (w == null) {
			throw Exceptions.argumentIsNullException();
		}
		if (subscription == null) {
			throw Exceptions.failWithCancel();
		}
		try {
			ChannelFuture cf = parent.sendNext(w);
			if (cf != null) {
				cf.addListener(writeListener);
			}
			parent.channel.flush();
		}
		catch (Throwable t) {
			log.error("Write error for " + w, t);
			onError(t);
			throw Exceptions.failWithCancel();
		}
	}

	@Override
	public void onSubscribe(final Subscription s) {
		if (Operators.validate(subscription, s)) {
			if(parent.channel.isOpen()){
				this.subscription = s;

				parent.onInactive.subscribe(null, null, this);

				s.request(1L);
			}
			else{
				s.cancel();
			}
		}
	}

	@Override
	public Object upstream() {
		return subscription;
	}

	final class WriteListener implements ChannelFutureListener {

		@Override
		public void operationComplete(ChannelFuture future) throws Exception {
			if (!future.isSuccess()) {
				promise.tryFailure(future.cause());
				if (log.isDebugEnabled()) {
					log.debug("Write error", future.cause());
				}
				return;
			}
			Subscription subscription = OutboundFlushEachSubscriber.this.subscription;
			if (subscription != null) {
				subscription.request(1L);
			}
		}
	}

	@Override
	public void run() {
		Subscription subscription = this.subscription;
		this.subscription = null;
		if (subscription != null) {
			if (log.isDebugEnabled()) {
				log.debug("Cancel from remotely closed connection");
			}
			subscription.cancel();
		}
	}

	static final Logger log = Loggers.getLogger(OutboundFlushEachSubscriber.class);
}
