package org.nypl.simplified.app.catalog;

import android.app.Activity;
import android.database.DataSetObserver;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;

import com.io7m.jfunctional.Option;
import com.io7m.jfunctional.OptionType;
import com.io7m.jfunctional.Pair;
import com.io7m.jfunctional.ProcedureType;
import com.io7m.jfunctional.Some;
import com.io7m.jfunctional.Unit;
import com.io7m.jnull.NullCheck;
import com.io7m.jnull.Nullable;
import com.io7m.junreachable.UnimplementedCodeException;
import com.io7m.junreachable.UnreachableCodeException;

import org.nypl.simplified.app.BookCoverProviderType;
import org.nypl.simplified.app.utilities.UIThread;
import org.nypl.simplified.books.accounts.AccountProvider;
import org.nypl.simplified.books.book_database.BookID;
import org.nypl.simplified.books.book_registry.BookEvent;
import org.nypl.simplified.books.book_registry.BookRegistryReadableType;
import org.nypl.simplified.books.controller.BooksControllerType;
import org.nypl.simplified.books.controller.ProfilesControllerType;
import org.nypl.simplified.books.core.BookDatabaseEntrySnapshot;
import org.nypl.simplified.books.core.BookDatabaseReadableType;
import org.nypl.simplified.books.core.BooksStatusCacheType;
import org.nypl.simplified.books.core.FeedEntryOPDS;
import org.nypl.simplified.books.core.FeedEntryType;
import org.nypl.simplified.books.core.FeedLoaderAuthenticationListenerType;
import org.nypl.simplified.books.core.FeedLoaderListenerType;
import org.nypl.simplified.books.core.FeedLoaderType;
import org.nypl.simplified.books.core.FeedMatcherType;
import org.nypl.simplified.books.core.FeedType;
import org.nypl.simplified.books.core.FeedWithGroups;
import org.nypl.simplified.books.core.FeedWithoutGroups;
import org.nypl.simplified.books.core.LogUtilities;
import org.nypl.simplified.http.core.HTTPAuthType;
import org.nypl.simplified.observable.ObservableSubscriptionType;
import org.slf4j.Logger;

import java.net.URI;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A view that displays a catalog feed that does not contain any groups.
 */

