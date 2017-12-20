package org.nypl.simplified.books.profiles;

import com.io7m.jnull.NullCheck;

import java.util.List;

public final class ProfileDatabaseException extends Exception {

  private final List<Exception> causes;

  public ProfileDatabaseException(
      final String message,
      final List<Exception> causes) {
    super(NullCheck.notNull(message, "Message"));
    this.causes = NullCheck.notNull(causes, "Causes");
  }

  public List<Exception> causes() {
    return this.causes;
  }
}
