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

import io.netty.channel.EventLoop;
import io.vertx.core.*;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonObject;
import io.vertx.core.spi.metrics.PoolMetrics;
import io.vertx.core.spi.tracing.VertxTracer;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ContextImpl extends AbstractContext {

  /**
   * Execute the {@code task} disabling the thread-local association for the duration
   * of the execution. {@link Vertx#currentContext()} will return {@code null},
   * @param task the task to execute
   * @throws IllegalStateException if the current thread is not a Vertx thread
   */
  static void executeIsolated(Handler<Void> task) {
    Thread currentThread = Thread.currentThread();
    if (currentThread instanceof VertxThread) {
      VertxThread vertxThread = (VertxThread) currentThread;
      ContextInternal prev = vertxThread.beginEmission(null);
      try {
        task.handle(null);
      } finally {
        vertxThread.endEmission(prev);
      }
    } else {
      task.handle(null);
    }
  }

  private static final Logger log = LoggerFactory.getLogger(ContextImpl.class);

  private static final String DISABLE_TIMINGS_PROP_NAME = "vertx.disableContextTimings";
  static final boolean DISABLE_TIMINGS = Boolean.getBoolean(DISABLE_TIMINGS_PROP_NAME);

  static final int KIND_EVENT_LOOP = 0;
  static final int KIND_WORKER = 1;
  static final int KIND_BENCHMARK = 2;

  protected final VertxInternal owner;
  protected final JsonObject config;
  private final Deployment deployment;
  private final CloseFuture closeFuture;
  private final ClassLoader tccl;
  private final EventLoop eventLoop;
  private final int kind;
  private ConcurrentMap<Object, Object> data;
  private ConcurrentMap<Object, Object> localData;
  private volatile Handler<Throwable> exceptionHandler;
  final TaskQueue internalOrderedTasks;
  final WorkerPool internalBlockingPool;
  final WorkerPool workerPool;
  final TaskQueue orderedTasks;

  public ContextImpl(VertxInternal vertx,
              int kind,
              EventLoop eventLoop,
              WorkerPool internalWorkerPool,
              WorkerPool workerPool,
              Deployment deployment,
              CloseFuture closeFuture,
              ClassLoader tccl,
              boolean disableTCCL) {
    super(disableTCCL);
    this.deployment = deployment;
    this.config = deployment != null ? deployment.config() : new JsonObject();
    this.eventLoop = eventLoop;
    this.kind = kind;
    this.tccl = tccl;
    this.owner = vertx;
    this.workerPool = workerPool;
    this.closeFuture = closeFuture;
    this.internalBlockingPool = internalWorkerPool;
    this.orderedTasks = new TaskQueue();
    this.internalOrderedTasks = new TaskQueue();
  }

  public Deployment getDeployment() {
    return deployment;
  }

  @Override
  public CloseFuture closeFuture() {
    return closeFuture;
  }

  @Override
  public boolean isDeployment() {
    return deployment != null;
  }

  @Override
  public String deploymentID() {
    return deployment != null ? deployment.deploymentID() : null;
  }

  @Override
  public JsonObject config() {
    return config;
  }

  public EventLoop nettyEventLoop() {
    return eventLoop;
  }

  public VertxInternal owner() {
    return owner;
  }

  @Override
  public <T> Future<T> executeBlockingInternal(Handler<Promise<T>> action) {
    return executeBlocking(this, action, internalBlockingPool, internalOrderedTasks);
  }

  @Override
  public <T> Future<T> executeBlockingInternal(Handler<Promise<T>> action, boolean ordered) {
    return executeBlocking(this, action, internalBlockingPool, ordered ? internalOrderedTasks : null);
  }

  @Override
  public <T> Future<T> executeBlocking(Handler<Promise<T>> blockingCodeHandler, boolean ordered) {
    return executeBlocking(this, blockingCodeHandler, workerPool, ordered ? orderedTasks : null);
  }

  @Override
  public <T> Future<T> executeBlocking(Handler<Promise<T>> blockingCodeHandler, TaskQueue queue) {
    return executeBlocking(this, blockingCodeHandler, workerPool, queue);
  }

  static <T> Future<T> executeBlocking(ContextInternal context, Handler<Promise<T>> blockingCodeHandler,
      WorkerPool workerPool, TaskQueue queue) {
    PoolMetrics metrics = workerPool.metrics();
    Object queueMetric = metrics != null ? metrics.submitted() : null;
    Promise<T> promise = context.promise();
    Future<T> fut = promise.future();
    try {
      Runnable command = () -> {
        Object execMetric = null;
        if (metrics != null) {
          execMetric = metrics.begin(queueMetric);
        }
        context.dispatch(promise, f -> {
          try {
            blockingCodeHandler.handle(promise);
          } catch (Throwable e) {
            promise.tryFail(e);
          }
        });
        if (metrics != null) {
          metrics.end(execMetric, fut.succeeded());
        }
      };
      Executor exec = workerPool.executor();
      if (queue != null) {
        queue.execute(command, exec);
      } else {
        exec.execute(command);
      }
    } catch (RejectedExecutionException e) {
      // Pool is already shut down
      if (metrics != null) {
        metrics.rejected(queueMetric);
      }
      throw e;
    }
    return fut;
  }

  @Override
  public VertxTracer tracer() {
    return owner.tracer();
  }

  @Override
  public ClassLoader classLoader() {
    return tccl;
  }

  @Override
  public WorkerPool workerPool() {
    return workerPool;
  }

  @Override
  public synchronized ConcurrentMap<Object, Object> contextData() {
    if (data == null) {
      data = new ConcurrentHashMap<>();
    }
    return data;
  }

  @Override
  public synchronized ConcurrentMap<Object, Object> localContextData() {
    if (localData == null) {
      localData = new ConcurrentHashMap<>();
    }
    return localData;
  }

  public void reportException(Throwable t) {
    Handler<Throwable> handler = exceptionHandler;
    if (handler == null) {
      handler = owner.exceptionHandler();
    }
    if (handler != null) {
      handler.handle(t);
    } else {
      log.error("Unhandled exception", t);
    }
  }

  @Override
  public Context exceptionHandler(Handler<Throwable> handler) {
    exceptionHandler = handler;
    return this;
  }

  @Override
  public Handler<Throwable> exceptionHandler() {
    return exceptionHandler;
  }

  public int getInstanceCount() {
    // the no verticle case
    if (deployment == null) {
      return 0;
    }

    // the single verticle without an instance flag explicitly defined
    if (deployment.deploymentOptions() == null) {
      return 1;
    }
    return deployment.deploymentOptions().getInstances();
  }

  @Override
  public boolean isEventLoopContext() {
    return kind == KIND_EVENT_LOOP;
  }

  @Override
  boolean inThread() {
    if (kind == KIND_EVENT_LOOP) {
      return eventLoop.inEventLoop();
    } else {
      return inThread2();
    }
  }

  private boolean inThread2() {
    if (kind == KIND_WORKER) {
      return Context.isOnWorkerThread();
    } else {
      throw new UnsupportedOperationException();
    }
  }

  @Override
  public final void runOnContext(Handler<Void> action) {
    runOnContext(this, action);
  }

  void runOnContext(ContextInternal ctx, Handler<Void> action) {
    if (kind == KIND_EVENT_LOOP) {
      try {
        nettyEventLoop().execute(() -> ctx.dispatch(action));
      } catch (RejectedExecutionException ignore) {
        // Pool is already shut down
      }
    } else {
      runOnContext2(ctx, action);
    }
  }

  private void runOnContext2(ContextInternal ctx, Handler<Void> action) {
    if (kind == KIND_WORKER) {
      try {
        runTask(orderedTasks, null, v -> ctx.dispatch(action));
      } catch (RejectedExecutionException ignore) {
        // Pool is already shut down
      }
    } else {
      ctx.dispatch(null, action);
    }
  }

  @Override
  public void execute(Runnable task) {
    execute(task, Runnable::run);
  }

  @Override
  public final <T> void execute(T argument, Handler<T> task) {
    if (kind == KIND_EVENT_LOOP) {
      EventLoop eventLoop = nettyEventLoop();
      if (eventLoop.inEventLoop()) {
        task.handle(argument);
      } else {
        eventLoop.execute(() -> task.handle(argument));
      }
    } else {
      execute2(argument, task);
    }
  }

  private <T> void execute2(T argument, Handler<T> task) {
    if (kind == KIND_WORKER) {
      if (Context.isOnWorkerThread()) {
        task.handle(argument);
      } else {
        runTask(orderedTasks, argument, task);
      }
    } else {
      task.handle(argument);
    }
  }

  @Override
  public <T> void emit(T argument, Handler<T> task) {
    emit(this, argument, task);
  }

  <T> void emit(ContextInternal ctx, T argument, Handler<T> task) {
    if (kind == KIND_EVENT_LOOP) {
      EventLoop eventLoop = nettyEventLoop();
      if (eventLoop.inEventLoop()) {
        ContextInternal prev = ctx.beginDispatch();
        try {
          task.handle(argument);
        } catch (Throwable t) {
          reportException(t);
        } finally {
          ctx.endDispatch(prev);
        }
      } else {
        eventLoop.execute(() -> emit(ctx, argument, task));
      }
    } else {
      emit2(ctx, argument, task);
    }
  }

  private <T> void emit2(ContextInternal ctx, T argument, Handler<T> task) {
    if (kind == KIND_WORKER) {
      if (Context.isOnWorkerThread()) {
        ctx.dispatch(argument, task);
      } else {
        runTask(orderedTasks, argument, arg -> ctx.dispatch(arg, task));
      }
    } else {
      throw new UnsupportedOperationException();
    }
  }

  @Override
  public final ContextInternal duplicate() {
    return new DuplicatedContext(this);
  }

  private <T> void runTask(TaskQueue queue, T value, Handler<T> task) {
    Objects.requireNonNull(task, "Task handler must not be null");
    PoolMetrics metrics = workerPool.metrics();
    Object queueMetric = metrics != null ? metrics.submitted() : null;
    queue.execute(() -> {
      Object execMetric = null;
      if (metrics != null) {
        execMetric = metrics.begin(queueMetric);
      }
      try {
        task.handle(value);
      } finally {
        if (metrics != null) {
          metrics.end(execMetric, true);
        }
      }
    }, workerPool.executor());
  }
}
