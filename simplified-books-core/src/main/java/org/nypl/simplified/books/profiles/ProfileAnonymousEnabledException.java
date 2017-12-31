package org.nypl.simplified.books.profiles;

public final class ProfileAnonymousEnabledException extends ProfileDatabaseUsageException {

  /**
   * Construct an exception.
   *
   * @param message The exception message
   */

  public ProfileAnonymousEnabledException(final String message) {
    super(message);
  }
}
