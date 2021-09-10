/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.spi;

import io.netty.channel.EventLoop;
import io.vertx.core.impl.CloseFuture;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.Deployment;
import io.vertx.core.impl.VertxBuilder;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.VertxThread;
import io.vertx.core.impl.WorkerPool;

import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface VertxContextFactory extends VertxServiceProvider {

  VertxContextFactory INSTANCE = new VertxContextFactory() {
  };

  @Override
  default void init(VertxBuilder builder) {
    if (builder.threadFactory() == null) {
      builder.contextFactory(this);
    }
  }

  default ContextInternal newVertxContext(VertxInternal vertx,
                                          int kind,
                                          EventLoop eventLoop,
                                          WorkerPool internalWorkerPool,
                                          WorkerPool workerPool,
                                          Deployment deployment,
                                          CloseFuture closeFuture,
                                          ClassLoader tccl,
                                          boolean disableTCCL) {
    return new ContextImpl(vertx, kind, eventLoop, internalWorkerPool, workerPool, deployment, closeFuture, tccl, disableTCCL);
  }
}
