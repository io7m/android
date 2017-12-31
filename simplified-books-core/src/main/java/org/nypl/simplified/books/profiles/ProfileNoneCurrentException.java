package org.nypl.simplified.books.profiles;

public final class ProfileNoneCurrentException extends ProfileDatabaseUsageException {

  /**
   * Construct an exception.
   *
   * @param message The exception message
   */

  public ProfileNoneCurrentException(final String message) {
    super(message);
  }
}
