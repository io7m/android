package org.nypl.simplified.books.controller;

/**
 * An exception raised by the user specifying an unknown account provider.
 */

public final class ProfileUnknownAccountProviderException extends ProfileControllerException {

  /**
   * Construct an exception.
   *
   * @param message The message
   */

  public ProfileUnknownAccountProviderException(final String message) {
    super(message);
  }
}