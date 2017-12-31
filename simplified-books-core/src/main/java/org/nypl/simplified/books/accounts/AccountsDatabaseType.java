package org.nypl.simplified.books.accounts;

import org.nypl.simplified.books.profiles.ProfileType;

import java.io.File;
import java.net.URI;
import java.util.SortedMap;

/**
 * <p>The interface exposed by the accounts database.</p>
 * <p>
 * An account database stores all of the accounts currently known to
 * a profile.</p>
 */

public interface AccountsDatabaseType {

  /**
   * @return The directory containing the on-disk accounts database
   */

  File directory();

  /**
   * @return A read-only view of the current accounts
   */

  SortedMap<AccountID, AccountType> accounts();

  /**
   * @return A read-only view of the current accounts by their provider
   */

  SortedMap<URI, AccountType> accountsByProvider();

  /**
   * Create an account using the given account provider.
   *
   * @param account_provider The account provider for the default account
   * @return A newly created account
   * @throws AccountsDatabaseException On account creation errors
   */

  AccountType createAccount(
      AccountProvider account_provider)
      throws AccountsDatabaseException;
}
