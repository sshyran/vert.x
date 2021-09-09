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

package io.vertx.core.impl;

import io.vertx.core.Vertx;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class BenchmarkContext {

  public static ContextImpl create(Vertx vertx) {
    VertxImpl impl = (VertxImpl) vertx;
    return new ContextImpl(
      impl,
      ContextImpl.KIND_BENCHMARK,
      impl.getEventLoopGroup().next(),
      impl.internalWorkerPool,
      impl.workerPool,
      null,
      null,
      Thread.currentThread().getContextClassLoader(),
      false
    );
  }
}
