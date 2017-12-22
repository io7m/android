package org.nypl.simplified.books.core;

import com.io7m.jfunctional.Option;
import com.io7m.jfunctional.OptionType;
import com.io7m.jfunctional.Unit;
import com.io7m.jnull.NullCheck;
import com.io7m.junreachable.UnreachableCodeException;

import org.nypl.simplified.books.accounts.AccountAuthenticatedHTTP;
import org.nypl.simplified.books.accounts.AccountAuthenticationCredentials;
import org.nypl.simplified.http.core.HTTPAuthType;
import org.nypl.simplified.http.core.HTTPResultError;
import org.nypl.simplified.http.core.HTTPResultException;
import org.nypl.simplified.http.core.HTTPResultMatcherType;
import org.nypl.simplified.http.core.HTTPResultOKType;
import org.nypl.simplified.http.core.HTTPResultType;
import org.nypl.simplified.http.core.HTTPType;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;

final class BooksControllerLoginTask implements Runnable,
    AccountDataSetupListenerType {
  private static final Logger LOG;

  static {
    LOG = LogUtilities.getLog(BooksControllerLoginTask.class);
  }

  private final BooksController books;
  private final BookDatabaseType books_database;
  private final BooksControllerConfigurationType config;
  private final HTTPType http;
  private final AccountLoginListenerType listener;
  private final DeviceActivationListenerType device_listener;
  private final AccountAuthenticationCredentials credentials;
  private final AccountsDatabaseType accounts_database;

  BooksControllerLoginTask(
      final BooksController in_books,
      final BookDatabaseType in_books_database,
      final AccountsDatabaseType in_accounts_database,
      final HTTPType in_http,
      final BooksControllerConfigurationType in_config,
      final AccountAuthenticationCredentials in_credentials,
      final AccountLoginListenerType in_listener,
      final DeviceActivationListenerType in_device_listener) {

    this.books = NullCheck.notNull(in_books);
    this.books_database = NullCheck.notNull(in_books_database);
    this.accounts_database = NullCheck.notNull(in_accounts_database);
    this.http = NullCheck.notNull(in_http);
    this.config = NullCheck.notNull(in_config);
    this.credentials = NullCheck.notNull(in_credentials);
    this.listener = new AccountLoginListenerCatcher(LOG, NullCheck.notNull(in_listener));
    this.device_listener = in_device_listener;
  }

  @Override
  public void onAccountDataSetupFailure(
      final OptionType<Throwable> error,
      final String message) {
    this.listener.onAccountLoginFailureLocalError(error, message);
  }

  @Override
  public void onAccountDataSetupSuccess() {

    /*
     * Setting up the database was successful, now try hitting the remote
     * server and seeing whether or not it rejects the given credentials.
     */

    final HTTPAuthType auth =
        AccountAuthenticatedHTTP.createAuthenticatedHTTP(this.credentials);

    final URI auth_uri = this.config.getCurrentRootFeedURI().resolve("loans/");
    final HTTPResultType<InputStream> r = this.http.head(Option.some(auth), auth_uri);

    LOG.debug(
        "attempting login on {}", auth_uri);

    r.matchResult(
        new HTTPResultMatcherType<InputStream, Unit, UnreachableCodeException>() {
          @Override
          public Unit onHTTPError(final HTTPResultError<InputStream> e) {
            BooksControllerLoginTask.this.onHTTPServerReturnedError(e);
            return Unit.unit();
          }

          @Override
          public Unit onHTTPException(final HTTPResultException<InputStream> e) {
            BooksControllerLoginTask.this.onHTTPException(e);
            return Unit.unit();
          }

          @Override
          public Unit onHTTPOK(final HTTPResultOKType<InputStream> e) {
            BooksControllerLoginTask.this.onHTTPServerAcceptedCredentials(e.getValue());
            return Unit.unit();
          }
        });
  }

  private <T> void onHTTPException(final HTTPResultException<T> e) {
    final Exception ex = e.getError();
    this.listener.onAccountLoginFailureLocalError(Option.some((Throwable) ex), ex.getMessage());
  }

  private void onHTTPServerReturnedError(final HTTPResultError<?> e) {
    final int code = e.getStatus();
    switch (code) {
      case HttpURLConnection.HTTP_UNAUTHORIZED: {
        this.listener.onAccountLoginFailureCredentialsIncorrect();
        break;
      }
      default: {
        this.listener.onAccountLoginFailureServerError(code);
      }
    }
  }

  private void onHTTPServerAcceptedCredentials(final InputStream data) {
    this.onCompletedSuccessfully();
  }

  private void onCompletedSuccessfully() {
    this.listener.onAccountLoginSuccess(this.credentials);

    LOG.debug("logged in as {} successfully", this.credentials.barcode());

    try {
      this.accounts_database.accountSetCredentials(this.credentials);
    } catch (final IOException e) {
      LOG.error("could not save credentials: ", e);
      this.listener.onAccountLoginFailureLocalError(Option.some((Throwable) e), e.getMessage());
      return;
    }

    this.books.accountSync(this.listener, this.device_listener);
  }

  @Override
  public void run() {

    /*
     * Set up the initial data directories, and notify this task
     * via the {@link AccountDataSetupListenerType} interface upon
     * success or failure.
     */

    this.books.submitRunnable(
        new BooksControllerDataSetupTask(this.books_database, this));
  }
}
