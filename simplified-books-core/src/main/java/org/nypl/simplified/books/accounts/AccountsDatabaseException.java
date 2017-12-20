package org.nypl.simplified.books.accounts;

import com.io7m.jnull.NullCheck;

import java.util.List;

public final class AccountsDatabaseException extends Exception {

  private final List<Exception> causes;

  public AccountsDatabaseException(
      final String message,
      final List<Exception> causes) {
    super(NullCheck.notNull(message, "Message"));
    this.causes = NullCheck.notNull(causes, "Causes");
  }

  public List<Exception> causes() {
    return this.causes;
  }
}
