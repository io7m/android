package org.nypl.simplified.books.controller;

/**
 * An exception raised by the user attempting to perform an operation without a current profile.
 */

public final class ProfileNoProfileIsCurrentException extends ProfileControllerException {

  /**
   * Construct an exception.
   *
   * @param message The message
   * @param cause   The cause
   */

  public ProfileNoProfileIsCurrentException(
      final String message,
      final Exception cause) {
    super(message, cause);
  }
}
