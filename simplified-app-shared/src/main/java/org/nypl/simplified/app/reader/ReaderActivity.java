package org.nypl.simplified.app.reader;

import android.app.Activity;
import android.app.FragmentManager;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.io7m.jfunctional.OptionType;
import com.io7m.jfunctional.Some;
import com.io7m.jnull.NullCheck;
import com.io7m.jnull.Nullable;

import org.nypl.simplified.app.ProfileTimeOutActivity;
import org.nypl.simplified.app.SimplifiedActivity;
import org.nypl.simplified.app.R;
import org.nypl.simplified.app.Simplified;
import org.nypl.simplified.app.reader.ReaderPaginationChangedEvent.OpenPage;
import org.nypl.simplified.app.reader.ReaderReadiumViewerSettings.ScrollMode;
import org.nypl.simplified.app.reader.ReaderReadiumViewerSettings.SyntheticSpreadMode;
import org.nypl.simplified.app.reader.ReaderTOC.TOCElement;
import org.nypl.simplified.app.utilities.ErrorDialogUtilities;
import org.nypl.simplified.app.utilities.FadeUtilities;
import org.nypl.simplified.app.utilities.UIThread;
import org.nypl.simplified.books.accounts.AccountID;
import org.nypl.simplified.books.accounts.AccountType;
import org.nypl.simplified.books.accounts.AccountsDatabaseNonexistentException;
import org.nypl.simplified.books.book_database.BookDatabaseEntryType;
import org.nypl.simplified.books.book_database.BookDatabaseException;
import org.nypl.simplified.books.book_database.BookID;
import org.nypl.simplified.books.core.LogUtilities;
import org.nypl.simplified.books.profiles.ProfileEvent;
import org.nypl.simplified.books.profiles.ProfileNoneCurrentException;
import org.nypl.simplified.books.profiles.ProfilePreferences;
import org.nypl.simplified.books.profiles.ProfilePreferencesChanged;
import org.nypl.simplified.books.profiles.ProfileReadableType;
import org.nypl.simplified.books.reader.ReaderBookLocation;
import org.nypl.simplified.books.reader.ReaderColorScheme;
import org.nypl.simplified.books.reader.ReaderPreferences;
import org.nypl.simplified.observable.ObservableSubscriptionType;
import org.readium.sdk.android.Container;
import org.readium.sdk.android.Package;
import org.slf4j.Logger;

import java.io.File;
import java.net.URI;
import java.util.List;

import static org.nypl.simplified.app.Simplified.WantActionBar.WANT_NO_ACTION_BAR;

/**
 * The main reader activity for reading an EPUB.
 */