public final class CatalogFeedWithoutGroups
    implements ListAdapter,
    OnScrollListener,
    FeedLoaderListenerType,
    FeedMatcherType<Unit, UnreachableCodeException> {

  private static final Logger LOG;

  static {
    LOG = LogUtilities.getLog(CatalogFeedWithoutGroups.class);
  }

  private final Activity activity;
  private final ArrayAdapter<FeedEntryType> adapter;
  private final BookCoverProviderType book_cover_provider;
  private final CatalogBookSelectionListenerType book_select_listener;
  private final FeedWithoutGroups feed;
  private final FeedLoaderType feed_loader;
  private final AtomicReference<Pair<Future<Unit>, URI>> loading;
  private final AtomicReference<OptionType<URI>> uri_next;
  private final ObservableSubscriptionType<BookEvent> book_event_sub;
  private final BookRegistryReadableType books_registry;
  private final BooksControllerType books_controller;
  private final AccountProvider account_provider;
  private final ProfilesControllerType profiles_controller;

  /**
   * Construct a view.
   *
   * @param in_activity                The host activity
   * @param in_book_cover_provider     A cover provider
   * @param in_book_selection_listener A book selection listener
   * @param in_book_registry           The books registry
   * @param in_book_controller         The books controller
   * @param in_profiles_controller     The profiles controller
   * @param in_feed_loader             An asynchronous feed loader
   * @param in_feed                    The current feed
   */

  public CatalogFeedWithoutGroups(
      final Activity in_activity,
      final AccountProvider in_account_provider,
      final BookCoverProviderType in_book_cover_provider,
      final CatalogBookSelectionListenerType in_book_selection_listener,
      final BookRegistryReadableType in_book_registry,
      final BooksControllerType in_book_controller,
      final ProfilesControllerType in_profiles_controller,
      final FeedLoaderType in_feed_loader,
      final FeedWithoutGroups in_feed) {

    this.activity =
        NullCheck.notNull(in_activity, "Activity");
    this.account_provider =
        NullCheck.notNull(in_account_provider, "Account provider");
    this.book_cover_provider =
        NullCheck.notNull(in_book_cover_provider, "Cover provider");
    this.book_select_listener =
        NullCheck.notNull(in_book_selection_listener, "Selection listener");
    this.books_registry =
        NullCheck.notNull(in_book_registry, "Books registry");
    this.books_controller =
        NullCheck.notNull(in_book_controller, "Books controller");
    this.profiles_controller =
        NullCheck.notNull(in_profiles_controller, "Profiles controller");
    this.feed =
        NullCheck.notNull(in_feed, "Feed");
    this.feed_loader =
        NullCheck.notNull(in_feed_loader, "Feed loader");

    this.uri_next = new AtomicReference<>(in_feed.getFeedNext());
    this.adapter = new ArrayAdapter<>(this.activity, 0, this.feed);
    this.loading = new AtomicReference<>();

    this.book_event_sub = in_book_registry.bookEvents().subscribe(new ProcedureType<BookEvent>() {
      @Override
      public void call(final BookEvent event) {
        onBookEvent(event);
      }
    });
  }

  private void onBookEvent(final BookEvent event) {
    if (this.feed.containsID(event.book())) {
      LOG.debug("update: updated feed entry");

      UIThread.runOnUIThread(
          new Runnable() {
            @Override
            public void run() {
              adapter.notifyDataSetChanged();
            }
          });

      throw new UnimplementedCodeException();
    }
  }

  private static boolean shouldLoadNext(
      final int first_visible_item,
      final int total_count) {

    LOG.debug("shouldLoadNext: {} - {} = {}",
        total_count, first_visible_item, total_count - first_visible_item);
    return (total_count - first_visible_item) <= 50;
  }

  @Override
  public boolean areAllItemsEnabled() {
    return this.adapter.areAllItemsEnabled();
  }

  @Override
  public int getCount() {
    return this.adapter.getCount();
  }

  @Override
  public FeedEntryType getItem(final int position) {
    return NullCheck.notNull(this.adapter.getItem(position));
  }

  @Override
  public long getItemId(final int position) {
    return this.adapter.getItemId(position);
  }

  @Override
  public int getItemViewType(final int position) {
    return this.adapter.getItemViewType(position);
  }

  @Override
  public View getView(
      final int position,
      final @Nullable View reused,
      final @Nullable ViewGroup parent) {

    final FeedEntryType e = NullCheck.notNull(this.adapter.getItem(position));
    final CatalogFeedBookCellView cv;
    if (reused != null) {
      cv = (CatalogFeedBookCellView) reused;
    } else {
      cv = new CatalogFeedBookCellView(
          this.activity,
          this.account_provider,
          this.book_cover_provider,
          this.books_controller,
          this.profiles_controller,
          this.books_registry);
    }

    cv.viewConfigure(e, this.book_select_listener);
    return cv;

  }

  @Override
  public int getViewTypeCount() {
    return this.adapter.getViewTypeCount();
  }

  @Override
  public boolean hasStableIds() {
    return this.adapter.hasStableIds();
  }

  @Override
  public boolean isEmpty() {
    return this.adapter.isEmpty();
  }

  @Override
  public boolean isEnabled(final int position) {
    return this.adapter.isEnabled(position);
  }

  /**
   * Attempt to load the next feed, if necessary. If the feed is already
   * loading, the feed will not be requested again.
   *
   * @param next_ref The next URI, if any
   * @return A future representing the loading feed
   */

  private @Nullable
  Future<Unit> loadNext(
      final AtomicReference<OptionType<URI>> next_ref) {
    final OptionType<URI> next_opt = next_ref.get();
    if (next_opt.isSome()) {
      final Some<URI> next_some = (Some<URI>) next_opt;
      final URI next = next_some.get();

      final Pair<Future<Unit>, URI> in_loading = this.loading.get();
      if (in_loading == null) {
        LOG.debug("no feed currently loading; loading next feed: {}", next);
        return this.loadNextActual(next);
      }

      final URI loading_uri = in_loading.getRight();
      if (!loading_uri.equals(next)) {
        LOG.debug("different feed currently loading; loading next feed: {}", next);
        return this.loadNextActual(next);
      }

      LOG.debug("already loading next feed, not loading again: {}", next);
    }

    return null;
  }

  private Future<Unit> loadNextActual(
      final URI next) {
    LOG.debug("loading: {}", next);
    final OptionType<HTTPAuthType> none = Option.none();
    final Future<Unit> r = this.feed_loader.fromURIWithDatabaseEntries(
        next, none, this);
    this.loading.set(Pair.pair(r, next));
    return r;
  }

  @Override
  public void onFeedLoadFailure(
      final URI u,
      final Throwable e) {
    if (e instanceof CancellationException) {
      return;
    }

    LOG.error("failed to load feed: ", e);
  }

  @Override
  public void onFeedLoadSuccess(
      final URI u,
      final FeedType f) {
    f.matchFeed(this);
  }

  @Override
  public void onFeedRequiresAuthentication(
      final URI u,
      final int attempts,
      final FeedLoaderAuthenticationListenerType listener) {

    /*
     * XXX: Delegate this to the current activity, as it knows
     * how to handle authentication!
     */

    listener.onAuthenticationNotProvided();
  }

  @Override
  public Unit onFeedWithGroups(
      final FeedWithGroups f) {
    LOG.error(
        "received feed with groups: {}", f.getFeedID());

    return Unit.unit();
  }

  @Override
  public Unit onFeedWithoutGroups(
      final FeedWithoutGroups f) {
    LOG.debug("received feed without groups: {}", f.getFeedID());

    this.feed.addAll(f);
    this.uri_next.set(f.getFeedNext());

    LOG.debug("current feed size: {}", this.feed.size());
    return Unit.unit();
  }

  @Override
  public void onScroll(
      final @Nullable AbsListView view,
      final int first_visible_item,
      final int visible_count,
      final int total_count) {

    /*
     * If the user is close enough to the end of the list, load the next feed.
     */

    if (CatalogFeedWithoutGroups.shouldLoadNext(first_visible_item, total_count)) {
      this.loadNext(this.uri_next);
    }
  }

  @Override
  public void onScrollStateChanged(
      final @Nullable AbsListView view,
      final int state) {
    switch (state) {
      case OnScrollListener.SCROLL_STATE_FLING:
      case OnScrollListener.SCROLL_STATE_TOUCH_SCROLL: {
        this.book_cover_provider.loadingThumbailsPause();
        break;
      }
      case OnScrollListener.SCROLL_STATE_IDLE: {
        this.book_cover_provider.loadingThumbnailsContinue();
        break;
      }
    }
  }

  @Override
  public void registerDataSetObserver(
      final @Nullable DataSetObserver observer) {
    this.adapter.registerDataSetObserver(observer);
  }

  @Override
  public void unregisterDataSetObserver(
      final @Nullable DataSetObserver observer) {
    this.adapter.unregisterDataSetObserver(observer);
  }

  private boolean updateFetchDatabaseSnapshot(
      final BookID current_id,
      final BookDatabaseReadableType db) {

//    final OptionType<BookDatabaseEntrySnapshot> snap_opt =
//        db.databaseGetEntrySnapshot(current_id);
//
//    LOG.debug("database snapshot is {}", snap_opt);
//
//    if (snap_opt.isSome()) {
//      final Some<BookDatabaseEntrySnapshot> some =
//          (Some<BookDatabaseEntrySnapshot>) snap_opt;
//      final BookDatabaseEntrySnapshot snap = some.get();
//      final FeedEntryType re =
//          FeedEntryOPDS.fromOPDSAcquisitionFeedEntry(snap.getEntry());
//      this.feed.updateEntry(re);
//      return true;
//    }
//
//    return false;

    throw new UnimplementedCodeException();
  }

  private boolean updateCheckForRevocationEntry(
      final BookID current_id,
      final BooksStatusCacheType status_cache) {

//    final OptionType<FeedEntryType> revoke_opt =
//        status_cache.booksRevocationFeedEntryGet(current_id);
//
//    LOG.debug("revocation entry update is {}", revoke_opt);
//
//    if (revoke_opt.isSome()) {
//      final Some<FeedEntryType> some = (Some<FeedEntryType>) revoke_opt;
//      this.feed.updateEntry(some.get());
//      return true;
//    }
//
//    return false;

    throw new UnimplementedCodeException();
  }
}
