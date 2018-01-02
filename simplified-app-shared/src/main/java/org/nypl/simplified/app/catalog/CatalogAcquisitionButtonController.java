package org.nypl.simplified.app.catalog;

import android.app.Activity;
import android.app.FragmentManager;
import android.content.Intent;
import android.view.View;
import android.view.View.OnClickListener;

import com.io7m.jnull.NullCheck;
import com.io7m.jnull.Nullable;
import com.io7m.junreachable.UnimplementedCodeException;

import org.nypl.simplified.app.LoginActivity;
import org.nypl.simplified.app.LoginDialog;
import org.nypl.simplified.app.R;
import org.nypl.simplified.books.accounts.AccountBarcode;
import org.nypl.simplified.books.accounts.AccountPIN;
import org.nypl.simplified.books.book_database.BookID;
import org.nypl.simplified.books.controller.BooksControllerType;
import org.nypl.simplified.books.controller.ProfilesControllerType;
import org.nypl.simplified.books.feeds.FeedEntryOPDS;
import org.nypl.simplified.books.core.LogUtilities;
import org.nypl.simplified.books.profiles.ProfileNoneCurrentException;
import org.nypl.simplified.books.profiles.ProfileNonexistentAccountProviderException;
import org.nypl.simplified.opds.core.OPDSAcquisition;
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry;
import org.slf4j.Logger;

/**
 * A controller for an acquisition button.
 * <p>
 * This is responsible for logging in, if necessary, and then starting the
 * borrow of a given book.
 */

public final class CatalogAcquisitionButtonController implements OnClickListener {

  private static final Logger LOG =
      LogUtilities.getLog(CatalogAcquisitionButtonController.class);

  private final OPDSAcquisition acquisition;
  private final Activity activity;
  private final BooksControllerType books;
  private final FeedEntryOPDS entry;
  private final BookID id;
  private final ProfilesControllerType profiles;

  /**
   * Construct a button controller.
   *
   * @param in_activity The host activity
   * @param in_books    The books database
   * @param in_id       The book ID
   * @param in_acq      The acquisition
   * @param in_entry    The associated feed entry
   */

  public CatalogAcquisitionButtonController(
      final Activity in_activity,
      final ProfilesControllerType in_profiles,
      final BooksControllerType in_books,
      final BookID in_id,
      final OPDSAcquisition in_acq,
      final FeedEntryOPDS in_entry) {

    this.activity =
        NullCheck.notNull(in_activity, "Activity");
    this.acquisition =
        NullCheck.notNull(in_acq, "OPDS Acquisition");
    this.id =
        NullCheck.notNull(in_id, "Book ID");
    this.profiles =
        NullCheck.notNull(in_profiles, "Profiles controller");
    this.books =
        NullCheck.notNull(in_books, "Books controller");
    this.entry =
        NullCheck.notNull(in_entry, "Feed entry");
  }

  @Override
  public void onClick(final @Nullable View v) {


    throw new UnimplementedCodeException();

/*    if (this.books.accountIsLoggedIn() && isNeedsAuth() && this.acquisition.getType() != OPDSAcquisition.Type.ACQUISITION_OPEN_ACCESS) {
      this.books.accountGetCachedLoginDetails(
          new AccountGetCachedCredentialsListenerType() {
            @Override
            public void onAccountIsNotLoggedIn() {
              throw new UnreachableCodeException();
            }

            @Override
            public void onAccountIsLoggedIn(
                final AccountAuthenticationCredentials creds) {
              CatalogAcquisitionButtonController.this.onLoginSuccess(creds);
            }
          });
    } else if (!isNeedsAuth() || this.acquisition.getType() == OPDSAcquisition.Type.ACQUISITION_OPEN_ACCESS) {
      this.getBook();
    } else {
      this.tryLogin();
    }*/
  }

  private static boolean isNeedsAuth() {
    throw new UnimplementedCodeException();
  }

  private void tryLogin() {

    final boolean clever_enabled = this.activity.getResources().getBoolean(R.bool.feature_auth_provider_clever);

    if (clever_enabled) {
      final Intent account = new Intent(this.activity, LoginActivity.class);
      this.activity.startActivityForResult(account, 1);
      this.activity.overridePendingTransition(0, 0);
    } else {

      final AccountBarcode barcode = AccountBarcode.create("");
      final AccountPIN pin = AccountPIN.create("");

      final LoginDialog df;
      try {
        df = LoginDialog.newDialog(
            this.profiles,
            "Login required",
            this.profiles.profileAccountProviderCurrent(),
            barcode,
            pin,
            () -> {
              LOG.debug("login succeeded");
              this.getBook();
            },
            () -> LOG.debug("login cancelled"),
            x -> LOG.debug("login failed: {}", x));
      } catch (final ProfileNoneCurrentException | ProfileNonexistentAccountProviderException e) {
        throw new IllegalStateException(e);
      }

      final FragmentManager fm = this.activity.getFragmentManager();
      df.show(fm, "login-dialog");
    }
  }

  private void getBook() {
    LOG.debug("attempting borrow of {} acquisition", this.acquisition.getType());

    switch (this.acquisition.getType()) {
      case ACQUISITION_BORROW:
      case ACQUISITION_GENERIC:
      case ACQUISITION_OPEN_ACCESS: {
        final OPDSAcquisitionFeedEntry eo = this.entry.getFeedEntry();
        throw new UnimplementedCodeException();
      }
      case ACQUISITION_BUY:
      case ACQUISITION_SAMPLE:
      case ACQUISITION_SUBSCRIBE: {
        throw new UnimplementedCodeException();
      }
    }
  }
}
