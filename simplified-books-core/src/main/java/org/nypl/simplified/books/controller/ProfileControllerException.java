package org.nypl.simplified.books.controller;

import com.io7m.jnull.NullCheck;

public abstract class ProfileControllerException extends RuntimeException {

  public ProfileControllerException(final String message) {
    super(NullCheck.notNull(message, "message"));
  }

  public ProfileControllerException(final String message, final Throwable cause) {
    super(NullCheck.notNull(message, "message"), NullCheck.notNull(cause, "cause"));
  }

  public ProfileControllerException(final Throwable cause) {
    super(NullCheck.notNull(cause, "cause"));
  }
}
