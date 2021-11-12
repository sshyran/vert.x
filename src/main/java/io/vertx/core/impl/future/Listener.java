/*
 * Copyright (c) 2011-2020 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.impl.future;

import io.vertx.core.impl.ContextInternal;

/**
 * Internal listener that signals success or failure when a future completes.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface Listener<T> {

  /**
   * Signal the success.
   *
   * @param value the value
   */
  default void emitSuccess(ContextInternal context, T value) {
    if (context != null && !context.isRunningOnContext()) {
      context.execute(() -> {
        ContextInternal prev = context.beginDispatch();
        try {
          onSuccess(value);
        } finally {
          context.endDispatch(prev);
        }
      });
    } else {
      onSuccess(value);
    }
  }

  /**
   * Signal the failure
   *
   * @param failure the failure
   */
  default void emitFailure(ContextInternal context, Throwable failure) {
    if (context != null && !context.isRunningOnContext()) {
      context.execute(() -> {
        ContextInternal prev = context.beginDispatch();
        try {
          onFailure(failure);
        } finally {
          context.endDispatch(prev);
        }
      });
    } else {
      onFailure(failure);
    }
  }

  /**
   * Signal the success.
   *
   * @param value the value
   */
  void onSuccess(T value);

  /**
   * Signal the failure
   *
   * @param failure the failure
   */
  void onFailure(Throwable failure);
}
