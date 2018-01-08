package org.nypl.simplified.app.catalog;

import android.app.ActionBar;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.SearchView.OnQueryTextListener;
import android.widget.TextView;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.io7m.jfunctional.Option;
import com.io7m.jfunctional.OptionType;
import com.io7m.jfunctional.Some;
import com.io7m.jfunctional.Unit;
import com.io7m.jnull.NullCheck;
import com.io7m.jnull.Nullable;
import com.io7m.junreachable.UnimplementedCodeException;
import com.io7m.junreachable.UnreachableCodeException;

import org.nypl.simplified.app.LoginActivity;
import org.nypl.simplified.app.NetworkConnectivityType;
import org.nypl.simplified.app.R;
import org.nypl.simplified.app.ScreenSizeInformationType;
import org.nypl.simplified.app.Simplified;
import org.nypl.simplified.app.SimplifiedActivity;
import org.nypl.simplified.app.utilities.UIThread;
import org.nypl.simplified.assertions.Assertions;
import org.nypl.simplified.books.book_registry.BookStatusEvent;
import org.nypl.simplified.books.controller.ProfileFeedRequest;
import org.nypl.simplified.books.core.BooksFeedSelection;
import org.nypl.simplified.books.core.DocumentStoreType;
import org.nypl.simplified.books.core.EULAType;
import org.nypl.simplified.books.core.LogUtilities;
import org.nypl.simplified.books.feeds.FeedEntryOPDS;
import org.nypl.simplified.books.feeds.FeedFacetMatcherType;
import org.nypl.simplified.books.feeds.FeedFacetOPDS;
import org.nypl.simplified.books.feeds.FeedFacetPseudo;
import org.nypl.simplified.books.feeds.FeedFacetPseudo.FacetType;
import org.nypl.simplified.books.feeds.FeedFacetType;
import org.nypl.simplified.books.feeds.FeedGroup;
import org.nypl.simplified.books.feeds.FeedLoaderAuthenticationListenerType;
import org.nypl.simplified.books.feeds.FeedLoaderListenerType;
import org.nypl.simplified.books.feeds.FeedLoaderType;
import org.nypl.simplified.books.feeds.FeedMatcherType;
import org.nypl.simplified.books.feeds.FeedSearchLocal;
import org.nypl.simplified.books.feeds.FeedSearchMatcherType;
import org.nypl.simplified.books.feeds.FeedSearchOpen1_1;
import org.nypl.simplified.books.feeds.FeedSearchType;
import org.nypl.simplified.books.feeds.FeedType;
import org.nypl.simplified.books.feeds.FeedWithGroups;
import org.nypl.simplified.books.feeds.FeedWithoutGroups;
import org.nypl.simplified.books.profiles.ProfileAccountSelectEvent.ProfileAccountSelectSucceeded;
import org.nypl.simplified.books.profiles.ProfileEvent;
import org.nypl.simplified.books.profiles.ProfileNoneCurrentException;
import org.nypl.simplified.http.core.HTTPAuthType;
import org.nypl.simplified.observable.ObservableSubscriptionType;
import org.nypl.simplified.opds.core.OPDSFacet;
import org.nypl.simplified.opds.core.OPDSOpenSearch1_1;
import org.nypl.simplified.stack.ImmutableStack;
import org.slf4j.Logger;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;

import javax.annotation.concurrent.GuardedBy;

import static org.nypl.simplified.books.feeds.FeedFacetPseudo.FacetType.*;

/**
 * The main catalog feed activity, responsible for displaying different types of
 * feeds.
 */

