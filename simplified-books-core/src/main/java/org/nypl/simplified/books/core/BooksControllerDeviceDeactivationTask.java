package org.nypl.simplified.books.core;

import com.io7m.jfunctional.Option;
import com.io7m.jfunctional.OptionType;
import com.io7m.jfunctional.Some;
import com.io7m.jnull.NullCheck;

import org.nypl.drm.core.AdobeAdeptConnectorType;
import org.nypl.drm.core.AdobeAdeptDeactivationReceiverType;
import org.nypl.drm.core.AdobeAdeptExecutorType;
import org.nypl.drm.core.AdobeAdeptProcedureType;
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

/**
 * A task that deactivates the current Adobe device.
 */

public final class BooksControllerDeviceDeactivationTask implements Runnable {

  private static final Logger LOG;

  static {
    LOG = LogUtilities.getLog(BooksControllerDeviceDeactivationTask.class);
  }

  private final OptionType<AdobeAdeptExecutorType> adobe_drm;
  private final AccountsDatabaseType accounts_database;
  private final AdobeAdeptDeactivationReceiverType deactivation_listener;

  BooksControllerDeviceDeactivationTask(
      final OptionType<AdobeAdeptExecutorType> in_adobe_drm,
      final AccountsDatabaseType in_accounts_database,
      final AdobeAdeptDeactivationReceiverType in_deactivation_listener) {

    this.adobe_drm =
        NullCheck.notNull(in_adobe_drm, "in_adobe_drm");
    this.accounts_database =
        NullCheck.notNull(in_accounts_database, "in_accounts_database");
    this.deactivation_listener =
        NullCheck.notNull(in_deactivation_listener, "in_deactivation_listener");
  }

  @Override
  public void run() {

    if (this.adobe_drm.isNone()) {
      LOG.debug("aborting deactivation: no Adobe DRM support");
      return;
    }

    final Some<AdobeAdeptExecutorType> some = (Some<AdobeAdeptExecutorType>) this.adobe_drm;
    final AdobeAdeptExecutorType adobe_exec = some.get();

    final OptionType<AccountAuthenticationCredentials> credentials_opt =
        this.accounts_database.accountGetCredentials();

    if (credentials_opt.isNone()) {
      LOG.debug("aborting deactivation: no credentials");
      return;
    }

    final AccountAuthenticationCredentials credentials =
        ((Some<AccountAuthenticationCredentials>) credentials_opt).get();

    if (credentials.hasActivatedAdobeDevice()) {
      LOG.debug("aborting deactivation: no activated Adobe credentials");
      return;
    }

    final AccountAuthenticationAdobePreActivationCredentials adobe_creds_pre =
        ((Some<AccountAuthenticationAdobePreActivationCredentials>) credentials.adobeCredentials()).get();
    final AccountAuthenticationAdobePostActivationCredentials adobe_creds_post =
        ((Some<AccountAuthenticationAdobePostActivationCredentials>) credentials.adobePostActivationCredentials()).get();

    final AccountAuthenticationAdobeClientToken adobe_token = adobe_creds_pre.clientToken();

    /*
     * A deactivation listener that deletes the post-activation credentials if deactivation
     * succeeds.
     */

    final AdobeAdeptDeactivationReceiverType listener =
        new AdobeAdeptDeactivationReceiverType() {

          @Override
          public void onDeactivationError(final String message) {
            LOG.debug("Failed to deactivate device: {}", message);
            deactivation_listener.onDeactivationError(message);
          }

          @Override
          public void onDeactivationSucceeded() {

            /*
             * Device deactivation succeeded. Remove the post-activation credentials from the
             * current set of credentials and save them.
             */

            final AccountAuthenticationCredentials updated_credentials =
                credentials.toBuilder()
                    .setAdobeCredentials(adobe_creds_pre.withoutPostActivationCredentials())
                    .build();

            try {
              accounts_database.accountSetCredentials(updated_credentials);
            } catch (final IOException e) {
              LOG.error("could not save credentials: ", e);
            }

            /*
             * Call the device manager and tell it about the deactivation.
             */

            contactDeviceManager(credentials, adobe_creds_pre, adobe_creds_post);
            deactivation_listener.onDeactivationSucceeded();
          }
        };

    /*
     * Run the device deactivation.
     */

    adobe_exec.execute(
        new AdobeAdeptProcedureType() {
          @Override
          public void executeWith(final AdobeAdeptConnectorType c) {
            c.deactivateDevice(
                listener,
                adobe_creds_pre.vendorID(),
                adobe_creds_post.userID(),
                adobe_token.tokenUserName(),
                adobe_token.tokenPassword());
          }
        });
  }

  private void contactDeviceManager(
      final AccountAuthenticationCredentials credentials,
      final AccountAuthenticationAdobePreActivationCredentials adobe_creds_pre,
      final AccountAuthenticationAdobePostActivationCredentials adobe_creds_post)
  {
    LOG.debug("contacting device manager");

    final URI uri = URI.create(
        adobe_creds_pre.deviceManagerURI() + "/" + adobe_creds_post.deviceID().getValue());
    LOG.debug("device URI: %s", uri);

    final OptionType<HTTPAuthType> http_auth =
        Option.some((HTTPAuthType) HTTPAuthBasic.create(
            credentials.barcode().value(),
            credentials.pin().value()));

    HTTP.newHTTP().delete(http_auth, uri, "vnd.librarysimplified/drm-device-id-list");
  }
}
