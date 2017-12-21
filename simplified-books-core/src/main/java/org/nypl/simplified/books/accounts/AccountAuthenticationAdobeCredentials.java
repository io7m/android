package org.nypl.simplified.books.accounts;

import com.google.auto.value.AutoValue;

/**
 * <p>A set of account Adobe-specific credentials.</p>
 * <p>The credentials are split into pre and post activation credentials; some values are not
 * known until the device has been activated and the type system is used here to indicate this.</p>
 *
 * @see AccountAuthenticationAdobePreActivationCredentials
 * @see AccountAuthenticationAdobePostActivationCredentials
 */

@AutoValue
public abstract class AccountAuthenticationAdobeCredentials {

  AccountAuthenticationAdobeCredentials() {

  }

  /**
   * Create a set of credentials.
   *
   * @param creds The initial credentials
   * @return A set of credentials
   */

  public static AccountAuthenticationAdobeCredentials create(
      AccountAuthenticationAdobePreActivationCredentials creds) {
    return new AutoValue_AccountAuthenticationAdobeCredentials(creds);
  }

  /**
   * @return The initially known credentials
   */

  public abstract AccountAuthenticationAdobePreActivationCredentials preActivationCredentials();
}
