package org.nypl.simplified.books.accounts;

import com.google.auto.value.AutoValue;

import java.net.URI;
import java.util.Collections;
import java.util.SortedMap;

/**
 * <p>An immutable account provider collection.</p>
 */

@AutoValue
public abstract class AccountProviderCollection {

  AccountProviderCollection() {

  }

  /**
   * Construct a provider collection.
   *
   * @param in_default_provider The default provider
   * @param in_providers        The available providers
   *
   * @throws IllegalArgumentException If the default provider is not present in the available providers
   */

  public static AccountProviderCollection create(
      final AccountProvider in_default_provider,
      final SortedMap<URI, AccountProvider> in_providers)
      throws IllegalArgumentException {
    return new AutoValue_AccountProviderCollection(
        in_default_provider, Collections.unmodifiableSortedMap(in_providers));
  }

  /**
   * @return The default account provider
   */

  public abstract AccountProvider providerDefault();

  /**
   * @return The account providers
   */

  public abstract SortedMap<URI, AccountProvider> providers();
}
