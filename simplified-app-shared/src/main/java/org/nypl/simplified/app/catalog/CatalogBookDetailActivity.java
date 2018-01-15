package org.nypl.simplified.app.catalog;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.widget.FrameLayout;

import com.io7m.jnull.NullCheck;
import com.io7m.jnull.Nullable;

import org.nypl.simplified.app.NavigationDrawerActivity;
import org.nypl.simplified.app.R;
import org.nypl.simplified.app.Simplified;
import org.nypl.simplified.books.accounts.AccountType;
import org.nypl.simplified.books.accounts.AccountsDatabaseNonexistentException;
import org.nypl.simplified.books.book_registry.BookRegistryReadableType;
import org.nypl.simplified.books.book_registry.BookStatusEvent;
import org.nypl.simplified.books.controller.ProfilesControllerType;
import org.nypl.simplified.books.feeds.FeedEntryOPDS;
import org.nypl.simplified.books.profiles.ProfileNoneCurrentException;
import org.nypl.simplified.observable.ObservableSubscriptionType;
import org.nypl.simplified.stack.ImmutableStack;

/**
 * An activity showing a full-screen book detail page.
 */

public final class CatalogBookDetailActivity extends CatalogActivity {

  private static final String CATALOG_BOOK_DETAIL_FEED_ENTRY_ID =
      "org.nypl.simplified.app.CatalogBookDetailActivity.feed_entry";

  private @Nullable CatalogBookDetailView view;
  private ObservableSubscriptionType<BookStatusEvent> book_subscription;

  /**
   * Construct an activity.
   */

  public CatalogBookDetailActivity() {

  }

  /**
   * Set the arguments of the activity to be created.
   *
   * @param b           The argument bundle
   * @param drawer_open {@code true} if the navigation drawer should be opened.
   * @param up_stack    The up-stack
   * @param e           The feed entry
   */

  public static void setActivityArguments(
      final Bundle b,
      final boolean drawer_open,
      final ImmutableStack<CatalogFeedArgumentsType> up_stack,
      final FeedEntryOPDS e) {

    NullCheck.notNull(b, "Bundle");
    NullCheck.notNull(up_stack, "Up stack");
    NullCheck.notNull(e, "Entry");

    NavigationDrawerActivity.setActivityArguments(b, drawer_open);
    CatalogActivity.setActivityArguments(b, up_stack);
    b.putSerializable(CatalogBookDetailActivity.CATALOG_BOOK_DETAIL_FEED_ENTRY_ID, e);
  }

  /**
   * Start a new activity with the given arguments.
   *
   * @param from     The parent activity
   * @param up_stack The up stack
   * @param e        The feed entry
   */

  public static void startNewActivity(
      final Activity from,
      final ImmutableStack<CatalogFeedArgumentsType> up_stack,
      final FeedEntryOPDS e) {

    NullCheck.notNull(from, "Activity");
    NullCheck.notNull(up_stack, "Up stack");
    NullCheck.notNull(e, "Entry");

    final Bundle b = new Bundle();
    CatalogBookDetailActivity.setActivityArguments(b, false, up_stack, e);
    final Intent i = new Intent(from, CatalogBookDetailActivity.class);
    i.putExtras(b);
    from.startActivity(i);
  }

  private FeedEntryOPDS getFeedEntry() {
    final Intent i = NullCheck.notNull(this.getIntent());
    final Bundle a = NullCheck.notNull(i.getExtras());
    return NullCheck.notNull((FeedEntryOPDS) a.getSerializable(CATALOG_BOOK_DETAIL_FEED_ENTRY_ID));
  }

  @Override
  protected String navigationDrawerGetActivityTitle(final Resources resources) {
    return resources.getString(R.string.catalog_book_detail);
  }

  @Override
  protected boolean navigationDrawerShouldShowIndicator() {
    return false;
  }

  @Override
  protected void onCreate(final @Nullable Bundle state) {
    super.onCreate(state);

    final BookRegistryReadableType book_registry =
        Simplified.getBooksRegistry();
    final ProfilesControllerType profiles =
        Simplified.getProfilesController();

    final FeedEntryOPDS entry = this.getFeedEntry();
    final AccountType account;
    try {
      account = profiles.profileAccountForBook(entry.getBookID());
    } catch (final ProfileNoneCurrentException | AccountsDatabaseNonexistentException e) {
      throw new IllegalStateException(e);
    }

    final LayoutInflater inflater =
        NullCheck.notNull(this.getLayoutInflater());

    final CatalogBookDetailView detail_view =
        new CatalogBookDetailView(
            this,
            inflater,
            account,
            Simplified.getCoverProvider(),
            Simplified.getBooksRegistry(),
            profiles,
            Simplified.getBooksController(),
            entry);

    this.view = detail_view;

    final FrameLayout content_area = this.getContentFrame();
    content_area.removeAllViews();
    content_area.addView(detail_view.getScrollView());
    content_area.requestLayout();

    /*
     * Subscribe the detail view to book events.
     */

    this.book_subscription =
        book_registry.bookEvents()
            .subscribe(detail_view::onBookEvent);
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    this.book_subscription.unsubscribe();
  }
}
