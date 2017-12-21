package org.nypl.simplified.books.accounts;

import com.google.auto.value.AutoValue;
import com.io7m.jfunctional.Option;
import com.io7m.jfunctional.OptionType;

import org.nypl.simplified.http.core.HTTPOAuthToken;
import org.nypl.simplified.opds.core.DRMLicensor;

import java.util.Collections;
import java.util.Set;

/**
 * <p>A set of account credentials.</p>
 * <p>At a minimum, these contain an account barcode (username) and a PIN (password). Other credentials
 * may however be present, such as an OAuth token or DRM-specific information.</p>
 */

@AutoValue
public abstract class AccountAuthenticationCredentials {

  AccountAuthenticationCredentials() {

  }

  /**
   * @return A new account credentials builder
   */

  public static Builder builder(
      final AccountPIN pin,
      final AccountBarcode barcode) {

    return new AutoValue_AccountAuthenticationCredentials.Builder()
        .setPin(pin)
        .setBarcode(barcode)
        .setOAuthToken(Option.<HTTPOAuthToken>none())
        .setAuthenticationProvider(Option.<AccountAuthenticationProvider>none())
        .setPatron(Option.<AccountPatron>none())
        .setAdobeCredentials(Option.<AccountAuthenticationAdobeCredentials>none());
  }

  /**
   * @return The account PIN
   */

  public abstract AccountPIN pin();

  /**
   * @return The account barcode
   */

  public abstract AccountBarcode barcode();

  /**
   * @return The OAuth token, if one is present
   */

  public abstract OptionType<HTTPOAuthToken> oAuthToken();

  /**
   * @return The Adobe credentials, if any are present
   */

  public abstract OptionType<AccountAuthenticationAdobeCredentials> adobeCredentials();

  /**
   * @return The authentication provider, if any
   */

  public abstract OptionType<AccountAuthenticationProvider> authenticationProvider();

  /**
   * @return The patron information, if any
   */

  public abstract OptionType<AccountPatron> patron();

  /**
   * @return The current value as a mutable builder
   */

  public abstract Builder toBuilder();

  /**
   * A mutable builder for the type.
   */

  @AutoValue.Builder
  public abstract static class Builder {

    Builder() {

    }

    /**
     * @param pin The PIN value
     * @return The current builder
     * @see #pin()
     */

    public abstract Builder setPin(
        AccountPIN pin);

    /**
     * @param barcode The barcode value
     * @return The current builder
     * @see #barcode()
     */

    public abstract Builder setBarcode(
        AccountBarcode barcode);

    /**
     * @param token The token value
     * @return The current builder
     * @see #oAuthToken()
     */

    public abstract Builder setOAuthToken(
        OptionType<HTTPOAuthToken> token);

    /**
     * @param credentials The credentials
     * @return The current builder
     * @see #adobeCredentials()
     */

    public abstract Builder setAdobeCredentials(
        OptionType<AccountAuthenticationAdobeCredentials> credentials);

    /**
     * @param provider The provider
     * @return The current builder
     * @see #authenticationProvider()
     */

    public abstract Builder setAuthenticationProvider(
        OptionType<AccountAuthenticationProvider> provider);

    /**
     * @param patron The patron
     * @return The current builder
     * @see #patron()
     */

    public abstract Builder setPatron(
        OptionType<AccountPatron> patron);

    /**
     * @return A constructed set of credentials
     */

    public abstract AccountAuthenticationCredentials build() ;
  }
}
