package org.nypl.simplified.books.accounts;

import java.io.File;
import java.net.URI;

/**
 * <p>The interface exposed by accounts.</p>
 * <p>An account aggregates a set of credentials and a book database.
 * Account are assigned monotonically increasing identifiers by the
 * application, but the identifiers themselves carry no meaning. It is
 * an error to depend on the values of identifiers for any kind of
 * program logic.</p>
 */

public interface AccountType {

  /**
   * @return The account ID
   */

  AccountID id();

  /**
   * @return The full path to the on-disk directory storing data for this account
   */

  File directory();

  /**
   * @return The URI of the account provider associated with this account
   */

  URI provider();
}
