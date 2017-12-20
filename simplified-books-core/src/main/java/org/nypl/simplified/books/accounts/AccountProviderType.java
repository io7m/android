package org.nypl.simplified.books.accounts;

import com.io7m.jfunctional.OptionType;

import java.net.URI;

/**
 * <p>The interface exposed by account providers.</p>
 * <p>An account provider supplies branding information and defines various
 * aspects of the account such as whether or not syncing of bookmarks to
 * a remote server is supported, etc. Account providers may be registered
 * statically within the application as resources, or may be fetched from
 * a remote server. Account providers are identified by opaque account
 * provider URIs. Each account stores the identifier of the
 * account provider with which it is associated. It is an error to depend on
 * the values of identifiers for any kind of program logic.</p>
 */

public interface AccountProviderType {

  /**
   * @return The account provider URI
   */

  URI id();

  /**
   * @return The display name
   */

  String displayName();

  /**
   * @return The subtitle
   */

  String subtitle();

  /**
   * @return The logo URI
   */

  URI logo();

  /**
   * @return An authentication description if authentication is required, or nothing if it isn't
   */

  OptionType<AccountProviderAuthenticationDescriptionType> authentication();

  /**
   * @return {@code true} iff the SimplyE synchronization is supported
   */

  boolean supportsSimplyESynchronization();

  /**
   * @return {@code true} iff the barcode scanner is supported
   */

  boolean supportsBarcodeScanner();

  /**
   * @return {@code true} iff the barcode display is supported
   */

  boolean supportsBarcodeDisplay();

  /**
   * @return {@code true} iff reservations are supported
   */

  boolean supportsReservations();

  /**
   * @return {@code true} iff the card creator is supported
   */

  boolean supportsCardCreator();

  /**
   * @return {@code true} iff the help center is supported
   */

  boolean supportsHelpCenter();

  /**
   * @return The base URI of the catalog
   */

  URI catalogURI();

  /**
   * The Over-13s catalog URI.
   *
   * @return The URI of the catalog for readers over the age of 13
   */

  OptionType<URI> catalogURIForOver13s();

  /**
   * @return The URI of the catalog for readers under the age of 13
   */

  OptionType<URI> catalogURIForUnder13s();

  /**
   * @return The support email address
   */

  String supportEmail();

  /**
   * @return The URI of the EULA if one is required
   */

  OptionType<URI> eula();

  /**
   * @return The URI of the EULA if one is required
   */

  OptionType<URI> license();

  /**
   * @return The URI of the privacy policy if one is required
   */

  OptionType<URI> privacyPolicy();

  /**
   * @return The main color used to decorate the application when using this provider
   */

  String mainColor();
}
