package org.nypl.simplified.books.accounts;

import com.google.auto.value.AutoValue;
import com.io7m.jfunctional.Option;
import com.io7m.jfunctional.OptionType;

import org.nypl.drm.core.AdobeDeviceID;
import org.nypl.drm.core.AdobeUserID;
import org.nypl.drm.core.AdobeVendorID;

/**
 * <p>A set of account Adobe-specific credentials.</p>
 */

@AutoValue
public abstract class AccountAuthenticationAdobeCredentials {

  AccountAuthenticationAdobeCredentials() {

  }

  /**
   * Create a set of credentials.
   *
   * @param vendor_id The vendor ID
   * @param user_id   The user ID
   * @param device_id The device ID
   * @return A set of credentials
   */

  public static AccountAuthenticationAdobeCredentials create(
      final AdobeVendorID vendor_id,
      final AdobeUserID user_id,
      final AdobeDeviceID device_id) {
    return builder(vendor_id, user_id, device_id).build();
  }

  /**
   * @return A new account credentials builder
   */

  public static Builder builder(
      final AdobeVendorID vendor,
      final AdobeUserID user,
      final AdobeDeviceID device) {

    return new AutoValue_AccountAuthenticationAdobeCredentials.Builder()
        .setVendorID(vendor)
        .setUserID(user)
        .setDeviceID(device)
        .setDeviceToken(Option.<AccountAdobeDeviceToken>none());
  }

  /**
   * @return The vendor ID
   */

  public abstract AdobeVendorID vendorID();

  /**
   * @return The user ID
   */

  public abstract AdobeUserID userID();

  /**
   * @return The device ID
   */

  public abstract AdobeDeviceID deviceID();

  /**
   * @return The device token
   */

  public abstract OptionType<AccountAdobeDeviceToken> deviceToken();

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
     * @param id The vendor ID
     * @return The current builder
     * @see #vendorID()
     */

    public abstract Builder setVendorID(
        AdobeVendorID id);

    /**
     * @param id The user ID
     * @return The current builder
     * @see #userID()
     */

    public abstract Builder setUserID(
        AdobeUserID id);

    /**
     * @param id The device ID
     * @return The current builder
     * @see #deviceID()
     */

    public abstract Builder setDeviceID(
        AdobeDeviceID id);

    /**
     * @param token The device token
     * @return The current builder
     * @see #deviceToken()
     */

    public abstract Builder setDeviceToken(
        OptionType<AccountAdobeDeviceToken> token);

    /**
     * @return A constructed set of credentials
     */

    public abstract AccountAuthenticationAdobeCredentials build();
  }
}
