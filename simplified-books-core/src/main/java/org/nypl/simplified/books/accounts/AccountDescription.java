package org.nypl.simplified.books.accounts;

import com.google.auto.value.AutoValue;

import java.net.URI;

/**
 * A description of an account.
 */

@AutoValue
public abstract class AccountDescription {

  AccountDescription() {

  }

  /**
   * Create an account description.
   *
   * @param provider The account provider ID
   * @return An account description
   */

  public static AccountDescription create(final URI provider) {
    return new AutoValue_AccountDescription(provider);
  }

  /**
   * @return The account provider associated with the account
   */

  public abstract URI provider();
}
