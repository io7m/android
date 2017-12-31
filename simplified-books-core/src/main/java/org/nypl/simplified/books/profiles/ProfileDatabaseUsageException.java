package org.nypl.simplified.books.profiles;

import com.io7m.jnull.NullCheck;

import java.util.List;

/**
 * An exception that indicates that the user attempted to misuse the database in some way.
 */

public abstract class ProfileDatabaseUsageException extends RuntimeException {

  /**
   * Construct an exception.
   *
   * @param message The exception message
   */

  public ProfileDatabaseUsageException(final String message) {
    super(NullCheck.notNull(message, "Message"));
  }
}
