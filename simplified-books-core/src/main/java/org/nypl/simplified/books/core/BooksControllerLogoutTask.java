package org.nypl.simplified.books.core;

import com.io7m.jfunctional.Option;
import com.io7m.jfunctional.OptionType;
import com.io7m.jnull.NullCheck;

import org.nypl.drm.core.AdobeAdeptDeactivationReceiverType;
import org.nypl.drm.core.AdobeAdeptExecutorType;
import org.nypl.simplified.books.accounts.AccountAuthenticationCredentials;
import org.slf4j.Logger;

import java.io.IOException;

final class BooksControllerLogoutTask implements Runnable {
  private static final Logger LOG;

  static {
    LOG = LogUtilities.getLog(BooksControllerLogoutTask.class);
  }

  private final AccountLogoutListenerType listener;
  private final OptionType<AdobeAdeptExecutorType> adobe_drm;
  private final BookDatabaseType database;
  private final AccountsDatabaseType accounts_database;
  private final AccountAuthenticationCredentials credentials;

  BooksControllerLogoutTask(
      final BookDatabaseType in_book_database,
      final AccountsDatabaseType in_accounts_database,
      final OptionType<AdobeAdeptExecutorType> in_adobe_drm,
      final AccountLogoutListenerType in_listener,
      final AccountAuthenticationCredentials in_credentials) {

    this.database = NullCheck.notNull(in_book_database);
    this.adobe_drm = NullCheck.notNull(in_adobe_drm);
    this.accounts_database = NullCheck.notNull(in_accounts_database);
    this.listener = new AccountLogoutListenerCatcher(LOG, NullCheck.notNull(in_listener));
    this.credentials = NullCheck.notNull(in_credentials);
  }

  private void deactivateDevice() {

    /*
     * If an Adobe DRM implementation is available, activate the device
     * with the credentials. If the Adobe server rejects the credentials,
     * then the login attempt is still considered to have failed.
     */

    if (this.adobe_drm.isSome() && this.credentials.hasActivatedAdobeDevice()) {

      final AdobeAdeptDeactivationReceiverType deactivation_listener =
          new AdobeAdeptDeactivationReceiverType() {

            @Override
            public void onDeactivationError(final String message) {
              onDeviceDeactivationFailure(message);
            }

            @Override
            public void onDeactivationSucceeded() {
              onDeviceDeactivationSucceeded();
            }
          };

      final BooksControllerDeviceDeactivationTask device_deactivation_task =
          new BooksControllerDeviceDeactivationTask(
              this.adobe_drm,
              this.accounts_database,
              deactivation_listener);

      device_deactivation_task.run();
    } else {

      /*
       * Otherwise, the login process is completed.
       */

      this.onDeviceDeactivationSucceeded();
    }
  }

  private void onDeviceDeactivationFailure(
      final String message) {

    LOG.debug("onDeviceDeactivationFailure: {}", message);
    this.listener.onAccountLogoutFailure(Option.<Throwable>none(), message);
  }

  private void onDeviceDeactivationSucceeded() {

    LOG.debug("onDeviceDeactivationSucceeded");

    /*
     * Delete the books database.
     */

    try {
      this.accounts_database.accountRemoveCredentials();
      this.database.databaseDestroy();
      this.listener.onAccountLogoutSuccess();
    } catch (IOException e) {
      LOG.error("onDeviceDeactivationSucceeded: ", e);
      this.listener.onAccountLogoutFailure(Option.<Throwable>some(e), e.getMessage());
    }
  }

  @Override
  public void run() {
    this.deactivateDevice();
  }
}
