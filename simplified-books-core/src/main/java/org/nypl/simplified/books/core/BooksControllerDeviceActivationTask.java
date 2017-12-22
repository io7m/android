package org.nypl.simplified.books.core;

import com.io7m.jfunctional.Option;
import com.io7m.jfunctional.OptionType;
import com.io7m.jfunctional.Some;

import org.nypl.drm.core.AdobeAdeptActivationReceiverType;
import org.nypl.drm.core.AdobeAdeptConnectorType;
import org.nypl.drm.core.AdobeAdeptExecutorType;
import org.nypl.drm.core.AdobeAdeptProcedureType;
import org.nypl.drm.core.AdobeDeviceID;
import org.nypl.drm.core.AdobeUserID;
import org.nypl.drm.core.AdobeVendorID;
import org.nypl.simplified.books.accounts.AccountAuthenticationAdobeClientToken;
import org.nypl.simplified.books.accounts.AccountAuthenticationAdobePostActivationCredentials;
import org.nypl.simplified.books.accounts.AccountAuthenticationAdobePreActivationCredentials;
import org.nypl.simplified.books.accounts.AccountAuthenticationCredentials;
import org.nypl.simplified.http.core.HTTP;
import org.nypl.simplified.http.core.HTTPAuthBasic;
import org.nypl.simplified.http.core.HTTPAuthType;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Runnable that JUST activates the device with Adobe (used on startup, and as part of logging in)
 */

public class BooksControllerDeviceActivationTask implements Runnable {

  private final OptionType<AdobeAdeptExecutorType> adobe_drm;
  private final AccountAuthenticationCredentials credentials;
  private final AccountsDatabaseType accounts_database;
  private final DeviceActivationListenerType device_activation_listener;

  private static final Logger LOG;

  static {
    LOG = LogUtilities.getLog(BooksControllerDeviceActivationTask.class);
  }

  BooksControllerDeviceActivationTask(
      final OptionType<AdobeAdeptExecutorType> in_adobe_drm,
      final AccountAuthenticationCredentials in_credentials,
      final AccountsDatabaseType in_accounts_database,
      final DeviceActivationListenerType in_device_activation_listener) {

    this.adobe_drm = in_adobe_drm;
    this.credentials = in_credentials;
    this.accounts_database = in_accounts_database;
    this.device_activation_listener = in_device_activation_listener;
  }

  @Override
  public void run() {

    if (this.adobe_drm.isNone()) {
      LOG.debug("no adobe drm, aborting!");
      return;
    }

    final Some<AdobeAdeptExecutorType> some = (Some<AdobeAdeptExecutorType>) this.adobe_drm;
    final AdobeAdeptExecutorType adobe_exec = some.get();

    if (this.credentials.adobeCredentials().isNone()) {
      LOG.debug("aborting activation: no adobe credentials");
      return;
    }

    final AccountAuthenticationAdobePreActivationCredentials adobe_creds_pre =
        ((Some<AccountAuthenticationAdobePreActivationCredentials>) this.credentials.adobeCredentials()).get();

    final AccountAuthenticationAdobeClientToken adobe_token = adobe_creds_pre.clientToken();

    /*
     * An activation listener that records the post-activation credentials to the database in the
     * case of a successful activation.
     */

    final AdobeAdeptActivationReceiverType activation_listener =
        new AdobeAdeptActivationReceiverType() {

          @Override
          public void onActivationsCount(int count) {

        /*
         * Device activation succeeded.
         */

            LOG.debug("Activation  count: {}", count);
          }

          @Override
          public void onActivation(
              final int index,
              final AdobeVendorID authority,
              final AdobeDeviceID device_id,
              final String user_name,
              final AdobeUserID user_id,
              final String expires) {

            LOG.debug("Activation [{}]: authority: {}", index, authority);
            LOG.debug("Activation [{}]: device_id: {}", index, device_id);
            LOG.debug("Activation [{}]: user_name: {}", index, user_name);
            LOG.debug("Activation [{}]: user_id: {}", index, user_id);
            LOG.debug("Activation [{}]: expires: {}", index, expires);

            /*
             * Device activation succeeded. Record the post activation credentials to the database.
             */

            final AccountAuthenticationAdobePostActivationCredentials new_post_creds =
                AccountAuthenticationAdobePostActivationCredentials.create(device_id, user_id);

            final AccountAuthenticationCredentials new_creds =
                credentials.toBuilder()
                    .setAdobeCredentials(adobe_creds_pre.withPostActivationCredentials(new_post_creds))
                    .build();

            try {
              accounts_database.accountSetCredentials(new_creds);
            } catch (final IOException e) {
              LOG.error("could not save credentials: ", e);
            }

            contactDeviceManager(adobe_creds_pre, new_post_creds);
            device_activation_listener.onDeviceActivationSuccess();
          }

          @Override
          public void onActivationError(
              final String error) {
            LOG.debug("Failed to activate device: {}", error);
            device_activation_listener.onDeviceActivationFailure(error);
          }
        };

    /*
     * Execute the device activation.
     */

    adobe_exec.execute(
        new AdobeAdeptProcedureType() {
          @Override
          public void executeWith(final AdobeAdeptConnectorType c) {
            c.activateDevice(
                activation_listener,
                adobe_creds_pre.vendorID(),
                adobe_token.tokenUserName(),
                adobe_token.tokenPassword());
          }
        });
  }

  /**
   * Call the remove device manager and tell it about the new activation.
   */

  private void contactDeviceManager(
      final AccountAuthenticationAdobePreActivationCredentials adobe_creds_pre,
      final AccountAuthenticationAdobePostActivationCredentials adobe_creds_post)
  {
    LOG.debug("contactDeviceManager: {}", adobe_creds_pre.deviceManagerURI());

    /*
     * XXX: Does the device manager only support Basic authentication? The previous maintainer
     *      did not do the usual copy/paste of the code that creates HTTP auth instances, so I've
     *      stuck to only using Basic auth here.
     */

    final OptionType<HTTPAuthType> http_auth =
        Option.some((HTTPAuthType) HTTPAuthBasic.create(
            credentials.barcode().value(),
            credentials.pin().value()));

    HTTP.newHTTP().post(
        http_auth,
        adobe_creds_pre.deviceManagerURI(),
        adobe_creds_post.deviceID().getValue().getBytes(StandardCharsets.US_ASCII),
        "vnd.librarysimplified/drm-device-id-list");
  }
}
