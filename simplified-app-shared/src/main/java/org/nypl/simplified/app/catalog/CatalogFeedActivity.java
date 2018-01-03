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
import org.nypl.simplified.books.core.BookFeedListenerType;
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
import org.nypl.simplified.books.profiles.ProfileNonexistentAccountProviderException;
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

/**
 * The main catalog feed activity, responsible for displaying different types of
 * feeds.
 */

public abstract class CatalogFeedActivity extends CatalogActivity
    implements BookFeedListenerType,
    FeedMatcherType<Unit, UnreachableCodeException>,
    FeedLoaderListenerType {

  private static final String CATALOG_ARGS;
  private static final String LIST_STATE_ID;
  private static final Logger LOG;

  static {
    LOG = LogUtilities.getLog(CatalogFeedActivity.class);
    CATALOG_ARGS = "org.nypl.simplified.app.CatalogFeedActivity.arguments";
    LIST_STATE_ID = "org.nypl.simplified.app.CatalogFeedActivity.list_view_state";
  }

  private  FeedType feed;
  private  AbsListView list_view;
  private  SwipeRefreshLayout swipe_refresh_layout;
  private  Future<Unit> loading;
  private  ViewGroup progress_layout;
  private int saved_scroll_pos;
  private boolean previously_paused;
  private SearchView search_view;
  private ObservableSubscriptionType<ProfileEvent> profile_event_subscription;
  private ObservableSubscriptionType<BookStatusEvent> book_event_subscription;

  /**
   * Construct an activity.
   */

  public CatalogFeedActivity() {

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
          public Unit onFeedArgumentsLocalBooks(
              final CatalogFeedArgumentsLocalBooks c) {
            SimplifiedActivity.setActivityArguments(b, false);
            CatalogActivity.setActivityArguments(b, c.getUpStack());
            return Unit.unit();
          }

          @Override
          public Unit onFeedArgumentsRemote(
              final CatalogFeedArgumentsRemote c) {
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

  private static void onPossiblyReceivedEULALink(final OptionType<URI> latest) {
    latest.map_(
        latest_actual -> {
          final DocumentStoreType docs = Simplified.getDocumentStore();

          docs.getEULA().map_(
              eula -> {
                try {
                  eula.documentSetLatestURL(latest_actual.toURL());
                } catch (final MalformedURLException e) {
                  LOG.error("could not use latest EULA link: ", e);
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
        final List<FeedFacetType> group =
            NullCheck.notNull(facet_groups.get(group_name));
        final ArrayList<FeedFacetType> group_copy =
            new ArrayList<FeedFacetType>(group);

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

        final FeedFacetMatcherType<Unit, UnreachableCodeException>
            facet_feed_listener =
            new FeedFacetMatcherType<Unit, UnreachableCodeException>() {
              @Override
              public Unit onFeedFacetOPDS(
                  final FeedFacetOPDS feed_opds) {
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
              public Unit onFeedFacetPseudo(
                  final FeedFacetPseudo fp) {
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

        final CatalogFacetSelectionListenerType facet_listener =
            in_selected -> in_selected.matchFeedFacet(facet_feed_listener);

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
          Simplified.getProfilesController().profileAccountCurrent().provider().catalogURI(),
          false);
    } catch (final ProfileNoneCurrentException e) {
      throw new IllegalStateException(e);
    }
  }

  private void loadFeed(
      final FeedLoaderType feed_loader,
      final URI u) {

    LOG.debug("loading feed: {}", u);
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
  public void onBookFeedFailure(final Throwable e) {

    if (e instanceof CancellationException) {
      LOG.debug("Cancelled feed");
      return;
    }

    UIThread.runOnUIThread(() -> onFeedLoadingFailureUI(e));
  }

  @Override
  public void onBookFeedSuccess(final FeedWithoutGroups f) {

    LOG.debug("received locally generated feed: {}", f.getFeedID());
    this.feed = f;
    this.onFeedWithoutGroups(f);
  }

  @Override
  protected void onCreate(final @Nullable Bundle state) {
    super.onCreate(state);

    this.navigationDrawerSetActionBarTitle();

    final CatalogFeedArgumentsType args = this.getArguments();
    final ImmutableStack<CatalogFeedArgumentsType> stack = this.getUpStack();
    this.configureUpButton(stack, args.getTitle());

    final Resources rr = NullCheck.notNull(this.getResources());
    setTitle(args.getTitle().equals(NullCheck.notNull(rr.getString(R.string.feature_app_name))) ? rr.getString(R.string.catalog) : args.getTitle());

    /*
     * Attempt to restore the saved scroll position, if there is one.
     */

    if (state != null) {
      LOG.debug("received state");
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

    LOG.debug("inflating menu");
    final MenuInflater inflater = this.getMenuInflater();
    inflater.inflate(R.menu.catalog, menu_nn);

    if (this.feed == null) {
      LOG.debug("menu creation requested but feed is not yet present");
      return true;
    }

    LOG.debug("menu creation requested and feed is present");
    this.onCreateOptionsMenuSearchItem(menu_nn);
    return true;
  }

  private void onCreateOptionsMenuSearchItem(final Menu menu_nn) {
    final MenuItem search_item = menu_nn.findItem(R.id.catalog_action_search);

    /*
     * If the feed actually has a search URI, then show the search field.
     * Otherwise, disable and hide it.
     */

    final FeedType feed_actual = NullCheck.notNull(this.feed);
    final OptionType<FeedSearchType> search_opt = feed_actual.getFeedSearch();
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
      this.search_view.setQueryHint("Search " + this.feed.getFeedTitle());
      if (args.getTitle().startsWith("Search")) {
        this.search_view.setQueryHint(args.getTitle());
      }

      /*
       * Check that the search URI is of an understood type.
       */

      final Resources rr = NullCheck.notNull(this.getResources());
      final FeedSearchType search = search_some.get();
      search_ok = search.matchSearch(
          new FeedSearchMatcherType<Boolean, UnreachableCodeException>() {
            @Override
            public Boolean onFeedSearchOpen1_1(
                final FeedSearchOpen1_1 fs) {
              CatalogFeedActivity.this.search_view.setOnQueryTextListener(
                  new OpenSearchQueryHandler(rr, args, fs.getSearch()));
              return NullCheck.notNull(Boolean.TRUE);
            }

            @Override
            public Boolean onFeedSearchLocal(
                final FeedSearchLocal f) {
              CatalogFeedActivity.this.search_view.setOnQueryTextListener(
                  new BooksLocalSearchQueryHandler(rr, args, FacetType.SORT_BY_TITLE));
              return NullCheck.notNull(Boolean.TRUE);
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
    LOG.debug("onDestroy");

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

    LOG.info("Failed to get feed: ", e);
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
      final URI u,
      final FeedType f) {

    LOG.debug("received feed for {}", u);
    this.feed = f;

    final CatalogFeedActivity cfa = this;
    UIThread.runOnUIThread(() -> cfa.configureUpButton(cfa.getUpStack(), f.getFeedTitle()));
    f.matchFeed(this);
  }

  @Override
  public Unit onFeedWithGroups(final FeedWithGroups f) {

    LOG.debug("received feed with blocks: {}", f.getFeedURI());

    UIThread.runOnUIThread(() -> CatalogFeedActivity.this.onFeedWithGroupsUI(f));
    onPossiblyReceivedEULALink(f.getFeedTermsOfService());
    return Unit.unit();
  }

  private void onFeedWithGroupsUI(final FeedWithGroups f) {

    LOG.debug("received feed with blocks: {}", f.getFeedURI());

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

    LOG.debug("restoring scroll position: {}", this.saved_scroll_pos);

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
          f);
    } catch (final ProfileNoneCurrentException e) {
      throw new IllegalStateException(e);
    }

    list.setAdapter(cfl);
    list.setOnScrollListener(cfl);
  }

  @Override
  public Unit onFeedWithoutGroups(final FeedWithoutGroups f) {

    LOG.debug("received feed without blocks: {}", f.getFeedURI());

    UIThread.runOnUIThread(
        () -> CatalogFeedActivity.this.onFeedWithoutGroupsUI(f));

    onPossiblyReceivedEULALink(f.getFeedTermsOfService());
    return Unit.unit();
  }

  private void onFeedWithoutGroupsEmptyUI(final FeedWithoutGroups f) {

    LOG.debug("received feed without blocks (empty): {}", f.getFeedURI());

    UIThread.checkIsUIThread();
    Assertions.checkPrecondition(f.isEmpty(), "Feed is empty");

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

    LOG.debug("received feed without blocks (non-empty): {}", feed_without_groups.getFeedURI());

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

    LOG.debug("restoring scroll position: {}", this.saved_scroll_pos);

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

  private void onFeedWithoutGroupsUI(
      final FeedWithoutGroups f) {
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

    LOG.debug("network is unavailable");

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
  protected void onSaveInstanceState(
      final @Nullable Bundle state) {

    super.onSaveInstanceState(state);

    LOG.debug("saving state");

    /*
     * Save the scroll position in the hope that it can be restored later.
     */

    final Bundle nn_state = NullCheck.notNull(state);
    final AbsListView lv = this.list_view;
    if (lv != null) {
      final int position = lv.getFirstVisiblePosition();
      LOG.debug("saving list view position: {}", position);
      nn_state.putInt(CatalogFeedActivity.LIST_STATE_ID, position);
    }
  }

  private void onSelectedBook(
      final ImmutableStack<CatalogFeedArgumentsType> new_up_stack,
      final FeedEntryOPDS e) {

    LOG.debug("onSelectedBook: {}", this);
    CatalogBookDetailActivity.startNewActivity(
        this,
        new_up_stack,
        this.navigationDrawerGetPart(),
        e);
  }

  private void onSelectedFeedGroup(
      final ImmutableStack<CatalogFeedArgumentsType> new_up_stack,
      final FeedGroup f) {
    LOG.debug("onSelectFeed: {}", this);

    final CatalogFeedArgumentsRemote remote = new CatalogFeedArgumentsRemote(
        false, new_up_stack, f.getGroupTitle(), f.getGroupURI(), false);
    this.catalogActivityForkNew(remote);
  }

  /**
   * Retry the current feed.
   */

  protected final void retryFeed() {
    final CatalogFeedArgumentsType args = this.getArguments();
    LOG.debug("retrying feed {}", args);

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

//    final BooksType books = getBooksType();
//    final Calendar now = NullCheck.notNull(Calendar.getInstance());
//    final URI dummy_uri = NullCheck.notNull(URI.create("Books"));
//    final String dummy_id = NullCheck.notNull(resources.getString(R.string.books));
//    final String title = NullCheck.notNull(resources.getString(R.string.books));
//    final String facet_group = NullCheck.notNull(resources.getString(R.string.books_sort_by));
//    final BooksFeedSelection selection = c.getSelection();
//
//    books.booksGetFeed(
//        dummy_uri,
//        dummy_id,
//        now,
//        title,
//        c.getFacetType(),
//        facet_group,
//        new CatalogFacetPseudoTitleProvider(resources),
//        c.getSearchTerms(),
//        selection,
//        this);

    throw new UnimplementedCodeException();
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

    BooksLocalSearchQueryHandler(
        final Resources in_resources,
        final CatalogFeedArgumentsType in_args,
        final FeedFacetPseudo.FacetType in_facet_active) {

      this.resources = NullCheck.notNull(in_resources);
      this.args = NullCheck.notNull(in_args);
      this.facet_active = NullCheck.notNull(in_facet_active);
    }

    @Override
    public boolean onQueryTextChange(final @Nullable String s) {
      return true;
    }

    @Override
    public boolean onQueryTextSubmit(
        final @Nullable String query) {

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

      if ("Search".equals(feed.getFeedTitle())) {
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

    OpenSearchQueryHandler(
        final Resources in_resources,
        final CatalogFeedArgumentsType in_args,
        final OPDSOpenSearch1_1 in_search) {
      this.resources = NullCheck.notNull(in_resources);
      this.args = NullCheck.notNull(in_args);
      this.search = NullCheck.notNull(in_search);
    }

    @Override
    public boolean onQueryTextChange(
        final @Nullable String s) {
      return true;
    }

    @Override
    public boolean onQueryTextSubmit(
        final @Nullable String query) {
      final String qnn = NullCheck.notNull(query);
      final URI target = this.search.getQueryURIForTerms(qnn);

      final CatalogFeedActivity cfa = CatalogFeedActivity.this;
      final ImmutableStack<CatalogFeedArgumentsType> us =
          ImmutableStack.empty();

      final String title =
          this.resources.getString(R.string.catalog_search) + ": " + qnn;

      final CatalogFeedArgumentsRemote new_args =
          new CatalogFeedArgumentsRemote(false, us, title, target, true);

      if ("Search".equals(feed.getFeedTitle())) {
        cfa.catalogActivityForkNewReplacing(new_args);
      } else {
        cfa.catalogActivityForkNew(new_args);
      }
      return true;
    }
  }
}
