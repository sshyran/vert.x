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

import io.vertx.core.*;

/**
 * A context implementation that does not hold any specific state.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class AbstractContext implements ContextInternal {

  @Override
  public final boolean isRunningOnContext() {
    return Vertx.currentContext() == this && inThread();
  }

  protected abstract boolean inThread();

  static <T> void setResultHandler(ContextInternal ctx, Future<T> fut, Handler<AsyncResult<T>> resultHandler) {
    if (resultHandler != null) {
      fut.onComplete(resultHandler);
    } else {
      fut.onFailure(ctx::reportException);
    }
  }
}
