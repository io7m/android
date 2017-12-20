package org.nypl.simplified.books.accounts;

import com.io7m.jnull.NullCheck;

import java.net.URI;

/**
 * A description of an account.
 */

public final class AccountDescription {

  private final URI provider;

  private AccountDescription(
      final URI provider) {
    this.provider = NullCheck.notNull(provider, "provider");
  }

  public static AccountDescription create(
      final URI provider) {
    return new AccountDescription(provider);
  }

  public URI provider() {
    return this.provider;
  }
}
