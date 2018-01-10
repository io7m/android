package org.nypl.simplified.app;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.DrawerLayout.DrawerListener;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.io7m.jfunctional.FunctionType;
import com.io7m.jfunctional.Option;
import com.io7m.jfunctional.OptionType;
import com.io7m.jfunctional.Unit;
import com.io7m.jnull.NullCheck;
import com.io7m.jnull.Nullable;

import org.nypl.simplified.app.catalog.CatalogFeedActivity;
import org.nypl.simplified.app.catalog.CatalogFeedArgumentsLocalBooks;
import org.nypl.simplified.app.catalog.CatalogFeedArgumentsRemote;
import org.nypl.simplified.app.catalog.CatalogFeedArgumentsType;
import org.nypl.simplified.app.catalog.MainBooksActivity;
import org.nypl.simplified.app.catalog.MainCatalogActivity;
import org.nypl.simplified.app.catalog.MainHoldsActivity;
import org.nypl.simplified.app.utilities.UIThread;
import org.nypl.simplified.books.accounts.AccountProvider;
import org.nypl.simplified.books.accounts.AccountType;
import org.nypl.simplified.books.controller.ProfilesControllerType;
import org.nypl.simplified.books.core.BooksFeedSelection;
import org.nypl.simplified.books.feeds.FeedFacetPseudo;
import org.nypl.simplified.books.core.LogUtilities;
import org.nypl.simplified.books.profiles.ProfileAccountSelectEvent;
import org.nypl.simplified.books.profiles.ProfileEvent;
import org.nypl.simplified.books.profiles.ProfileNoneCurrentException;
import org.nypl.simplified.books.profiles.ProfileNonexistentAccountProviderException;
import org.nypl.simplified.observable.ObservableSubscriptionType;
import org.nypl.simplified.stack.ImmutableStack;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.nypl.simplified.app.Simplified.WantActionBar.WANT_ACTION_BAR;
import static org.nypl.simplified.app.SimplifiedPart.PART_BOOKS;
import static org.nypl.simplified.app.SimplifiedPart.PART_CATALOG;
import static org.nypl.simplified.app.SimplifiedPart.PART_HOLDS;
import static org.nypl.simplified.app.SimplifiedPart.PART_MANAGE_ACCOUNTS;
import static org.nypl.simplified.app.SimplifiedPart.PART_SETTINGS;
import static org.nypl.simplified.app.SimplifiedPart.PART_SWITCHER;

/**
 * The type of non-reader activities in the app.
 * <p>
 * This is the where navigation drawer configuration takes place.
 */

