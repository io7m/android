package org.nypl.simplified.books.core;

import org.nypl.simplified.books.accounts.AccountAuthenticationCredentials;

/**
 * The type of listeners for receiving cached credentials.
 */

public interface AccountGetCachedCredentialsListenerType {

  /**
   * The account is not logged in.
   */

  void onAccountIsNotLoggedIn();

  /**
   * The account is logged in with the given credentials.
   *
   * @param credentials The current credentials
   */

  void onAccountIsLoggedIn(
      AccountAuthenticationCredentials credentials);
}
