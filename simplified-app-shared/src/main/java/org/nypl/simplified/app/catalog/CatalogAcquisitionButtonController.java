package org.nypl.simplified.app.catalog;

import android.app.Activity;
import android.view.View;
import android.view.View.OnClickListener;

import com.io7m.jfunctional.None;
import com.io7m.jfunctional.Option;
import com.io7m.jfunctional.OptionVisitorType;
import com.io7m.jfunctional.Pair;
import com.io7m.jfunctional.Some;
import com.io7m.jnull.NullCheck;
import com.io7m.jnull.Nullable;
import com.io7m.junreachable.UnimplementedCodeException;

import org.nypl.simplified.app.LoginDialog;
import org.nypl.simplified.app.Simplified;
import org.nypl.simplified.books.accounts.AccountID;
import org.nypl.simplified.books.accounts.AccountProvider;
import org.nypl.simplified.books.accounts.AccountType;
import org.nypl.simplified.books.accounts.AccountsDatabaseNonexistentException;
import org.nypl.simplified.books.accounts.AccountsDatabaseNonexistentProviderException;
import org.nypl.simplified.books.book_database.BookID;
import org.nypl.simplified.books.book_registry.BookRegistryReadableType;
import org.nypl.simplified.books.book_registry.BookWithStatus;
import org.nypl.simplified.books.controller.BooksControllerType;
import org.nypl.simplified.books.controller.ProfilesControllerType;
import org.nypl.simplified.books.core.LogUtilities;
import org.nypl.simplified.books.feeds.FeedEntryOPDS;
import org.nypl.simplified.books.profiles.ProfileNoneCurrentException;
import org.nypl.simplified.books.profiles.ProfileNonexistentAccountProviderException;
import org.nypl.simplified.books.profiles.ProfileReadableType;
import org.nypl.simplified.opds.core.OPDSAcquisition;
import org.slf4j.Logger;

/**
 * A controller for an acquisition button.
 * <p>
 * This is responsible for logging in, if necessary, and then starting the
 * borrow of a given book.
 */

public final class CatalogAcquisitionButtonController implements OnClickListener {

  private static final Logger LOG = LogUtilities.getLog(CatalogAcquisitionButtonController.class);

  private final OPDSAcquisition acquisition;
  private final Activity activity;
  private final BooksControllerType books;
  private final FeedEntryOPDS entry;
  private final BookID id;
  private final ProfilesControllerType profiles;
  private final BookRegistryReadableType book_registry;

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
      final BookRegistryReadableType in_book_registry,
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
    this.book_registry =
        NullCheck.notNull(in_book_registry, "Book registry");
    this.entry =
        NullCheck.notNull(in_entry, "Feed entry");
  }

  @Override
  public void onClick(final @Nullable View v) {

    final AccountType account  =
        accountForBook(this.profiles, this.book_registry, this.id);

    final boolean authentication_required =
        account.provider().authentication().isSome()
            && this.acquisition.getType() != OPDSAcquisition.Type.ACQUISITION_OPEN_ACCESS;

    final boolean authentication_provided = account.credentials().isSome();
    if (authentication_required && !authentication_provided) {
      this.tryLogin(account);
      return;
    }

    this.tryBorrow(account);
  }

  private void tryBorrow(final AccountType account) {

    final OPDSAcquisition.Type type = this.acquisition.getType();
    LOG.debug("trying borrow of type {}", type);

    switch (type) {
      case ACQUISITION_BORROW:
      case ACQUISITION_GENERIC:
      case ACQUISITION_OPEN_ACCESS: {
        this.books.bookBorrow(this.id, account, this.acquisition, this.entry.getFeedEntry());
        return;
      }
      case ACQUISITION_BUY:
      case ACQUISITION_SAMPLE:
      case ACQUISITION_SUBSCRIBE: {
        throw new UnimplementedCodeException();
      }
    }
  }

  private void tryLogin(final AccountType account) {

    LOG.debug("trying login");
    final LoginDialog dialog =
        LoginDialog.newDialog(
            this.profiles,
            "Login Required",
            account,
            () -> this.onLoginSuccess(account),
            this::onLoginCancelled,
            this::onLoginFailure);
    dialog.show(this.activity.getFragmentManager(), "login-dialog");
  }

  private void onLoginFailure(final String message) {
    LOG.debug("login failed: {}", message);
  }

  private void onLoginCancelled() {
    LOG.debug("login cancelled");
  }

  private void onLoginSuccess(
      final AccountType account) {

    LOG.debug("login succeeded");
    this.tryBorrow(account);
  }

  private static AccountType accountForBook(
      final ProfilesControllerType profiles,
      final BookRegistryReadableType book_registry,
      final BookID book_id) {

    return Option.of(book_registry.books().get(book_id)).accept(
        new OptionVisitorType<BookWithStatus, AccountType>() {
          @Override
          public AccountType none(final None<BookWithStatus> none) {
            try {
              return profiles.profileAccountCurrent();
            } catch (final ProfileNoneCurrentException e) {
              throw new IllegalStateException(e);
            }
          }

          @Override
          public AccountType some(final Some<BookWithStatus> some) {
            try {
              final ProfileReadableType profile = profiles.profileCurrent();
              final AccountID account_id = some.get().book().account();
              return profile.account(account_id);
            } catch (ProfileNoneCurrentException | AccountsDatabaseNonexistentException e) {
              throw new IllegalStateException(e);
            }
          }
        });
  }
}
