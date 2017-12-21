package org.nypl.simplified.books.accounts;

import com.google.auto.value.AutoValue;

/**
 * A token used by Adobe DRM to activate and deactivate devices.
 */

@AutoValue
public abstract class AccountAdobeDeviceToken {

  AccountAdobeDeviceToken() {

  }

  /**
   * Construct a patron.
   *
   * @param in_value The raw patron value
   */

  public static AccountAdobeDeviceToken create(
      final String in_value) {
    return new AutoValue_AccountAdobeDeviceToken(in_value);
  }

  /**
   * @return The raw token value
   */

  public abstract String value();

  @Override
  public String toString() {
    return this.value();
  }

}
