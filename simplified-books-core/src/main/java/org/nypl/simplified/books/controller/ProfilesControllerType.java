package org.nypl.simplified.books.controller;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.io7m.jfunctional.Unit;

import org.joda.time.LocalDate;
import org.nypl.simplified.books.accounts.AccountAuthenticationCredentials;
import org.nypl.simplified.books.accounts.AccountEvent;
import org.nypl.simplified.books.accounts.AccountEvent.AccountCreationEvent;
import org.nypl.simplified.books.accounts.AccountEvent.AccountDeletionEvent;
import org.nypl.simplified.books.accounts.AccountEvent.AccountLoginEvent;
import org.nypl.simplified.books.accounts.AccountProvider;
import org.nypl.simplified.books.profiles.ProfileAccountSelectEvent;
import org.nypl.simplified.books.profiles.ProfileEvent;
import org.nypl.simplified.books.profiles.ProfileCreationEvent;
import org.nypl.simplified.books.profiles.ProfileID;
import org.nypl.simplified.books.profiles.ProfileReadableType;
import org.nypl.simplified.books.profiles.ProfilesDatabaseType;
import org.nypl.simplified.observable.ObservableReadableType;

import java.net.URI;
import java.util.SortedMap;

/**
 * The profiles controller.
 */

public interface ProfilesControllerType {

  /**
   * @return A read-only view of the current profiles
   */

  SortedMap<ProfileID, ProfileReadableType> profiles();

  /**
   * @return {@link ProfilesDatabaseType.AnonymousProfileEnabled#ANONYMOUS_PROFILE_ENABLED} if the anonymous profile is enabled
   */

  ProfilesDatabaseType.AnonymousProfileEnabled profileAnonymousEnabled();

  /**
   * @return The most recently selected profile, or the anonymous profile if it is enabled
   * @throws ProfileNoProfileIsCurrentException If the anonymous profile is disabled and no profile has been selected
   * @see #profileSelect(ProfileID)
   * @see #profileAnonymousEnabled()
   */

  ProfileReadableType profileCurrent()
      throws ProfileNoProfileIsCurrentException;

  /**
   * @return The account provider corresponding to the current account in the current profile
   * @throws ProfileNoProfileIsCurrentException     If the anonymous profile is disabled and no profile has been selected
   * @throws ProfileUnknownAccountProviderException If the current account refers to an account provider that is not in the current set of known account providers
   * @see #profileSelect(ProfileID)
   * @see #profileAnonymousEnabled()
   */

  AccountProvider profileAccountProviderCurrent()
      throws ProfileNoProfileIsCurrentException,
      ProfileUnknownAccountProviderException;

  /**
   * @return An observable that publishes profile events
   */

  ObservableReadableType<ProfileEvent> profileEvents();

  /**
   * Create a profile, asynchronously, and return a profile event.
   *
   * @param account_provider The account provider used to create the default account
   * @param display_name     The profile display name
   * @param date             The date of birth for the profile
   * @return A future that returns a status value
   */

  ListenableFuture<ProfileCreationEvent> profileCreate(
      AccountProvider account_provider,
      String display_name,
      LocalDate date);

  /**
   * Set the given profile as the current profile. The operation always succeeds if a profile
   * exists with the given ID.
   *
   * @param id The profile ID
   * @return A future that returns unit
   */

  ListenableFuture<Unit> profileSelect(
      ProfileID id);

  /**
   * Determine the URI for the root of the catalog in the current account in the current profile.
   * This method returns the correct URI based on the preferences for the current profile (for
   * example, some catalogs have different addresses for readers under the age of 13, so the URI
   * selected is dependent on the date of birth specified in the profile).
   *
   * @return The URI for the root of the catalog in the current account
   * @throws ProfileNoProfileIsCurrentException     If the anonymous profile is disabled and no profile has been selected
   * @throws ProfileUnknownAccountProviderException If the current account refers to an account provider that is not in the current set of known account providers
   * @see #profileSelect(ProfileID)
   * @see #profileAnonymousEnabled()
   */

  URI profileCurrentCatalogRootURI()
      throws ProfileNoProfileIsCurrentException, ProfileUnknownAccountProviderException;

  /**
   * Attempt to login using the current account of the current profile. The login is attempted
   * using the given credentials.
   *
   * @param credentials The credentials
   * @return A future that returns a login event
   */

  ListenableFuture<AccountLoginEvent> profileAccountLogin(
      AccountAuthenticationCredentials credentials);

  /**
   * Create an account using the given account provider. The operation will fail if
   * an account already exists using the given provider.
   *
   * @param provider The account provider ID
   * @return A future that returns a login event
   */

  ListenableFuture<AccountCreationEvent> profileAccountCreate(
      URI provider);

  /**
   * Create an account using the given account provider. The operation will fail if
   * an account does not exist using the given provider, or if deleting the account would result
   * in there being no accounts left.
   *
   * @param provider The account provider ID
   * @return A future that returns a login event
   */

  ListenableFuture<AccountDeletionEvent> profileAccountDeleteByProvider(
      URI provider);

  /**
   * Switch the current account of the current profile to the one created by the given provider.
   *
   * @param provider The account provider ID
   */

  ListenableFuture<ProfileAccountSelectEvent> profileAccountSelectByProvider(
      URI provider);

  /**
   * @return An observable that publishes account events
   */

  ObservableReadableType<AccountEvent> accountEvents();

  /**
   * @return A list of all of the account providers used by the current profile
   * @throws ProfileNoProfileIsCurrentException     If the anonymous profile is disabled and no profile has been selected
   * @throws ProfileUnknownAccountProviderException If the current account refers to an account provider that is not in the current set of known account providers
   * @see #profileSelect(ProfileID)
   * @see #profileAnonymousEnabled()
   */

  ImmutableList<AccountProvider> profileCurrentlyUsedAccountProviders()
      throws ProfileNoProfileIsCurrentException, ProfileUnknownAccountProviderException;
}
