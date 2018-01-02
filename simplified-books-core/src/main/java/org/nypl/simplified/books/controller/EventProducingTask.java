package org.nypl.simplified.books.controller;

import com.io7m.jfunctional.FunctionType;
import com.io7m.jnull.NullCheck;

import org.nypl.simplified.observable.ObservableType;
import org.slf4j.Logger;

import java.util.concurrent.Callable;

final class EventProducingTask<T> implements Callable<T> {

  private final ObservableType<? super T> receiver;
  private final Callable<T> execute;
  private final FunctionType<Exception, T> on_exception;
  private final Logger logger;

  EventProducingTask(
      final Logger logger,
      final ObservableType<? super T> receiver,
      final Callable<T> execute,
      final FunctionType<Exception, T> on_exception) {

    this.logger =
        NullCheck.notNull(logger, "Logger");
    this.receiver =
        NullCheck.notNull(receiver, "Receiver");
    this.execute =
        NullCheck.notNull(execute, "Execute");
    this.on_exception =
        NullCheck.notNull(on_exception, "On Exception");
  }

  @Override
  public T call()
      throws Exception {
    try {
      final T x = this.execute.call();
      this.receiver.send(x);
      return x;
    } catch (final Exception e) {
      this.logger.error("task raised exception: ", e);
      this.receiver.send(this.on_exception.call(e));
      throw e;
    }
  }
}