public abstract class SimplifiedActivity extends Activity
    implements DrawerListener, OnItemClickListener {

  private static final Logger LOG;
  private static final String NAVIGATION_DRAWER_OPEN_ID;
  private static int ACTIVITY_COUNT;

  static {
    LOG = LogUtilities.getLog(SimplifiedActivity.class);
    NAVIGATION_DRAWER_OPEN_ID = "org.nypl.simplified.app.SimplifiedActivity.drawer_open";
  }

  private ArrayAdapter<SimplifiedPart> adapter;
  private ArrayAdapter<Object> adapter_accounts;
  private FrameLayout content_frame;
  private DrawerLayout drawer;
  private Map<SimplifiedPart, FunctionType<Bundle, Unit>> drawer_arg_funcs;
  private Map<SimplifiedPart, Class<? extends Activity>> drawer_classes_by_name;
  private List<SimplifiedPart> drawer_items;
  private ListView drawer_list;
  private SharedPreferences drawer_settings;
  private boolean finishing;
  private int selected;
  private ObservableSubscriptionType<ProfileEvent> profile_event_subscription;
  private ArrayList<Object> adapter_account_array;

  /**
   * Set the arguments for the activity that will be created.
   *
   * @param b           The argument bundle
   * @param open_drawer {@code true} iff the navigation drawer should be opened
   */

  public static void setActivityArguments(
      final Bundle b,
      final boolean open_drawer) {
    NullCheck.notNull(b);
    b.putBoolean(NAVIGATION_DRAWER_OPEN_ID, open_drawer);
  }

  /**
   * Set up a map of part names to functions that configure argument
   * bundles. Given a {@link SimplifiedPart}, this allows the construction
   * of an argument bundle for the target activity class.
   */

  private static ImmutableMap<SimplifiedPart, FunctionType<Bundle, Unit>> calculateDrawerActions(
      final Resources resources,
      final boolean holds_enabled) {

    final ImmutableMap.Builder<SimplifiedPart, FunctionType<Bundle, Unit>> drawer_actions =
        ImmutableMap.builder();

    drawer_actions.put(
        PART_BOOKS, b -> {
          final OptionType<String> no_search = Option.none();
          final ImmutableStack<CatalogFeedArgumentsType> empty_stack =
              ImmutableStack.empty();
          final CatalogFeedArgumentsLocalBooks local =
              new CatalogFeedArgumentsLocalBooks(
                  empty_stack,
                  PART_BOOKS.getPartName(resources),
                  FeedFacetPseudo.FacetType.SORT_BY_TITLE,
                  no_search,
                  BooksFeedSelection.BOOKS_FEED_LOANED);
          CatalogFeedActivity.setActivityArguments(b, local);
          return Unit.unit();
        });

    drawer_actions.put(
        PART_CATALOG, b -> {
          try {
            final ProfilesControllerType profiles = Simplified.getProfilesController();
            final AccountType account = profiles.profileAccountCurrent();

            final ImmutableStack<CatalogFeedArgumentsType> empty =
                ImmutableStack.empty();
            final CatalogFeedArgumentsRemote remote =
                new CatalogFeedArgumentsRemote(
                    false,
                    NullCheck.notNull(empty),
                    NullCheck.notNull(resources.getString(R.string.feature_app_name)),
                    profiles.profileAccountCurrentCatalogRootURI(),
                    false);
            CatalogFeedActivity.setActivityArguments(b, remote);
            return Unit.unit();
          } catch (final ProfileNoneCurrentException e) {
            throw new IllegalStateException(e);
          }
        });

    if (holds_enabled) {
      drawer_actions.put(
          PART_HOLDS, b -> {
            final OptionType<String> no_search = Option.none();
            final ImmutableStack<CatalogFeedArgumentsType> empty_stack =
                ImmutableStack.empty();
            final CatalogFeedArgumentsLocalBooks local =
                new CatalogFeedArgumentsLocalBooks(
                    empty_stack,
                    PART_HOLDS.getPartName(resources),
                    FeedFacetPseudo.FacetType.SORT_BY_TITLE,
                    no_search,
                    BooksFeedSelection.BOOKS_FEED_HOLDS);
            CatalogFeedActivity.setActivityArguments(b, local);
            return Unit.unit();
          });
    }

    drawer_actions.put(
        PART_SETTINGS, b -> {
          setActivityArguments(b, false);
          return Unit.unit();
        });

    drawer_actions.put(
        PART_SWITCHER, b -> {
          setActivityArguments(b, false);
          return Unit.unit();
        });

    return drawer_actions.build();
  }

  /**
   * Set up a map of names ↔ classes. This is used to start an activity
   * by class, given a {@link SimplifiedPart}.
   */

  private static ImmutableMap<SimplifiedPart, Class<? extends Activity>>
  calculateActivityClassesByPart(final boolean holds_enabled) {

    final ImmutableMap.Builder<SimplifiedPart, Class<? extends Activity>> classes_by_name =
        ImmutableMap.builder();

    classes_by_name.put(PART_BOOKS, MainBooksActivity.class);
    classes_by_name.put(PART_CATALOG, MainCatalogActivity.class);
    if (holds_enabled) {
      classes_by_name.put(PART_HOLDS, MainHoldsActivity.class);
    }
    classes_by_name.put(PART_SETTINGS, MainSettingsActivity.class);
    return classes_by_name.build();
  }

  /**
   * Calculate the basic items that should appear in the navigation drawer.
   */

  private static ImmutableList<SimplifiedPart> calculateDrawerItems(
      final boolean holds_enabled) {

    final ImmutableList.Builder<SimplifiedPart> drawer_items = ImmutableList.builder();
    drawer_items.add(PART_SWITCHER);
    drawer_items.add(PART_CATALOG);
    drawer_items.add(PART_BOOKS);
    if (holds_enabled) {
      drawer_items.add(PART_HOLDS);
    }
    drawer_items.add(PART_SETTINGS);
    return drawer_items.build();
  }

  /**
   * @return The application part to which this activity belongs
   */

  protected abstract SimplifiedPart navigationDrawerGetPart();

  /**
   * @return {@code true} iff the navigation drawer should show an indicator
   */

  protected abstract boolean navigationDrawerShouldShowIndicator();

  private void finishWithConditionalAnimationOverride() {
    this.finish();

    /*
     * If this activity is the last activity, do not override the closing
     * transition animation.
     */

    if (ACTIVITY_COUNT > 1) {
      this.overridePendingTransition(0, 0);
    }
  }

  protected final FrameLayout getContentFrame() {
    return NullCheck.notNull(this.content_frame);
  }

  private void hideKeyboard() {
    // Check if no view has focus:
    final View view = this.getCurrentFocus();
    if (view != null) {
      final InputMethodManager im = (InputMethodManager) this.getSystemService(
          Context.INPUT_METHOD_SERVICE);
      im.hideSoftInputFromWindow(
          view.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
    }
  }

  protected final void navigationDrawerSetActionBarTitle() {
    final ActionBar bar = NullCheck.notNull(this.getActionBar());
    final Resources rr = NullCheck.notNull(this.getResources());
    final SimplifiedPart part = this.navigationDrawerGetPart();
    bar.setTitle(part.getPartName(rr));
  }

  @Override
  public void onBackPressed() {
    LOG.debug("onBackPressed: {}", this);
    final Resources rr = NullCheck.notNull(this.getResources());

    final DrawerLayout d = NullCheck.notNull(this.drawer);
    final ActionBar bar = this.getActionBar();
    if (d.isDrawerOpen(GravityCompat.START)) {
      this.finishing = true;
      d.closeDrawer(GravityCompat.START);
      bar.setHomeActionContentDescription(rr.getString(R.string.navigation_accessibility_drawer_show));
    } else {

      /*
       * If this activity is the last activity, do not override the closing
       * transition animation.
       */

      if (ACTIVITY_COUNT == 1) {
        if (this.getClass() != MainCatalogActivity.class) {
          // got to main catalog activity
          //final DrawerLayout d = NullCheck.notNull(this.drawer);
          this.selected = 1;
          this.startSideBarActivity();


        } else {
          d.openDrawer(GravityCompat.START);
          bar.setHomeActionContentDescription(rr.getString(R.string.navigation_accessibility_drawer_hide));
        }
      } else {
        this.finishWithConditionalAnimationOverride();
      }
    }
  }

  private void startSideBarActivity() {
    if (this.selected != -1) {
      final List<SimplifiedPart> di = NullCheck.notNull(this.drawer_items);

      final SimplifiedPart name = NullCheck.notNull(di.get(this.selected));

      if (this.selected > 0) {
        final Map<SimplifiedPart, Class<? extends Activity>> dc =
            NullCheck.notNull(this.drawer_classes_by_name);
        final Class<? extends Activity> c = NullCheck.notNull(dc.get(name));

        final Map<SimplifiedPart, FunctionType<Bundle, Unit>> fas =
            NullCheck.notNull(this.drawer_arg_funcs);
        final FunctionType<Bundle, Unit> fa = NullCheck.notNull(fas.get(name));

        final Bundle b = new Bundle();
        setActivityArguments(b, false);
        fa.call(b);

        final Intent i = new Intent();
        i.setClass(this, c);
        i.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        i.putExtras(b);
        this.startActivity(i);

        this.overridePendingTransition(0, 0);
      } else {
        // replace drawer with selection of libraries
        final ListView dl =
            NullCheck.notNull(this.findViewById(R.id.left_drawer));

        dl.setOnItemClickListener(this);
        dl.setAdapter(this.adapter_accounts);
      }
    }

    if (this.selected > 0) {
      this.selected = -1;
      final DrawerLayout d = NullCheck.notNull(this.drawer);
      d.closeDrawer(GravityCompat.START);
    }
  }

  @Override
  protected void onCreate(final @Nullable Bundle state) {

    this.setTheme(Simplified.getCurrentTheme(WANT_ACTION_BAR));
    super.onCreate(state);

    LOG.debug("onCreate: {}", this);
    this.setContentView(R.layout.main);

    boolean open_drawer = true;
    final Intent i = NullCheck.notNull(this.getIntent());
    LOG.debug("non-null intent");
    final Bundle a = i.getExtras();
    if (a != null) {
      LOG.debug("non-null intent extras");
      open_drawer = a.getBoolean(NAVIGATION_DRAWER_OPEN_ID);
      LOG.debug("drawer requested: {}", open_drawer);
    }

    /*
     * The activity is being re-initialized. Set the drawer to whatever
     * state it was in when the activity was destroyed.
     */

    if (state != null) {
      LOG.debug("reinitializing");
      open_drawer = state.getBoolean(NAVIGATION_DRAWER_OPEN_ID, open_drawer);
    }

    /*
     * As per the Android design documents: If the user has manually opened
     * the navigation drawer, then the user is assumed to understand how the
     * drawer works. Therefore, if it appears that the drawer should be
     * opened, check to see if it should actually be closed.
     *
     * XXX: Make this part of the profile preferences
     */

    final SharedPreferences in_drawer_settings =
        NullCheck.notNull(this.getSharedPreferences("drawer-settings", 0));
    if (in_drawer_settings.getBoolean("has-opened-manually", false)) {
      LOG.debug("user has manually opened drawer in the past, not opening it now!");
      open_drawer = false;
    }
    this.drawer_settings = in_drawer_settings;

    /*
     * Holds are an optional feature. If they are disabled, then the item
     * is simply removed from the navigation drawer.
     */

    final Resources resources = NullCheck.notNull(this.getResources());
    final boolean holds_enabled = resources.getBoolean(R.bool.feature_holds_enabled);

    /*
     * Configure the navigation drawer.
     */

    final DrawerLayout drawer_layout =
        NullCheck.notNull(this.findViewById(R.id.drawer_layout));
    final ListView drawer_list_view =
        NullCheck.notNull(this.findViewById(R.id.left_drawer));
    final FrameLayout frame_layout =
        NullCheck.notNull(this.findViewById(R.id.content_frame));

    drawer_layout.setDrawerListener(this);
    drawer_layout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);
    drawer_list_view.setOnItemClickListener(this);
    drawer_layout.setDrawerTitle(Gravity.LEFT, resources.getString(R.string.navigation_accessibility));

    final LayoutInflater inflater = NullCheck.notNull(this.getLayoutInflater());

    this.adapter_account_array =
        new ArrayList<>();
    this.adapter_accounts =
        new ArrayAdapterWithAccounts(
            this,
            this.getAssets(),
            this.adapter_account_array,
            inflater);
    this.adapter =
        new ArrayAdapterWithoutAccounts(
            this,
            this.getAssets(),
            calculateDrawerItems(holds_enabled),
            inflater,
            resources,
            drawer_list_view);

    drawer_list_view.setAdapter(this.adapter);

    final ImmutableMap<SimplifiedPart, Class<? extends Activity>> classes_by_name =
        calculateActivityClassesByPart(holds_enabled);
    final ImmutableMap<SimplifiedPart, FunctionType<Bundle, Unit>> drawer_actions =
        calculateDrawerActions(resources, holds_enabled);

    /*
     * Show or hide the three dashes next to the home button.
     */

    final ActionBar bar = NullCheck.notNull(this.getActionBar(), "Action bar");
    if (this.navigationDrawerShouldShowIndicator()) {
      LOG.debug("setting navigation drawer indicator");
      if (android.os.Build.VERSION.SDK_INT < 21) {
        bar.setDisplayHomeAsUpEnabled(false);
        bar.setHomeButtonEnabled(true);
      }
    }

    /*
     * If the drawer should be open, open it.
     */

    if (open_drawer) {
      drawer_layout.openDrawer(GravityCompat.START);
      bar.setHomeActionContentDescription(resources.getString(R.string.navigation_accessibility_drawer_hide));
    }

    this.drawer_items = calculateDrawerItems(holds_enabled);
    this.drawer_classes_by_name = classes_by_name;
    this.drawer_arg_funcs = drawer_actions;
    this.drawer = drawer_layout;
    this.drawer_list = drawer_list_view;
    this.content_frame = frame_layout;
    this.selected = -1;
    ACTIVITY_COUNT = ACTIVITY_COUNT + 1;

    LOG.debug("activity count: {}", ACTIVITY_COUNT);

    this.profile_event_subscription =
        Simplified.getProfilesController()
            .profileEvents()
            .subscribe(this::onProfileEvent);

    this.populateSidebar();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    LOG.debug("onDestroy: {}", this);
    ACTIVITY_COUNT = ACTIVITY_COUNT - 1;
    this.profile_event_subscription.unsubscribe();
  }

  private void onProfileEvent(final ProfileEvent event) {
    if (event instanceof ProfileAccountSelectEvent) {
      final ProfileAccountSelectEvent event_select = (ProfileAccountSelectEvent) event;
      event_select.matchSelect(
          this::onProfileAccountSelectSucceeded,
          this::onProfileAccountSelectFailed);
      return;
    }
  }

  private Unit onProfileAccountSelectFailed(
      final ProfileAccountSelectEvent.ProfileAccountSelectFailed event) {

    LOG.debug("onProfileAccountSelectFailed: {}", event);
    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setMessage(R.string.profiles_account_selection_error_general);
    builder.create().show();
    return Unit.unit();
  }

  private Unit onProfileAccountSelectSucceeded(
      final ProfileAccountSelectEvent.ProfileAccountSelectSucceeded event) {

    LOG.debug("onProfileAccountSelectSucceeded: {}", event);
    return Unit.unit();
  }

  private void populateSidebar() {
    try {
      UIThread.checkIsUIThread();

      final ImmutableList<AccountProvider> drawer_item_accounts =
          Simplified.getProfilesController().profileCurrentlyUsedAccountProviders();

      final ImmutableList<Object> drawer_item_accounts_untyped =
          ImmutableList.builder()
              .addAll(drawer_item_accounts)
              .add(PART_MANAGE_ACCOUNTS)
              .build();

      this.adapter_account_array.clear();
      this.adapter_account_array.addAll(drawer_item_accounts_untyped);
      this.adapter_accounts.notifyDataSetChanged();

    } catch (final ProfileNoneCurrentException | ProfileNonexistentAccountProviderException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public final void onDrawerClosed(
      final @Nullable View drawer_view) {

    LOG.debug("onDrawerClosed: selected: {}", this.selected);

    /*
     * If the drawer is closing because the user pressed the back button, then
     * finish the activity.
     */

    if (this.finishing) {
      this.finishWithConditionalAnimationOverride();
    }
  }

  @Override
  public final void onDrawerOpened(
      final @Nullable View drawer_view) {
    this.selected = -1;
    LOG.debug("onDrawerOpened: {}", drawer_view);

    final SharedPreferences in_drawer_settings = NullCheck.notNull(this.drawer_settings);
    in_drawer_settings.edit().putBoolean("has-opened-manually", true).apply();
    this.hideKeyboard();
  }

  @Override
  public final void onDrawerSlide(
      final @Nullable View drawer_view,
      final float slide_offset) {
    // Nothing
  }

  @Override
  public final void onDrawerStateChanged(
      final int new_state) {
    LOG.debug("onDrawerStateChanged: {}", new_state);
  }

  @Override
  public void onItemClick(
      final @Nullable AdapterView<?> parent,
      final @Nullable View view,
      final int position,
      final long id) {

    final ListView drawer_list =
        NullCheck.notNull(this.findViewById(R.id.left_drawer));

    if (drawer_list.getAdapter().equals(this.adapter)) {
      LOG.debug("onItemClick: {}", position);
      final Resources rr = NullCheck.notNull(this.getResources());
      final ActionBar bar = this.getActionBar();
      bar.setHomeActionContentDescription(rr.getString(R.string.navigation_accessibility_drawer_show));
      this.selected = position;
      this.startSideBarActivity();
      return;
    }

    final Object object = this.adapter_accounts.getItem(position);
    LOG.debug("onItemClick: {}", object);

    if (object instanceof AccountProvider) {
      final AccountProvider provider = (AccountProvider) object;
      Simplified.getProfilesController().profileAccountSelectByProvider(provider.id());
      return;
    }

    this.selected = this.adapter.getCount() - 1;
    this.startSideBarActivity();
  }

  @Override
  public boolean onOptionsItemSelected(
      final @Nullable MenuItem item_mn) {
    final MenuItem item = NullCheck.notNull(item_mn);
    final Resources rr = NullCheck.notNull(this.getResources());

    switch (item.getItemId()) {
      case android.R.id.home: {
        final DrawerLayout d = NullCheck.notNull(this.drawer);
        final ActionBar bar = this.getActionBar();
        if (d.isDrawerOpen(GravityCompat.START)) {
          d.closeDrawer(GravityCompat.START);
          bar.setHomeActionContentDescription(rr.getString(R.string.navigation_accessibility_drawer_show));
        } else {
          d.openDrawer(GravityCompat.START);
          bar.setHomeActionContentDescription(rr.getString(R.string.navigation_accessibility_drawer_hide));
        }

        return super.onOptionsItemSelected(item);
      }

      default: {
        return super.onOptionsItemSelected(item);
      }
    }
  }

  @Override
  protected void onResume() {
    super.onResume();

    LOG.debug("onResume: {}", this);

    final List<SimplifiedPart> drawer_items = NullCheck.notNull(this.drawer_items);
    final ListView drawer_list_view = NullCheck.notNull(this.drawer_list);
    final SimplifiedPart part = this.navigationDrawerGetPart();

    final int pos = drawer_items.indexOf(part);
    LOG.debug("restored selected item: {}", pos);

    drawer_list_view.setSelection(pos);
    drawer_list_view.setItemChecked(pos, true);

    // opening the drawer, when the user comes back from the help section, the view will not be empty.

  }

  @Override
  protected void onSaveInstanceState(
      final @Nullable Bundle state) {
    super.onSaveInstanceState(state);

    /*
     * Save the state of the navigation drawer. The intention here is that
     * the draw will correctly be open or closed based on the state it was
     * in when the user left the activity. In practice, the drawer tends to
     * close whenever the user moves to a new activity, so saving and restoring
     * may be redundant.
     */

    final Bundle state_nn = NullCheck.notNull(state);
    final DrawerLayout d = NullCheck.notNull(this.drawer);
    state_nn.putBoolean(NAVIGATION_DRAWER_OPEN_ID, d.isDrawerOpen(GravityCompat.START));
  }

  /**
   * An array adapter that shows application parts.
   */

  private static final class ArrayAdapterWithoutAccounts extends ArrayAdapter<SimplifiedPart> {

    private final List<SimplifiedPart> drawer_items;
    private final LayoutInflater inflater;
    private final Resources resources;
    private final ListView drawer_list_view;
    private final AssetManager assets;

    ArrayAdapterWithoutAccounts(
        final SimplifiedActivity activity,
        final AssetManager in_assets,
        final List<SimplifiedPart> drawer_items,
        final LayoutInflater inflater,
        final Resources resources,
        final ListView drawer_list_view) {

      super(activity, R.layout.drawer_item, drawer_items);
      this.drawer_items = drawer_items;
      this.assets = in_assets;
      this.inflater = inflater;
      this.resources = resources;
      this.drawer_list_view = drawer_list_view;
    }

    @Override
    public View getView(
        final int position,
        final @Nullable View reuse,
        final @Nullable ViewGroup parent) {

      View v;
      if (reuse != null) {
        v = reuse;
      } else {
        v = inflater.inflate(R.layout.drawer_item, parent, false);
      }
      final SimplifiedPart part = NullCheck.notNull(drawer_items.get(position));

      if (part.equals(PART_SWITCHER)) {
        v = inflater.inflate(R.layout.drawer_item_current_account, parent, false);
      }

      final TextView text_view =
          NullCheck.notNull(v.findViewById(android.R.id.text1));
      final ImageView icon_view =
          NullCheck.notNull(v.findViewById(R.id.cellIcon));

      if (part.equals(PART_SWITCHER)) {
        v.setBackgroundResource(R.drawable.textview_underline);

        final AccountType account;
        try {
          account = Simplified.getProfilesController().profileAccountCurrent();
        } catch (final ProfileNoneCurrentException e) {
          throw new IllegalStateException(e);
        }

        final AccountProvider account_provider = account.provider();
        text_view.setText(account_provider.displayName());
        SimplifiedIconViews.configureIconViewFromURI(
            this.assets, icon_view, account_provider.logo());

      } else {
        text_view.setText(part.getPartName(resources));
        if (drawer_list_view.getCheckedItemPosition() == position) {
          text_view.setContentDescription(text_view.getText() + ". selected.");

          if (PART_CATALOG == part) {
            icon_view.setImageResource(R.drawable.menu_icon_catalog_white);
          } else if (PART_BOOKS == part) {
            icon_view.setImageResource(R.drawable.menu_icon_books_white);
          } else if (PART_HOLDS == part) {
            icon_view.setImageResource(R.drawable.menu_icon_holds_white);
          } else if (PART_SETTINGS == part) {
            icon_view.setImageResource(R.drawable.menu_icon_settings_white);
          }

        } else {
          if (PART_CATALOG == part) {
            icon_view.setImageResource(R.drawable.menu_icon_catalog);
          } else if (PART_BOOKS == part) {
            icon_view.setImageResource(R.drawable.menu_icon_books);
          } else if (PART_HOLDS == part) {
            icon_view.setImageResource(R.drawable.menu_icon_holds);
          } else if (PART_SETTINGS == part) {
            icon_view.setImageResource(R.drawable.menu_icon_settings);
          }

        }
      }

      return v;
    }
  }

  /**
   * An array adapter that shows both application parts and account providers.
   */

  private static final class ArrayAdapterWithAccounts extends ArrayAdapter<Object> {

    private final ArrayList<Object> drawer_item_accounts_untyped;
    private final LayoutInflater inflater;
    private final AssetManager assets;

    ArrayAdapterWithAccounts(
        final SimplifiedActivity activity,
        final AssetManager in_assets,
        final ArrayList<Object> drawer_item_accounts_untyped,
        final LayoutInflater inflater) {

      super(activity, R.layout.drawer_item_account, drawer_item_accounts_untyped);
      this.drawer_item_accounts_untyped = drawer_item_accounts_untyped;
      this.assets = in_assets;
      this.inflater = inflater;
    }

    @Override
    public View getView(
        final int position,
        final @Nullable View reuse,
        final @Nullable ViewGroup parent) {

      final View v;
      if (reuse != null) {
        v = reuse;
      } else {
        v = inflater.inflate(R.layout.drawer_item_account, parent, false);
      }

      final TextView text_view =
          NullCheck.notNull(v.findViewById(android.R.id.text1));
      final ImageView icon_view =
          NullCheck.notNull(v.findViewById(R.id.cellIcon));

      final Object object = NullCheck.notNull(drawer_item_accounts_untyped.get(position));
      if (object instanceof AccountProvider) {
        final AccountProvider account_provider = (AccountProvider) object;
        text_view.setText(account_provider.displayName());
        SimplifiedIconViews.configureIconViewFromURI(
            this.assets, icon_view, account_provider.logo());
        return v;
      }

      text_view.setText(R.string.settings_manage_accounts);
      icon_view.setImageResource(R.drawable.menu_icon_settings);
      return v;
    }
  }
}
