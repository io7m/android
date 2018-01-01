package org.nypl.simplified.app.catalog;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.widget.FrameLayout;

import com.io7m.jfunctional.ProcedureType;
import com.io7m.jnull.NullCheck;
import com.io7m.jnull.Nullable;
import com.io7m.junreachable.UnimplementedCodeException;

import org.nypl.simplified.app.Simplified;
import org.nypl.simplified.app.SimplifiedActivity;
import org.nypl.simplified.app.SimplifiedPart;
import org.nypl.simplified.books.book_registry.BookEvent;
import org.nypl.simplified.books.book_registry.BookRegistryReadableType;
import org.nypl.simplified.books.core.BooksStatusCacheType;
import org.nypl.simplified.books.core.BooksType;
import org.nypl.simplified.books.core.FeedEntryOPDS;
import org.nypl.simplified.observable.ObservableSubscriptionType;
import org.nypl.simplified.stack.ImmutableStack;

/**
 * An activity showing a full-screen book detail page.
 */

public final class CatalogBookDetailActivity extends CatalogActivity {

  private static final String CATALOG_BOOK_DETAIL_FEED_ENTRY_ID;
  private static final String CATALOG_BOOK_DETAIL_PART;

  static {
    CATALOG_BOOK_DETAIL_FEED_ENTRY_ID =
        "org.nypl.simplified.app.CatalogBookDetailActivity.feed_entry";
    CATALOG_BOOK_DETAIL_PART =
        "org.nypl.simplified.app.CatalogBookDetailActivity.part";
  }

  private @Nullable SimplifiedPart part;
  private @Nullable CatalogBookDetailView view;
  private ObservableSubscriptionType<BookEvent> book_subscription;

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
   * @param in_part     The application part
   * @param up_stack    The up-stack
   * @param e           The feed entry
   */

  public static void setActivityArguments(
      final Bundle b,
      final boolean drawer_open,
      final SimplifiedPart in_part,
      final ImmutableStack<CatalogFeedArgumentsType> up_stack,
      final FeedEntryOPDS e) {

    NullCheck.notNull(b);
    SimplifiedActivity.setActivityArguments(b, drawer_open);
    CatalogActivity.setActivityArguments(b, up_stack);
    b.putSerializable(
        CatalogBookDetailActivity.CATALOG_BOOK_DETAIL_PART, in_part);
    b.putSerializable(
        CatalogBookDetailActivity.CATALOG_BOOK_DETAIL_FEED_ENTRY_ID,
        NullCheck.notNull(e));
  }

  /**
   * Start a new activity with the given arguments.
   *
   * @param from     The parent activity
   * @param up_stack The up stack
   * @param in_part  The application part
   * @param e        The feed entry
   */

  public static void startNewActivity(
      final Activity from,
      final ImmutableStack<CatalogFeedArgumentsType> up_stack,
      final SimplifiedPart in_part,
      final FeedEntryOPDS e) {

    final Bundle b = new Bundle();
    CatalogBookDetailActivity.setActivityArguments(
        b, false, in_part, up_stack, e);
    final Intent i = new Intent(from, CatalogBookDetailActivity.class);
    i.putExtras(b);
    i.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
    from.startActivity(i);
  }

  private FeedEntryOPDS getFeedEntry() {
    final Intent i = NullCheck.notNull(this.getIntent());
    final Bundle a = NullCheck.notNull(i.getExtras());
    return NullCheck.notNull(
        (FeedEntryOPDS) a.getSerializable(
            CatalogBookDetailActivity.CATALOG_BOOK_DETAIL_FEED_ENTRY_ID));
  }

  private SimplifiedPart getPart() {
    final Intent i = NullCheck.notNull(this.getIntent());
    final Bundle a = NullCheck.notNull(i.getExtras());
    return NullCheck.notNull(
        (SimplifiedPart) a.getSerializable(
            CatalogBookDetailActivity.CATALOG_BOOK_DETAIL_PART));
  }

  @Override
  protected SimplifiedPart navigationDrawerGetPart() {
    return NullCheck.notNull(this.part);
  }

  @Override
  protected boolean navigationDrawerShouldShowIndicator() {
    return false;
  }

  @Override
  protected void onCreate(
      final @Nullable Bundle state) {
    super.onCreate(state);

    final BookRegistryReadableType book_registry =
        Simplified.getBooksRegistry();
    final LayoutInflater inflater =
        NullCheck.notNull(this.getLayoutInflater());

    final CatalogBookDetailView detail_view =
        new CatalogBookDetailView(
            this,
            inflater,
            Simplified.getProfilesController().profileAccountProviderCurrent(),
            Simplified.getCoverProvider(),
            Simplified.getBooksRegistry(),
            Simplified.getProfilesController(),
            Simplified.getBooksController(),
            this.getFeedEntry());

    this.view = detail_view;
    this.part = this.getPart();

    final FrameLayout content_area = this.getContentFrame();
    content_area.removeAllViews();
    content_area.addView(detail_view.getScrollView());
    content_area.requestLayout();

    this.book_subscription =
        book_registry.bookEvents().subscribe(new ProcedureType<BookEvent>() {
          @Override
          public void call(final BookEvent event) {
            detail_view.onBookEvent(event);
          }
        });
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    this.book_subscription.unsubscribe();
  }
}
