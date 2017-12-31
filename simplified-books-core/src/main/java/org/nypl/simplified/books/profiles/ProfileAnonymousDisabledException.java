package org.nypl.simplified.books.profiles;

public final class ProfileAnonymousDisabledException extends ProfileDatabaseUsageException {

  /**
   * Construct an exception.
   *
   * @param message The exception message
   */

  public ProfileAnonymousDisabledException(final String message) {
    super(message);
  }
}