public abstract class CatalogFeedActivity extends CatalogActivity
    implements
    FeedMatcherType<Unit, UnreachableCodeException>,
    FeedLoaderListenerType {

  private static final String CATALOG_ARGS =
      "org.nypl.simplified.app.CatalogFeedActivity.arguments";
  private static final String LIST_STATE_ID =
      "org.nypl.simplified.app.CatalogFeedActivity.list_view_state";

  private final Object feed_lock;
  private @GuardedBy("feed_lock") FeedType feed;

  private AbsListView list_view;
  private SwipeRefreshLayout swipe_refresh_layout;
  private Future<Unit> loading;
  private ViewGroup progress_layout;
  private int saved_scroll_pos;
  private boolean previously_paused;
  private SearchView search_view;
  private ObservableSubscriptionType<ProfileEvent> profile_event_subscription;
  private ObservableSubscriptionType<BookStatusEvent> book_event_subscription;

  /**
   * @return The specific logger instance provided by subclasses
   */

  protected abstract Logger log();

  /**
   * Construct an activity.
   */

  public CatalogFeedActivity() {
    this.feed_lock = new Object();
  }

  /**
   * Set the arguments of the activity to be created.
   * Modifies Bundle based on attributes and type (from local or remote)
   * before being given to Intent in the calling method.
   *
   * @param b       The argument bundle
   * @param in_args The feed arguments
   */

  public static void setActivityArguments(
      final Bundle b,
      final CatalogFeedArgumentsType in_args) {

    NullCheck.notNull(b);
    NullCheck.notNull(in_args);

    b.putSerializable(CatalogFeedActivity.CATALOG_ARGS, in_args);

    in_args.matchArguments(
        new CatalogFeedArgumentsMatcherType<Unit, UnreachableCodeException>() {
          @Override
          public Unit onFeedArgumentsLocalBooks(final CatalogFeedArgumentsLocalBooks c) {
            SimplifiedActivity.setActivityArguments(b, false);
            CatalogActivity.setActivityArguments(b, c.getUpStack());
            return Unit.unit();
          }

          @Override
          public Unit onFeedArgumentsRemote(final CatalogFeedArgumentsRemote c) {
            SimplifiedActivity.setActivityArguments(b, c.isDrawerOpen());
            CatalogActivity.setActivityArguments(b, c.getUpStack());
            return Unit.unit();
          }
        });
  }

  /**
   * On the (possible) receipt of a link to the feed's EULA, update the URI for
   * the document if one has actually been defined for the application.
   *
   * @param latest The (possible) link
   * @see EULAType
   */

  private static void onPossiblyReceivedEULALink(
      final Logger log,
      final OptionType<URI> latest) {

    latest.map_(
        latest_actual -> {
          final DocumentStoreType docs = Simplified.getDocumentStore();

          docs.getEULA().map_(
              eula -> {
                try {
                  eula.documentSetLatestURL(latest_actual.toURL());
                } catch (final MalformedURLException e) {
                  log.error("could not use latest EULA link: ", e);
                }
              });
        });
  }

  @Override
  public void onBackPressed() {
    this.invalidateOptionsMenu();
    super.onBackPressed();
  }

  @Override
  protected void onResume() {
    super.onResume();

    /*
     * If the activity was previously paused, this means that the user
     * navigated away from the activity and is now coming back to it. If the
     * user went into a book detail view and revoked a book, then the feed
     * should be completely reloaded when the user comes back, to ensure that
     * the book no longer shows up in the list.
     *
     * This obviously only applies to local feeds.
     */

    if (this.search_view != null) {
      this.search_view.setQuery("", false);
      this.search_view.clearFocus();
    }

    boolean did_retry = false;
    final Bundle extras = getIntent().getExtras();
    if (extras != null) {
      final boolean reload = extras.getBoolean("reload");
      if (reload) {
        did_retry = true;
        this.retryFeed();
        extras.putBoolean("reload", false);
      }
    }

    if (this.previously_paused && !did_retry) {
      final CatalogFeedArgumentsType args = this.getArguments();
      if (args.isLocallyGenerated()) {
        this.retryFeed();
      }
    }
  }

  /**
   * Configure the facets layout. This is what causes facets to be shown or not
   * shown at the top of the screen when rendering a feed.
   *
   * @param feed      The feed
   * @param layout    The view group that will contain facet elements
   * @param screen    The screen size controller
   * @param resources The app resources
   */

  private void configureFacets(
      final FeedWithoutGroups feed,
      final ViewGroup layout,
      final ScreenSizeInformationType screen,
      final Resources resources) {

    final ViewGroup facets_view =
        NullCheck.notNull(layout.findViewById(R.id.catalog_feed_nogroups_facets));
    final View facet_divider =
        NullCheck.notNull(layout.findViewById(R.id.catalog_feed_nogroups_facet_divider));

    final Map<String, List<FeedFacetType>> facet_groups =
        feed.getFeedFacetsByGroup();

    /*
     * If the facet groups are empty, then no facet bar should be displayed.
     */

    if (facet_groups.isEmpty()) {
      facets_view.setVisibility(View.GONE);
      facet_divider.setVisibility(View.GONE);
    } else {

      /*
       * Otherwise, for each facet group, show a drop-down menu allowing
       * the selection of individual facets.
       */

      for (final String group_name : facet_groups.keySet()) {
        final List<FeedFacetType> group = NullCheck.notNull(facet_groups.get(group_name));
        final ArrayList<FeedFacetType> group_copy = new ArrayList<>(group);

        final LinearLayout.LayoutParams text_view_layout_params = new LinearLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        text_view_layout_params.rightMargin = (int) screen.screenDPToPixels(8);

        final TextView text_view = new TextView(this);
        text_view.setTextColor(resources.getColor(R.color.normal_text_major));
        text_view.setTextSize(12.0f);
        text_view.setText(group_name + ":");
        text_view.setLayoutParams(text_view_layout_params);
        facets_view.addView(text_view);

        final OptionType<String> search_terms;
        final CatalogFeedArgumentsType current_args = this.getArguments();
        if (current_args instanceof CatalogFeedArgumentsLocalBooks) {
          final CatalogFeedArgumentsLocalBooks locals =
              (CatalogFeedArgumentsLocalBooks) current_args;
          search_terms = locals.getSearchTerms();
        } else {
          search_terms = Option.none();
        }

        final FeedFacetMatcherType<Unit, UnreachableCodeException> facet_feed_listener =
            new FeedFacetMatcherType<Unit, UnreachableCodeException>() {
              @Override
              public Unit onFeedFacetOPDS(final FeedFacetOPDS feed_opds) {
                final OPDSFacet o = feed_opds.getOPDSFacet();
                final CatalogFeedArgumentsRemote args =
                    new CatalogFeedArgumentsRemote(
                        false,
                        getUpStack(),
                        feed.getFeedTitle(),
                        o.getURI(),
                        false);

                CatalogFeedActivity.this.catalogActivityForkNewReplacing(args);
                return Unit.unit();
              }

              @Override
              public Unit onFeedFacetPseudo(final FeedFacetPseudo fp) {
                final String facet_title =
                    NullCheck.notNull(resources.getString(R.string.books_sort_by));

                final CatalogFeedArgumentsLocalBooks args =
                    new CatalogFeedArgumentsLocalBooks(
                        getUpStack(),
                        facet_title,
                        fp.getType(),
                        search_terms,
                        getLocalFeedTypeSelection());

                CatalogFeedActivity.this.catalogActivityForkNewReplacing(args);
                return Unit.unit();
              }
            };

        final CatalogFacetSelectionListenerType facet_listener = in_selected -> {
          this.log().debug("selected facet: {}", in_selected);
          in_selected.matchFeedFacet(facet_feed_listener);
        };

        final CatalogFacetButton fb = new CatalogFacetButton(
            this, NullCheck.notNull(group_name), group_copy, facet_listener);

        fb.setLayoutParams(text_view_layout_params);
        facets_view.addView(fb);
      }
    }
  }

  /**
   * If this activity is being used in a part of the application that generates
   * local feeds, then return the type of feed that should be generated.
   *
   * @return The type of feed that should be generated.
   */

  protected abstract BooksFeedSelection getLocalFeedTypeSelection();

  @Override
  public void onFeedRequiresAuthentication(
      final URI u,
      final int attempts,
      final FeedLoaderAuthenticationListenerType listener) {

    UIThread.runOnUIThread(() -> {
      final Intent i = new Intent(CatalogFeedActivity.this, LoginActivity.class);
      this.startActivity(i);
      this.overridePendingTransition(0, 0);
      this.finish();
    });
  }

  private void configureUpButton(
      final ImmutableStack<CatalogFeedArgumentsType> up_stack,
      final String title) {

    final ActionBar bar = this.getActionBar();
    if (!up_stack.isEmpty()) {
      bar.setTitle(title);
    }
  }

  private CatalogFeedArgumentsType getArguments() {

    /*
     * Attempt to fetch arguments.
     */

    final Resources rr = NullCheck.notNull(this.getResources());
    final Intent i = NullCheck.notNull(this.getIntent());
    final Bundle a = i.getExtras();
    if (a != null) {
      final CatalogFeedArgumentsType args =
          (CatalogFeedArgumentsType) a.getSerializable(CatalogFeedActivity.CATALOG_ARGS);
      if (args != null) {
        return args;
      }
    }

    /*
     * If there were no arguments (because, for example, this activity is the
     * initial one started for the app), synthesize some.
     */

    try {
      return new CatalogFeedArgumentsRemote(
          true,
          ImmutableStack.empty(),
          NullCheck.notNull(rr.getString(R.string.feature_app_name)),
          Simplified.getProfilesController().profileAccountCurrentCatalogRootURI(),
          false);
    } catch (final ProfileNoneCurrentException e) {
      throw new IllegalStateException(e);
    }
  }

  private void loadFeed(
      final FeedLoaderType feed_loader,
      final URI u) {

    this.log().debug("loading feed: {}", u);
    final OptionType<HTTPAuthType> none = Option.none();
    this.loading = feed_loader.fromURIWithDatabaseEntries(u, none, this);
  }

  @Override
  protected boolean navigationDrawerShouldShowIndicator() {
    return this.getUpStack().isEmpty();
  }

  private ImmutableStack<CatalogFeedArgumentsType> newUpStack(
      final CatalogFeedArgumentsType args) {
    final ImmutableStack<CatalogFeedArgumentsType> up_stack = this.getUpStack();
    return up_stack.push(args);
  }

  @Override
  protected void onCreate(final @Nullable Bundle state) {
    super.onCreate(state);

    this.navigationDrawerSetActionBarTitle();

    final CatalogFeedArgumentsType args = this.getArguments();
    final ImmutableStack<CatalogFeedArgumentsType> stack = this.getUpStack();
    this.configureUpButton(stack, args.getTitle());

    final Resources resources = NullCheck.notNull(this.getResources());
    setTitle(args.getTitle().equals(NullCheck.notNull(resources.getString(R.string.feature_app_name))) ? resources.getString(R.string.catalog) : args.getTitle());

    /*
     * Attempt to restore the saved scroll position, if there is one.
     */

    if (state != null) {
      this.log().debug("received state");
      this.saved_scroll_pos = state.getInt(CatalogFeedActivity.LIST_STATE_ID);
    } else {
      this.saved_scroll_pos = 0;
    }

    /*
     * Display a progress bar until the feed is either loaded or fails.
     */

    final LayoutInflater inflater = this.getLayoutInflater();
    final FrameLayout content_area = this.getContentFrame();
    final ViewGroup in_progress_layout = NullCheck.notNull(
        (ViewGroup) inflater.inflate(R.layout.catalog_loading, content_area, false));

    content_area.addView(in_progress_layout);
    content_area.requestLayout();
    this.progress_layout = in_progress_layout;

    /*
     * If the feed is not locally generated, and the network is not
     * available, then fail fast and display an error message.
     */

    final NetworkConnectivityType net = Simplified.getNetworkConnectivity();
    if (!args.isLocallyGenerated()) {
      if (!net.isNetworkAvailable()) {
        this.onNetworkUnavailable();
        return;
      }
    }

    /*
     * Create a dispatching function that will load a feed based on the given
     * arguments, and execute it.
     */

    args.matchArguments(
        new CatalogFeedArgumentsMatcherType<Unit, UnreachableCodeException>() {
          @Override
          public Unit onFeedArgumentsLocalBooks(final CatalogFeedArgumentsLocalBooks c) {
            doLoadLocalFeed(c);
            return Unit.unit();
          }

          @Override
          public Unit onFeedArgumentsRemote(final CatalogFeedArgumentsRemote c) {
            doLoadRemoteFeed(c);
            return Unit.unit();
          }
        });

    /*
     * Subscribe to profile change events.
     */

    this.profile_event_subscription =
        Simplified.getProfilesController()
            .profileEvents()
            .subscribe(this::onProfileEvent);
  }

  private void onProfileEvent(final ProfileEvent event) {

    /*
     * If the current profile changed accounts, start a new catalog feed activity. The
     * new activity will automatically navigate to the root of the new account's catalog.
     */

    if (event instanceof ProfileAccountSelectSucceeded) {
      UIThread.runOnUIThread(() -> {
        final Intent i = new Intent(CatalogFeedActivity.this, MainCatalogActivity.class);
        final Bundle b = new Bundle();
        SimplifiedActivity.setActivityArguments(b, false);
        i.putExtras(b);
        this.startActivity(i);
        this.overridePendingTransition(0, 0);
        this.finish();
      });
    }
  }

  @Override
  public boolean onCreateOptionsMenu(final @Nullable Menu in_menu) {
    final Menu menu_nn = NullCheck.notNull(in_menu);

    this.log().debug("inflating menu");
    final MenuInflater inflater = this.getMenuInflater();
    inflater.inflate(R.menu.catalog, menu_nn);

    final FeedType feed_ref;
    synchronized (this.feed_lock) {
      feed_ref = this.feed;
      if (feed_ref == null) {
        this.log().debug("menu creation requested but feed is not yet present");
        return true;
      }
    }

    this.log().debug("menu creation requested and feed is present");
    this.onCreateOptionsMenuSearchItem(menu_nn, feed_ref);
    return true;
  }

  private void onCreateOptionsMenuSearchItem(
      final Menu menu_nn,
      final FeedType feed) {

    final MenuItem search_item = menu_nn.findItem(R.id.catalog_action_search);

    /*
     * If the feed actually has a search URI, then show the search field.
     * Otherwise, disable and hide it.
     */

    final OptionType<FeedSearchType> search_opt = feed.getFeedSearch();
    boolean search_ok = false;
    if (search_opt.isSome()) {
      final Some<FeedSearchType> search_some =
          (Some<FeedSearchType>) search_opt;

      this.search_view = (SearchView) search_item.getActionView();
      this.search_view.setSubmitButtonEnabled(true);
      this.search_view.setIconifiedByDefault(false);
      search_item.setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS);
      search_item.expandActionView();

      /*
       * Set some placeholder text
       */

      final CatalogFeedArgumentsType args = this.getArguments();
      this.search_view.setQueryHint("Search " + feed.getFeedTitle());
      if (args.getTitle().startsWith("Search")) {
        this.search_view.setQueryHint(args.getTitle());
      }

      /*
       * Check that the search URI is of an understood type.
       */

      final Resources resources = NullCheck.notNull(this.getResources());
      final FeedSearchType search = search_some.get();
      search_ok = search.matchSearch(
          new FeedSearchMatcherType<Boolean, UnreachableCodeException>() {
            @Override
            public Boolean onFeedSearchOpen1_1(final FeedSearchOpen1_1 fs) {
              search_view.setOnQueryTextListener(
                  new OpenSearchQueryHandler(log(), resources, args, feed, fs.getSearch()));
              return true;
            }

            @Override
            public Boolean onFeedSearchLocal(final FeedSearchLocal f) {
              search_view.setOnQueryTextListener(
                  new BooksLocalSearchQueryHandler(log(), resources, args, feed, SORT_BY_TITLE));
              return true;
            }
          });
    }

    if (search_ok) {
      search_item.setEnabled(true);
      search_item.setVisible(true);
    }
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    this.log().debug("onDestroy");

    final Future<Unit> future = this.loading;
    if (future != null) {
      future.cancel(true);
    }

    this.profile_event_subscription.unsubscribe();

    final ObservableSubscriptionType<BookStatusEvent> book_sub = this.book_event_subscription;
    if (book_sub != null) {
      book_sub.unsubscribe();
    }
  }

  @Override
  public void onFeedLoadFailure(
      final URI u,
      final Throwable x) {

    UIThread.runOnUIThread(() -> onFeedLoadingFailureUI(x));
  }

  private void onFeedLoadingFailureUI(final Throwable e) {
    UIThread.checkIsUIThread();

    this.log().info("Failed to get feed: ", e);
    this.invalidateOptionsMenu();

    final FrameLayout content_area = this.getContentFrame();
    final ViewGroup progress = NullCheck.notNull(this.progress_layout);
    progress.setVisibility(View.GONE);
    content_area.removeAllViews();

    final LayoutInflater inflater = this.getLayoutInflater();
    final ViewGroup error =
        NullCheck.notNull((ViewGroup) inflater.inflate(
            R.layout.catalog_loading_error, content_area, false));
    content_area.addView(error);
    content_area.requestLayout();

    final Button retry = NullCheck.notNull(error.findViewById(R.id.catalog_error_retry));
    retry.setOnClickListener(v -> this.retryFeed());
  }

  @Override
  public void onFeedLoadSuccess(
      final URI feed_uri,
      final FeedType feed_result) {

    this.log().debug("onFeedLoadSuccess: received feed for {}", feed_uri);

    synchronized (this.feed_lock) {
      this.feed = NullCheck.notNull(feed_result, "Feed result");
    }

    UIThread.runOnUIThread(
        () -> this.configureUpButton(this.getUpStack(), feed_result.getFeedTitle()));

    feed_result.matchFeed(this);
  }

  @Override
  public Unit onFeedWithGroups(final FeedWithGroups feed) {
    this.log().debug("onFeedWithGroups: received: {}", feed.getFeedURI());

    UIThread.runOnUIThread(() -> this.onFeedWithGroupsUI(feed));
    onPossiblyReceivedEULALink(this.log(), feed.getFeedTermsOfService());
    return Unit.unit();
  }

  private void onFeedWithGroupsUI(final FeedWithGroups feed) {
    this.log().debug("onFeedWithGroupsUI: received: {}", feed.getFeedURI());

    UIThread.checkIsUIThread();
    this.invalidateOptionsMenu();

    final FrameLayout content_area = this.getContentFrame();
    final ViewGroup progress = NullCheck.notNull(this.progress_layout);
    progress.setVisibility(View.GONE);
    content_area.removeAllViews();

    final LayoutInflater inflater = this.getLayoutInflater();
    final ViewGroup layout = NullCheck.notNull(
        (ViewGroup) inflater.inflate(
            R.layout.catalog_feed_groups_list, content_area, false));

    content_area.addView(layout);
    content_area.requestLayout();

    this.log().debug("restoring scroll position: {}", this.saved_scroll_pos);

    final ListView list = NullCheck.notNull(
        layout.findViewById(
            R.id.catalog_feed_blocks_list));

    this.swipe_refresh_layout =
        NullCheck.notNull(layout.findViewById(R.id.swipe_refresh_layout));
    this.swipe_refresh_layout.setOnRefreshListener(this::retryFeed);

    list.post(() -> list.setSelection(saved_scroll_pos));
    list.setDividerHeight(0);
    this.list_view = list;

    final CatalogFeedArgumentsType args = this.getArguments();
    final ImmutableStack<CatalogFeedArgumentsType> new_up_stack = this.newUpStack(args);

    final CatalogFeedLaneListenerType in_lane_listener =
        new CatalogFeedLaneListenerType() {
          @Override
          public void onSelectBook(final FeedEntryOPDS e) {
            onSelectedBook(new_up_stack, e);
          }

          @Override
          public void onSelectFeed(final FeedGroup in_group) {
            onSelectedFeedGroup(new_up_stack, in_group);
          }
        };

    final CatalogFeedWithGroups cfl;
    try {
      cfl = new CatalogFeedWithGroups(
          this,
          Simplified.getProfilesController().profileAccountCurrent(),
          Simplified.getScreenSizeInformation(),
          Simplified.getCoverProvider(),
          in_lane_listener,
          feed);
    } catch (final ProfileNoneCurrentException e) {
      throw new IllegalStateException(e);
    }

    list.setAdapter(cfl);
    list.setOnScrollListener(cfl);
  }

  @Override
  public Unit onFeedWithoutGroups(final FeedWithoutGroups feed) {
    this.log().debug("onFeedWithoutGroups: received: {}", feed.getFeedURI());

    UIThread.runOnUIThread(() -> this.onFeedWithoutGroupsUI(feed));
    onPossiblyReceivedEULALink(this.log(), feed.getFeedTermsOfService());
    return Unit.unit();
  }

  private void onFeedWithoutGroupsEmptyUI(final FeedWithoutGroups feed) {
    this.log().debug("onFeedWithoutGroupsEmptyUI: {}", feed.getFeedURI());

    UIThread.checkIsUIThread();
    Assertions.checkPrecondition(feed.isEmpty(), "Feed is empty");
    this.invalidateOptionsMenu();

    final FrameLayout content_area = this.getContentFrame();
    final ViewGroup progress = NullCheck.notNull(this.progress_layout);
    progress.setVisibility(View.GONE);
    content_area.removeAllViews();

    final LayoutInflater inflater = this.getLayoutInflater();
    final ViewGroup layout = NullCheck.notNull(
        (ViewGroup) inflater.inflate(
            R.layout.catalog_feed_nogroups_empty, content_area, false));

    final TextView empty_text = NullCheck.notNull(
        layout.findViewById(
            R.id.catalog_feed_nogroups_empty_text));

    if (this.getArguments().isSearching()) {
      final Resources resources = this.getResources();
      empty_text.setText(resources.getText(R.string.catalog_empty_feed));
    } else {
      empty_text.setText(this.catalogFeedGetEmptyText());
    }

    content_area.addView(layout);
    content_area.requestLayout();
  }

  private void onFeedWithoutGroupsNonEmptyUI(final FeedWithoutGroups feed_without_groups) {
    this.log().debug("onFeedWithoutGroupsNonEmptyUI: {}", feed_without_groups.getFeedURI());

    UIThread.checkIsUIThread();
    Assertions.checkPrecondition(!feed_without_groups.isEmpty(), "Feed is non-empty");
    this.invalidateOptionsMenu();

    final FrameLayout content_area = this.getContentFrame();
    final ViewGroup progress = NullCheck.notNull(this.progress_layout);
    progress.setVisibility(View.GONE);
    content_area.removeAllViews();

    final LayoutInflater inflater = this.getLayoutInflater();
    final ViewGroup layout = NullCheck.notNull(
        (ViewGroup) inflater.inflate(
            R.layout.catalog_feed_nogroups, content_area, false));

    content_area.addView(layout);
    content_area.requestLayout();

    this.log().debug("restoring scroll position: {}", this.saved_scroll_pos);

    final Resources resources =
        NullCheck.notNull(this.getResources());

    final GridView grid_view = NullCheck.notNull(
        layout.findViewById(
            R.id.catalog_feed_nogroups_grid));

    this.swipe_refresh_layout =
        NullCheck.notNull(layout.findViewById(R.id.swipe_refresh_layout));

    this.swipe_refresh_layout.setOnRefreshListener(() -> {
      // XXX: Refresh the feed
    });

    this.configureFacets(
        feed_without_groups, layout, Simplified.getScreenSizeInformation(), resources);

    grid_view.post(() -> grid_view.setSelection(CatalogFeedActivity.this.saved_scroll_pos));
    this.list_view = grid_view;

    final CatalogFeedArgumentsType args = this.getArguments();
    final ImmutableStack<CatalogFeedArgumentsType> new_up_stack =
        this.newUpStack(args);

    final CatalogBookSelectionListenerType book_select_listener =
        (v, e) -> CatalogFeedActivity.this.onSelectedBook(new_up_stack, e);

    final CatalogFeedWithoutGroups without;
    try {
      without = new CatalogFeedWithoutGroups(
          this,
          Simplified.getProfilesController().profileAccountCurrent(),
          Simplified.getCoverProvider(),
          book_select_listener,
          Simplified.getBooksRegistry(),
          Simplified.getBooksController(),
          Simplified.getProfilesController(),
          Simplified.getFeedLoader(),
          feed_without_groups);
    } catch (final ProfileNoneCurrentException e) {
      throw new IllegalStateException(e);
    }

    grid_view.setAdapter(without);
    grid_view.setOnScrollListener(without);

    /*
     * Subscribe the grid view to book events. This will allow individual cells to be
     * updated whenever the status of a book changes.
     */

    this.book_event_subscription =
        Simplified.getBooksRegistry()
            .bookEvents()
            .subscribe(without::onBookEvent);
  }

  private void onFeedWithoutGroupsUI(final FeedWithoutGroups f) {
    UIThread.checkIsUIThread();

    if (f.isEmpty()) {
      this.onFeedWithoutGroupsEmptyUI(f);
      return;
    }

    this.onFeedWithoutGroupsNonEmptyUI(f);
  }

  /**
   * The network is unavailable. Simply display a message and a button to allow
   * the user to retry loading when they have fixed their connection.
   */

  private void onNetworkUnavailable() {
    UIThread.checkIsUIThread();

    this.log().debug("network is unavailable");

    final FrameLayout content_area = this.getContentFrame();
    final ViewGroup progress = NullCheck.notNull(this.progress_layout);
    progress.setVisibility(View.GONE);
    content_area.removeAllViews();

    final LayoutInflater inflater = this.getLayoutInflater();
    final ViewGroup error = NullCheck.notNull(
        (ViewGroup) inflater.inflate(
            R.layout.catalog_loading_not_connected, content_area, false));
    content_area.addView(error);
    content_area.requestLayout();

    final Button retry = NullCheck.notNull(error.findViewById(R.id.catalog_error_retry));
    retry.setOnClickListener(v -> this.retryFeed());
  }

  @Override
  protected void onPause() {
    super.onPause();
    this.previously_paused = true;
  }

  @Override
  protected void onSaveInstanceState(final @Nullable Bundle state) {
    super.onSaveInstanceState(state);

    this.log().debug("saving state");

    /*
     * Save the scroll position in the hope that it can be restored later.
     */

    final Bundle nn_state = NullCheck.notNull(state);
    final AbsListView lv = this.list_view;
    if (lv != null) {
      final int position = lv.getFirstVisiblePosition();
      this.log().debug("saving list view position: {}", position);
      nn_state.putInt(CatalogFeedActivity.LIST_STATE_ID, position);
    }
  }

  private void onSelectedBook(
      final ImmutableStack<CatalogFeedArgumentsType> new_up_stack,
      final FeedEntryOPDS e) {

    this.log().debug("onSelectedBook: {}", this);
    CatalogBookDetailActivity.startNewActivity(
        this,
        new_up_stack,
        this.navigationDrawerGetPart(),
        e);
  }

  private void onSelectedFeedGroup(
      final ImmutableStack<CatalogFeedArgumentsType> new_up_stack,
      final FeedGroup f) {
    this.log().debug("onSelectFeed: {}", this);

    final CatalogFeedArgumentsRemote remote = new CatalogFeedArgumentsRemote(
        false, new_up_stack, f.getGroupTitle(), f.getGroupURI(), false);
    this.catalogActivityForkNew(remote);
  }

  /**
   * Retry the current feed.
   */

  protected final void retryFeed() {
    final CatalogFeedArgumentsType args = this.getArguments();
    this.log().debug("retrying feed {}", args);

    final FeedLoaderType loader = Simplified.getFeedLoader();

    args.matchArguments(
        new CatalogFeedArgumentsMatcherType<Unit, UnreachableCodeException>() {
          @Override
          public Unit onFeedArgumentsLocalBooks(final CatalogFeedArgumentsLocalBooks c) {
            catalogActivityForkNewReplacing(args);
            if (swipe_refresh_layout != null) {
              swipe_refresh_layout.setRefreshing(false);
            }
            return Unit.unit();
          }

          @Override
          public Unit onFeedArgumentsRemote(final CatalogFeedArgumentsRemote c) {
            loader.invalidate(c.getURI());
            catalogActivityForkNewReplacing(args);
            if (swipe_refresh_layout != null) {
              swipe_refresh_layout.setRefreshing(false);
            }
            return Unit.unit();
          }
        });
  }

  /**
   * @return The text to display when a feed is empty.
   */

  protected abstract String catalogFeedGetEmptyText();

  /**
   * Unconditionally load a locally-generated feed.
   *
   * @param c The feed arguments
   */

  private void doLoadLocalFeed(final CatalogFeedArgumentsLocalBooks c) {
    final Resources resources = getResources();

    final URI books_uri = URI.create("Books");

    final ProfileFeedRequest request =
        ProfileFeedRequest.builder(
            books_uri,
            resources.getString(R.string.books),
            resources.getString(R.string.books_sort_by),
            new CatalogFacetPseudoTitleProvider(resources))
            .setFeedSelection(c.getSelection())
            .setSearch(c.getSearchTerms())
            .setFacetActive(c.getFacetType())
            .build();

    try {
      final ListeningExecutorService exec = Simplified.getBackgroundTaskExecutor();
      FluentFuture
          .from(Simplified.getProfilesController().profileFeed(request))
          .transform(feed -> {
            this.onFeedLoadSuccess(books_uri, feed);
            return Unit.unit();
          }, exec)
          .catching(Exception.class, ex -> {
            this.onFeedLoadFailure(books_uri, ex);
            return Unit.unit();
          }, exec);
    } catch (final ProfileNoneCurrentException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Unconditionally load a remote feed.
   *
   * @param c The feed arguments
   */

  private void doLoadRemoteFeed(final CatalogFeedArgumentsRemote c) {
    this.loadFeed(Simplified.getFeedLoader(), c.getURI());
  }

  /**
   * A handler for local book searches.
   */

  private final class BooksLocalSearchQueryHandler implements OnQueryTextListener {

    private final CatalogFeedArgumentsType args;
    private final FeedFacetPseudo.FacetType facet_active;
    private final Resources resources;
    private final FeedType feed;
    private final Logger logger;

    BooksLocalSearchQueryHandler(
        final Logger in_logger,
        final Resources in_resources,
        final CatalogFeedArgumentsType in_args,
        final FeedType feed,
        final FeedFacetPseudo.FacetType in_facet_active) {

      this.logger =
          NullCheck.notNull(in_logger, "Logger");
      this.resources =
          NullCheck.notNull(in_resources, "Resources");
      this.args =
          NullCheck.notNull(in_args, "Arguments");
      this.feed =
          NullCheck.notNull(feed, "Feed");
      this.facet_active =
          NullCheck.notNull(in_facet_active, "Facet");
    }

    @Override
    public boolean onQueryTextChange(final @Nullable String s) {
      return true;
    }

    @Override
    public boolean onQueryTextSubmit(final @Nullable String query) {
      this.logger.debug("submitting local search query: {}", query);

      final String qnn = NullCheck.notNull(query);
      final CatalogFeedActivity cfa = CatalogFeedActivity.this;
      final ImmutableStack<CatalogFeedArgumentsType> us =
          ImmutableStack.empty();

      final String title = this.resources.getString(R.string.catalog_search) + ": " + qnn;
      final CatalogFeedArgumentsLocalBooks new_args =
          new CatalogFeedArgumentsLocalBooks(
              us,
              title,
              this.facet_active,
              Option.some(qnn),
              cfa.getLocalFeedTypeSelection());

      if ("Search".equals(this.feed.getFeedTitle())) {
        cfa.catalogActivityForkNewReplacing(new_args);
      } else {
        cfa.catalogActivityForkNew(new_args);
      }

      return true;
    }
  }

  /**
   * A handler for OpenSearch 1.1 searches.
   */

  private final class OpenSearchQueryHandler implements OnQueryTextListener {

    private final CatalogFeedArgumentsType args;
    private final OPDSOpenSearch1_1 search;
    private final Resources resources;
    private final FeedType feed;
    private final Logger logger;

    OpenSearchQueryHandler(
        final Logger in_logger,
        final Resources in_resources,
        final CatalogFeedArgumentsType in_args,
        final FeedType feed,
        final OPDSOpenSearch1_1 in_search) {

      this.logger =
          NullCheck.notNull(in_logger, "Logger");
      this.resources =
          NullCheck.notNull(in_resources, "Resources");
      this.args =
          NullCheck.notNull(in_args, "Arguments");
      this.feed =
          NullCheck.notNull(feed, "Feed");
      this.search =
          NullCheck.notNull(in_search, "Search");
    }

    @Override
    public boolean onQueryTextChange(final @Nullable String s) {
      return true;
    }

    @Override
    public boolean onQueryTextSubmit(final @Nullable String query) {
      this.logger.debug("submitting OpenSearch 1.1 query: {}", query);

      final String qnn = NullCheck.notNull(query);
      final URI target = this.search.getQueryURIForTerms(qnn);

      final CatalogFeedActivity cfa = CatalogFeedActivity.this;
      final ImmutableStack<CatalogFeedArgumentsType> us =
          ImmutableStack.empty();

      final String title =
          this.resources.getString(R.string.catalog_search) + ": " + qnn;

      final CatalogFeedArgumentsRemote new_args =
          new CatalogFeedArgumentsRemote(false, us, title, target, true);

      if ("Search".equals(this.feed.getFeedTitle())) {
        cfa.catalogActivityForkNewReplacing(new_args);
      } else {
        cfa.catalogActivityForkNew(new_args);
      }
      return true;
    }
  }
}
