package org.nypl.simplified.books.accounts;

import java.net.URI;
import java.util.SortedMap;

/**
 * A collection of account providers.
 *
 * @see AccountProvider
 */

public interface AccountProviderCollectionType {

  /**
   * @return The available account providers
   */

  SortedMap<URI, AccountProvider> providers();

  /**
   * @return The default account provider that will be used to create new profiles
   */

  AccountProvider defaultProvider();
}
