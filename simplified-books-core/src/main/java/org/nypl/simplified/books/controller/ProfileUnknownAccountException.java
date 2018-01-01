package org.nypl.simplified.books.controller;

/**
 * An exception raised by the user specifying an unknown account.
 */

public final class ProfileUnknownAccountException extends ProfileControllerException {

  /**
   * Construct an exception.
   *
   * @param message The message
   * @param cause   The cause
   */

  public ProfileUnknownAccountException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