public final class ReaderActivity extends ProfileTimeOutActivity implements
    ReaderHTTPServerStartListenerType,
    ReaderSimplifiedFeedbackListenerType,
    ReaderReadiumFeedbackListenerType,
    ReaderReadiumEPUBLoadListenerType,
    ReaderCurrentPageListenerType,
    ReaderTOCSelectionListenerType,
    ReaderMediaOverlayAvailabilityListenerType {

  private static final String BOOK_ID;
  private static final String ACCOUNT_ID;
  private static final Logger LOG;

  static {
    LOG = LogUtilities.getLog(ReaderActivity.class);
    BOOK_ID = "org.nypl.simplified.app.ReaderActivity.book";
    ACCOUNT_ID = "org.nypl.simplified.app.ReaderActivity.account_id";
  }

  private BookID book_id;
  private Container epub_container;
  private ReaderReadiumJavaScriptAPIType readium_js_api;
  private ReaderSimplifiedJavaScriptAPIType simplified_js_api;
  private ViewGroup view_hud;
  private ProgressBar view_loading;
  private ViewGroup view_media;
  private ImageView view_media_next;
  private ImageView view_media_play;
  private ImageView view_media_prev;
  private ProgressBar view_progress_bar;
  private TextView view_progress_text;
  private View view_root;
  private ImageView view_settings;
  private TextView view_title_text;
  private ImageView view_toc;
  private WebView view_web_view;
  private ReaderReadiumViewerSettings viewer_settings;
  private boolean web_view_resized;
  private ReaderBookLocation current_location;
  private AccountType account;
  private ProfileReadableType profile;
  private ObservableSubscriptionType<ProfileEvent> settings_subscription;

  /**
   * Construct an activity.
   */

  public ReaderActivity() {

  }

  /**
   * Start a new reader for the given book.
   *
   * @param from    The parent activity
   * @param account The account that owns the book
   * @param book    The unique ID of the book
   */

  public static void startActivity(
      final Activity from,
      final AccountType account,
      final BookID book) {

    NullCheck.notNull(from, "from");
    NullCheck.notNull(account, "account");
    NullCheck.notNull(book, "book");

    final Bundle b = new Bundle();
    b.putSerializable(ReaderActivity.BOOK_ID, book);
    b.putSerializable(ReaderActivity.ACCOUNT_ID, account.id());
    final Intent i = new Intent(from, ReaderActivity.class);
    i.putExtras(b);
    from.startActivity(i);
  }

  /**
   * Apply the given color scheme to all views. Unfortunately, there does not
   * seem to be a more pleasant way, in the Android API, than manually applying
   * values to all of the views in the hierarchy.
   */

  private void applyViewerColorScheme(final ReaderColorScheme cs) {
    LOG.debug("applying color scheme");

    final View in_root = NullCheck.notNull(this.view_root);
    UIThread.runOnUIThread(() -> {
      in_root.setBackgroundColor(ReaderColorSchemes.background(cs));
    });
  }

  private void makeInitialReadiumRequest(final ReaderHTTPServerType hs) {
    final URI reader_uri = URI.create(hs.getURIBase() + "reader.html");
    final WebView wv = NullCheck.notNull(this.view_web_view);

    UIThread.runOnUIThread(() -> {
      LOG.debug("making initial reader request: {}", reader_uri);
      wv.loadUrl(reader_uri.toString());
    });
  }

  @Override
  protected void onActivityResult(
      final int request_code,
      final int result_code,
      final @Nullable Intent data) {
    super.onActivityResult(request_code, result_code, data);

    LOG.debug("onActivityResult: {} {} {}", request_code, result_code, data);

    if (request_code == ReaderTOCActivity.TOC_SELECTION_REQUEST_CODE) {
      if (result_code == Activity.RESULT_OK) {
        final Intent nnd = NullCheck.notNull(data);
        final Bundle b = NullCheck.notNull(nnd.getExtras());
        final TOCElement e = NullCheck.notNull(
            (TOCElement) b.getSerializable(ReaderTOCActivity.TOC_SELECTED_ID));
        this.onTOCSelectionReceived(e);
      }
    }
  }

  @Override
  public void onConfigurationChanged(final @Nullable Configuration c) {
    super.onConfigurationChanged(c);

    LOG.debug("configuration changed");

    final WebView in_web_view =
        NullCheck.notNull(this.view_web_view);
    final TextView in_progress_text =
        NullCheck.notNull(this.view_progress_text);
    final ProgressBar in_progress_bar =
        NullCheck.notNull(this.view_progress_bar);

    in_web_view.setVisibility(View.INVISIBLE);
    in_progress_text.setVisibility(View.INVISIBLE);
    in_progress_bar.setVisibility(View.INVISIBLE);

    this.web_view_resized = true;
    UIThread.runOnUIThreadDelayed(() -> {
      final ReaderReadiumJavaScriptAPIType readium_js =
          NullCheck.notNull(ReaderActivity.this.readium_js_api);
      readium_js.getCurrentPage(ReaderActivity.this);
      readium_js.mediaOverlayIsAvailable(ReaderActivity.this);
    }, 300L);
  }

  @Override
  protected void onCreate(final @Nullable Bundle state) {

    this.setTheme(Simplified.getCurrentTheme(WANT_NO_ACTION_BAR));
    super.onCreate(state);
    this.setContentView(R.layout.reader);

    LOG.debug("starting");

    final Intent i = NullCheck.notNull(this.getIntent());
    final Bundle a = NullCheck.notNull(i.getExtras());

    this.book_id =
        NullCheck.notNull((BookID) a.getSerializable(ReaderActivity.BOOK_ID));
    final AccountID account_id =
        (AccountID) a.getSerializable(ReaderActivity.ACCOUNT_ID);

    LOG.debug("book id:    {}", this.book_id);
    LOG.debug("account id: {}", account_id);

    try {
      this.profile = Simplified.getProfilesController().profileCurrent();
      this.account = this.profile.account(account_id);
      String message = "book_opened," + this.profile.id().id() + "," + this.profile.displayName() + "," + this.book_id;
      Simplified.getAnalyticsController().logToAnalytics(message);  
    } catch (final AccountsDatabaseNonexistentException | ProfileNoneCurrentException e) {
      this.failWithErrorMessage(this.getResources(), e);
      return;
    }

    final BookDatabaseEntryType db_entry;
    final File epub_file;
    try {
      db_entry = account.bookDatabase().entry(book_id);
      final OptionType<File> file_opt = db_entry.book().file();
      if (file_opt.isSome()) {
        epub_file = ((Some<File>) file_opt).get();
      } else {
        throw new ReaderMissingEPUBException();
      }
    } catch (final BookDatabaseException | ReaderMissingEPUBException e) {
      this.failWithErrorMessage(this.getResources(), e);
      return;
    }

    final ReaderPreferences reader_preferences =
        this.profile.preferences()
            .readerPreferences();

    this.viewer_settings =
        new ReaderReadiumViewerSettings(
            SyntheticSpreadMode.SINGLE,
            ScrollMode.AUTO,
            (int) reader_preferences.fontScale(),
            20);

    final ReaderReadiumFeedbackDispatcherType rd =
        ReaderReadiumFeedbackDispatcher.newDispatcher();
    final ReaderSimplifiedFeedbackDispatcherType sd =
        ReaderSimplifiedFeedbackDispatcher.newDispatcher();

    final ViewGroup in_hud =
        NullCheck.notNull(this.findViewById(R.id.reader_hud_container));
    final ImageView in_toc =
        NullCheck.notNull(in_hud.findViewById(R.id.reader_toc));
    final ImageView in_settings =
        NullCheck.notNull(in_hud.findViewById(R.id.reader_settings));
    final TextView in_title_text =
        NullCheck.notNull(in_hud.findViewById(R.id.reader_title_text));
    final TextView in_progress_text =
        NullCheck.notNull(in_hud.findViewById(R.id.reader_position_text));
    final ProgressBar in_progress_bar =
        NullCheck.notNull(in_hud.findViewById(R.id.reader_position_progress));

    final ViewGroup in_media_overlay =
        NullCheck.notNull(this.findViewById(R.id.reader_hud_media));
    final ImageView in_media_previous =
        NullCheck.notNull(this.findViewById(R.id.reader_hud_media_previous));
    final ImageView in_media_next =
        NullCheck.notNull(this.findViewById(R.id.reader_hud_media_next));
    final ImageView in_media_play =
        NullCheck.notNull(this.findViewById(R.id.reader_hud_media_play));

    final ProgressBar in_loading =
        NullCheck.notNull(this.findViewById(R.id.reader_loading));
    final WebView in_webview =
        NullCheck.notNull(this.findViewById(R.id.reader_webview));

    this.view_root = NullCheck.notNull(in_hud.getRootView());

    in_loading.setVisibility(View.VISIBLE);
    in_progress_bar.setVisibility(View.INVISIBLE);
    in_progress_text.setVisibility(View.INVISIBLE);
    in_webview.setVisibility(View.INVISIBLE);
    in_hud.setVisibility(View.VISIBLE);
    in_media_overlay.setVisibility(View.INVISIBLE);

    in_settings.setOnClickListener(view -> {
      final FragmentManager fm = ReaderActivity.this.getFragmentManager();
      final ReaderSettingsDialog dialog =
          ReaderSettingsDialog.create(Simplified.getProfilesController());
      dialog.show(fm, "settings-dialog");
    });

    this.setReaderBrightness(reader_preferences);

    this.view_loading = in_loading;
    this.view_progress_text = in_progress_text;
    this.view_progress_bar = in_progress_bar;
    this.view_title_text = in_title_text;
    this.view_web_view = in_webview;
    this.view_hud = in_hud;
    this.view_toc = in_toc;
    this.view_settings = in_settings;
    this.web_view_resized = true;
    this.view_media = in_media_overlay;
    this.view_media_next = in_media_next;
    this.view_media_prev = in_media_previous;
    this.view_media_play = in_media_play;

    final WebChromeClient wc_client = new WebChromeClient() {
      @Override
      public void onShowCustomView(
          final @Nullable View view,
          final @Nullable CustomViewCallback callback) {
        super.onShowCustomView(view, callback);
        LOG.debug("web-chrome: {}", view);
      }
    };

    final WebViewClient wv_client =
        new ReaderWebViewClient(this, sd, this, rd, this);
    in_webview.setBackgroundColor(0x00000000);
    in_webview.setWebChromeClient(wc_client);
    in_webview.setWebViewClient(wv_client);
    in_webview.setOnLongClickListener(view -> {
      LOG.debug("ignoring long click on web view");
      return true;
    });

    // Allow the webview to be debuggable only if this is a dev build
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
        WebView.setWebContentsDebuggingEnabled(true);
      }
    }

    final WebSettings s = NullCheck.notNull(in_webview.getSettings());
    s.setAppCacheEnabled(false);
    s.setAllowFileAccess(false);
    s.setAllowFileAccessFromFileURLs(false);
    s.setAllowContentAccess(false);
    s.setAllowUniversalAccessFromFileURLs(false);
    s.setSupportMultipleWindows(false);
    s.setCacheMode(WebSettings.LOAD_NO_CACHE);
    s.setGeolocationEnabled(false);
    s.setJavaScriptEnabled(true);

    this.readium_js_api = ReaderReadiumJavaScriptAPI.newAPI(in_webview);
    this.simplified_js_api = ReaderSimplifiedJavaScriptAPI.newAPI(in_webview);

    in_title_text.setText("");

    final ReaderReadiumEPUBLoaderType pl = Simplified.getReadiumEPUBLoader();
    pl.loadEPUB(
        ReaderReadiumEPUBLoadRequest.builder(epub_file)
            .setAdobeRightsFile(db_entry.book().adobeRightsFile())
            .build(), this);

    this.settings_subscription =
        Simplified.getProfilesController()
            .profileEvents()
            .subscribe(this::onProfileEvent);
  }

  private void setReaderBrightness(final ReaderPreferences reader_preferences) {
    final WindowManager.LayoutParams layout_params = getWindow().getAttributes();
    layout_params.screenBrightness = (float) reader_preferences.brightness();
    getWindow().setAttributes(layout_params);
  }

  private void onProfileEvent(final ProfileEvent event) {
    LOG.debug("onProfileEvent: {}", event);

    if (event instanceof ProfilePreferencesChanged) {
      final ProfilePreferencesChanged changed = (ProfilePreferencesChanged) event;

      final ProfilePreferences preferences;
      try {
        preferences = Simplified.getProfilesController().profileCurrent().preferences();
      } catch (final ProfileNoneCurrentException e) {
        LOG.error("profile is not current: ", e);
        return;
      }

      if (changed.changedReaderPreferences()) {
        LOG.debug("reader preferences changed");
        this.onReaderPreferencesChanged(preferences.readerPreferences());
      }
    }
  }

  private void onReaderPreferencesChanged(final ReaderPreferences reader_preferences) {

    final ReaderReadiumJavaScriptAPIType js = NullCheck.notNull(this.readium_js_api);
    js.setPageStyleSettings(reader_preferences);
    this.applyViewerColorScheme(reader_preferences.colorScheme());

    UIThread.runOnUIThreadDelayed(() -> {
      final ReaderReadiumJavaScriptAPIType readium_js =
          NullCheck.notNull(ReaderActivity.this.readium_js_api);
      readium_js.mediaOverlayIsAvailable(ReaderActivity.this);
    }, 300L);
  }

  private void failWithErrorMessage(
      final Resources resources,
      final Exception e) {

    LOG.error("failWithErrorMessage: ", e);

    final String message;
    if (e instanceof BookDatabaseException) {
      message = resources.getString(R.string.reader_error_book_database);
    } else if (e instanceof ReaderMissingEPUBException) {
      message = resources.getString(R.string.reader_error_epub_missing);
    } else {
      message = resources.getString(R.string.reader_error_general);
    }

    ErrorDialogUtilities.showErrorWithRunnable(this, LOG, message, e, ReaderActivity.this::finish);
  }


  @Override
  public void onCurrentPageError(final Throwable x) {
    LOG.error("{}", x.getMessage(), x);
  }

  @Override
  public void onCurrentPageReceived(final ReaderBookLocation new_location) {
    this.current_location = new_location;

    LOG.debug("received book location: {}", new_location);
    try {
      Simplified.getProfilesController().profileBookmarkSet(this.book_id, new_location);
    } catch (final ProfileNoneCurrentException e) {
      LOG.error("profile is not current: ", e);
    }
  }

  @Override
  protected void onPause() {
    super.onPause();

    if (this.book_id != null && this.current_location != null) {
      try {
        Simplified.getProfilesController().profileBookmarkSet(this.book_id, this.current_location);
      } catch (final ProfileNoneCurrentException e) {
        LOG.error("profile is not current: ", e);
      }
    }
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();

    final ReaderReadiumJavaScriptAPIType readium_js =
        NullCheck.notNull(ReaderActivity.this.readium_js_api);
    readium_js.getCurrentPage(this);
    readium_js.mediaOverlayIsAvailable(this);

    if (this.settings_subscription != null) {
      this.settings_subscription.unsubscribe();
    }
  }

  @Override
  public void onEPUBLoadFailed(final Throwable ex) {
    ErrorDialogUtilities.showErrorWithRunnable(
        this,
        LOG,
        this.getResources().getString(R.string.reader_error_epub_error),
        ex,
        ReaderActivity.this::finish);
  }

  @Override
  public void onEPUBLoadSucceeded(final Container c) {
    this.epub_container = c;
    final Package p = NullCheck.notNull(c.getDefaultPackage());

    final TextView in_title_text = NullCheck.notNull(this.view_title_text);
    UIThread.runOnUIThread(() -> in_title_text.setText(NullCheck.notNull(p.getTitle())));

    /*
     * Configure the TOC button.
     */

    final View in_toc = NullCheck.notNull(this.view_toc);
    in_toc.setOnClickListener(view -> {
      final ReaderTOC sent_toc = ReaderTOC.fromPackage(p);
      ReaderTOCActivity.startActivityForResult(this, this.account, sent_toc);
    });

    /*
     * Get a reference to the web server. Start it if necessary (the callbacks
     * will still be executed if the server is already running).
     */

    final ReaderHTTPServerType hs = Simplified.getReaderHTTPServer();
    hs.startIfNecessaryForPackage(p, this);
  }

  @Override
  public void onMediaOverlayIsAvailable(final boolean available) {
    LOG.debug("media overlay status changed: available: {}", available);

    final ViewGroup in_media_hud = NullCheck.notNull(this.view_media);
    final TextView in_title = NullCheck.notNull(this.view_title_text);
    UIThread.runOnUIThread(() -> {
      in_media_hud.setVisibility(available ? View.VISIBLE : View.GONE);
      in_title.setVisibility(available ? View.GONE : View.VISIBLE);
    });
  }

  @Override
  public void onMediaOverlayIsAvailableError(final Throwable x) {
    LOG.error("{}", x.getMessage(), x);
  }

  @Override
  public void onReadiumFunctionDispatchError(final Throwable x) {
    LOG.error("{}", x.getMessage(), x);
  }

  @Override
  public void onReadiumFunctionInitialize() {
    LOG.debug("readium initialize requested");

    final ReaderHTTPServerType hs = Simplified.getReaderHTTPServer();
    final Container c = NullCheck.notNull(this.epub_container);
    final Package p = NullCheck.notNull(c.getDefaultPackage());
    p.setRootUrls(hs.getURIBase().toString(), null);

    final ReaderReadiumViewerSettings vs =
        NullCheck.notNull(this.viewer_settings);
    final ReaderReadiumJavaScriptAPIType js =
        NullCheck.notNull(this.readium_js_api);

    /*
     * If there's a bookmark for the current book, send a request to open the
     * book to that specific page. Otherwise, start at the beginning.
     */

    final OptionType<ReaderBookLocation> mark;
    try {
      mark = Simplified.getProfilesController().profileBookmarkGet(this.book_id);
    } catch (final ProfileNoneCurrentException e) {
      throw new IllegalArgumentException(e);
    }

    final OptionType<ReaderOpenPageRequestType> page_request = mark.map(location -> {
      LOG.debug("restoring bookmark {}", location);
      this.current_location = location;
      return ReaderOpenPageRequest.fromBookLocation(location);
    });

    // is this correct? inject fonts before book opens or after
    js.injectFonts();

    // open book with page request, vs = view settings, p = package , what is package actually ? page_request = idref + contentcfi
    js.openBook(p, vs, page_request);

    /*
     * Configure the visibility of UI elements.
     */

    final WebView in_web_view =
        NullCheck.notNull(this.view_web_view);
    final ProgressBar in_loading =
        NullCheck.notNull(this.view_loading);
    final ProgressBar in_progress_bar =
        NullCheck.notNull(this.view_progress_bar);
    final TextView in_progress_text =
        NullCheck.notNull(this.view_progress_text);
    final ImageView in_media_play =
        NullCheck.notNull(this.view_media_play);
    final ImageView in_media_next =
        NullCheck.notNull(this.view_media_next);
    final ImageView in_media_prev =
        NullCheck.notNull(this.view_media_prev);

    in_loading.setVisibility(View.GONE);
    in_web_view.setVisibility(View.VISIBLE);
    in_progress_bar.setVisibility(View.VISIBLE);
    in_progress_text.setVisibility(View.INVISIBLE);

    this.onReaderPreferencesChanged(this.profile.preferences().readerPreferences());

    UIThread.runOnUIThread(() -> {
      in_media_play.setOnClickListener(view -> {
        LOG.debug("toggling media overlay");
        js.mediaOverlayToggle();
      });

      in_media_next.setOnClickListener(view -> {
        LOG.debug("next media overlay");
        js.mediaOverlayNext();
      });

      in_media_prev.setOnClickListener(view -> {
        LOG.debug("previous media overlay");
        js.mediaOverlayPrevious();
      });
    });
  }

  @Override
  public void onReadiumFunctionInitializeError(final Throwable e) {
    ErrorDialogUtilities.showErrorWithRunnable(
        this,
        LOG,
        "Unable to initialize Readium",
        e,
        ReaderActivity.this::finish);
  }

  /**
   * {@inheritDoc}
   * <p>
   * When the device orientation changes, the configuration change handler
   * {@link #onConfigurationChanged(Configuration)} makes the web view invisible
   * so that the user does not see the now incorrectly-paginated content. When
   * Readium tells the app that the content pagination has changed, it makes the
   * web view visible again.
   */

  @Override
  public void onReadiumFunctionPaginationChanged(final ReaderPaginationChangedEvent e) {
    LOG.debug("pagination changed: {}", e);
    final WebView in_web_view = NullCheck.notNull(this.view_web_view);
    
    /*
     * Configure the progress bar and text.
     */

    final TextView in_progress_text =
        NullCheck.notNull(this.view_progress_text);
    final ProgressBar in_progress_bar =
        NullCheck.notNull(this.view_progress_bar);

    final Container container = NullCheck.notNull(this.epub_container);
    final Package default_package = NullCheck.notNull(container.getDefaultPackage());

    final List<OpenPage> pages = e.getOpenPages();
    if (!pages.isEmpty()) {
      final OpenPage page = NullCheck.notNull(pages.get(0));
      String message = "book_open_page," + (page.getSpineItemPageIndex() + 1) + "/" + page.getSpineItemPageCount();
      Simplified.getAnalyticsController().logToAnalytics(message);
    }

    UIThread.runOnUIThread(() -> {
      final double p = e.getProgressFractional();
      in_progress_bar.setMax(100);
      in_progress_bar.setProgress((int) (100.0 * p));

      if (pages.isEmpty()) {
        in_progress_text.setText("");
      } else {
        final OpenPage page = NullCheck.notNull(pages.get(0));
        in_progress_text.setText(
            NullCheck.notNull(
                String.format(
                    "Page %d of %d (%s)",
                    page.getSpineItemPageIndex() + 1,
                    page.getSpineItemPageCount(),
                    default_package.getSpineItem(page.getIDRef()).getTitle())));
      }

      /*
       * Ask for Readium to deliver the unique identifier of the current page,
       * and tell Simplified that the page has changed and so any Javascript
       * state should be reconfigured.
       */

      UIThread.runOnUIThreadDelayed(() -> {
        final ReaderReadiumJavaScriptAPIType readium_js = NullCheck.notNull(this.readium_js_api);
        readium_js.getCurrentPage(this);
        readium_js.mediaOverlayIsAvailable(this);
      }, 300L);
    });

    final ReaderSimplifiedJavaScriptAPIType simplified_js =
        NullCheck.notNull(this.simplified_js_api);

    /*
     * Make the web view visible with a slight delay (as sometimes a
     * pagination-change event will be sent even though the content has not
     * yet been laid out in the web view). Only do this if the screen
     * orientation has just changed.
     */

    if (this.web_view_resized) {
      this.web_view_resized = false;
      UIThread.runOnUIThreadDelayed(() -> {
        in_web_view.setVisibility(View.VISIBLE);
        in_progress_bar.setVisibility(View.VISIBLE);
        in_progress_text.setVisibility(View.VISIBLE);
        simplified_js.pageHasChanged();
      }, 200L);
    } else {
      UIThread.runOnUIThread(simplified_js::pageHasChanged);
    }
  }

  @Override
  public void onReadiumFunctionPaginationChangedError(final Throwable x) {
    LOG.error("{}", x.getMessage(), x);
  }

  @Override
  public void onReadiumFunctionSettingsApplied() {
    LOG.debug("received settings applied");
  }

  @Override
  public void onReadiumFunctionSettingsAppliedError(final Throwable e) {
    LOG.error("{}", e.getMessage(), e);
  }

  @Override
  public void onReadiumFunctionUnknown(final String text) {
    LOG.error("unknown readium function: {}", text);
  }

  @Override
  public void onReadiumMediaOverlayStatusChangedIsPlaying(final boolean playing) {
    LOG.debug("media overlay status changed: playing: {}", playing);

    final Resources rr = NullCheck.notNull(this.getResources());
    final ImageView play = NullCheck.notNull(this.view_media_play);

    UIThread.runOnUIThread(() -> {
      if (playing) {
        play.setImageDrawable(rr.getDrawable(R.drawable.circle_pause_8x));
      } else {
        play.setImageDrawable(rr.getDrawable(R.drawable.circle_play_8x));
      }
    });
  }

  @Override
  public void onReadiumMediaOverlayStatusError(final Throwable e) {
    LOG.error("{}", e.getMessage(), e);
  }

  @Override
  public void onServerStartFailed(
      final ReaderHTTPServerType hs,
      final Throwable x) {

    ErrorDialogUtilities.showErrorWithRunnable(
        this,
        LOG,
        "Could not start http server.",
        x,
        ReaderActivity.this::finish);
  }

  @Override
  public void onServerStartSucceeded(
      final ReaderHTTPServerType hs,
      final boolean first) {
    if (first) {
      LOG.debug("http server started");
    } else {
      LOG.debug("http server already running");
    }

    this.makeInitialReadiumRequest(hs);
  }

  @Override
  public void onSimplifiedFunctionDispatchError(final Throwable x) {
    LOG.error("{}", x.getMessage(), x);
  }

  @Override
  public void onSimplifiedFunctionUnknown(final String text) {
    LOG.error("unknown function: {}", text);
  }

  @Override
  public void onSimplifiedGestureCenter() {
    final ViewGroup in_hud = NullCheck.notNull(this.view_hud);
    UIThread.runOnUIThread(() -> {
      switch (in_hud.getVisibility()) {
        case View.VISIBLE: {
          FadeUtilities.fadeOut(in_hud, FadeUtilities.DEFAULT_FADE_DURATION);
          break;
        }
        case View.INVISIBLE:
        case View.GONE: {
          FadeUtilities.fadeIn(in_hud, FadeUtilities.DEFAULT_FADE_DURATION);
          break;
        }
      }
    });
  }

  @Override
  public void onSimplifiedGestureCenterError(final Throwable x) {
    LOG.error("{}", x.getMessage(), x);
  }

  @Override
  public void onSimplifiedGestureLeft() {
    final ReaderReadiumJavaScriptAPIType js = NullCheck.notNull(this.readium_js_api);
    js.pagePrevious();
  }

  @Override
  public void onSimplifiedGestureLeftError(final Throwable x) {
    LOG.error("{}", x.getMessage(), x);
  }

  @Override
  public void onSimplifiedGestureRight() {
    final ReaderReadiumJavaScriptAPIType js = NullCheck.notNull(this.readium_js_api);
    js.pageNext();
  }

  @Override
  public void onSimplifiedGestureRightError(final Throwable x) {
    LOG.error("{}", x.getMessage(), x);
  }

  @Override
  public void onTOCSelectionReceived(final TOCElement e) {
    LOG.debug("received TOC selection: {}", e);

    final ReaderReadiumJavaScriptAPIType js = NullCheck.notNull(this.readium_js_api);
    js.openContentURL(e.getContentRef(), e.getSourceHref());
  }

  private static final class ReaderMissingEPUBException extends Exception {

  }
}
