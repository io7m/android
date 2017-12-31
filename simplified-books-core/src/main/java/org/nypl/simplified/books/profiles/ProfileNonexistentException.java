package org.nypl.simplified.books.profiles;

public final class ProfileNonexistentException extends ProfileDatabaseUsageException {

  /**
   * Construct an exception.
   *
   * @param message The exception message
   */

  public ProfileNonexistentException(final String message) {
    super(message);
  }
}
