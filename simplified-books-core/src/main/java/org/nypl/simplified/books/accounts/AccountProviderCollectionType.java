package org.nypl.simplified.books.accounts;

import java.net.URI;
import java.util.SortedMap;

/**
 * A collection of account providers.
 *
 * @see AccountProviderType
 */

public interface AccountProviderCollectionType {

  /**
   * @return The available account providers
   */

  SortedMap<URI, AccountProviderType> providers();

  /**
   * @return The default account provider that will be used to create new profiles
   */

  AccountProviderType defaultProvider();
}
