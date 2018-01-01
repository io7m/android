package org.nypl.simplified.app;

import android.app.Application;
import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.util.DisplayMetrics;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.io7m.jfunctional.FunctionType;
import com.io7m.jfunctional.OptionType;
import com.io7m.jfunctional.Some;
import com.io7m.jfunctional.Unit;
import com.io7m.jnull.NullCheck;
import com.io7m.jnull.Nullable;

import org.nypl.drm.core.AdobeAdeptExecutorType;
import org.nypl.simplified.app.catalog.CatalogBookCoverGenerator;
import org.nypl.simplified.app.catalog.CatalogBookCoverGeneratorType;
import org.nypl.simplified.app.reader.ReaderHTTPMimeMap;
import org.nypl.simplified.app.reader.ReaderHTTPMimeMapType;
import org.nypl.simplified.app.reader.ReaderHTTPServerAAsync;
import org.nypl.simplified.app.reader.ReaderHTTPServerType;
import org.nypl.simplified.app.reader.ReaderReadiumEPUBLoader;
import org.nypl.simplified.app.reader.ReaderReadiumEPUBLoaderType;
import org.nypl.simplified.assertions.Assertions;
import org.nypl.simplified.books.accounts.AccountProviderCollection;
import org.nypl.simplified.books.accounts.AccountProvidersJSON;
import org.nypl.simplified.books.accounts.AccountsDatabases;
import org.nypl.simplified.books.book_registry.BookRegistry;
import org.nypl.simplified.books.book_registry.BookRegistryReadableType;
import org.nypl.simplified.books.book_registry.BookRegistryType;
import org.nypl.simplified.books.controller.Controller;
import org.nypl.simplified.books.controller.BooksControllerType;
import org.nypl.simplified.books.controller.ProfilesControllerType;
import org.nypl.simplified.books.core.AuthenticationDocumentValuesType;
import org.nypl.simplified.books.core.Clock;
import org.nypl.simplified.books.core.ClockType;
import org.nypl.simplified.books.core.DocumentStore;
import org.nypl.simplified.books.core.DocumentStoreBuilderType;
import org.nypl.simplified.books.core.DocumentStoreType;
import org.nypl.simplified.books.core.FeedHTTPTransport;
import org.nypl.simplified.books.core.FeedLoader;
import org.nypl.simplified.books.core.FeedLoaderType;
import org.nypl.simplified.books.core.LogUtilities;
import org.nypl.simplified.books.profiles.ProfileDatabaseException;
import org.nypl.simplified.books.profiles.ProfilesDatabase;
import org.nypl.simplified.books.profiles.ProfilesDatabaseType;
import org.nypl.simplified.bugsnag.IfBugsnag;
import org.nypl.simplified.cardcreator.CardCreator;
import org.nypl.simplified.downloader.core.DownloaderHTTP;
import org.nypl.simplified.downloader.core.DownloaderType;
import org.nypl.simplified.files.DirectoryUtilities;
import org.nypl.simplified.http.core.HTTP;
import org.nypl.simplified.http.core.HTTPAuthType;
import org.nypl.simplified.http.core.HTTPType;
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntryParser;
import org.nypl.simplified.opds.core.OPDSAuthenticationDocumentParser;
import org.nypl.simplified.opds.core.OPDSAuthenticationDocumentParserType;
import org.nypl.simplified.opds.core.OPDSFeedParser;
import org.nypl.simplified.opds.core.OPDSFeedParserType;
import org.nypl.simplified.opds.core.OPDSFeedTransportType;
import org.nypl.simplified.opds.core.OPDSSearchParser;
import org.nypl.simplified.opds.core.OPDSSearchParserType;
import org.nypl.simplified.tenprint.TenPrintGenerator;
import org.nypl.simplified.tenprint.TenPrintGeneratorType;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Global application state.
 */

public final class Simplified extends Application {

  private static final Logger LOG;
  private static volatile Simplified INSTANCE;

  static {
    LOG = LogUtilities.getLog(Simplified.class);
  }

  private CardCreator cardcreator;
  private ExecutorService exec_catalog_feeds;
  private ExecutorService exec_covers;
  private ExecutorService exec_downloader;
  private ExecutorService exec_books;
  private ExecutorService exec_epub;
  private ScreenSizeInformation screen;
  private File directory_base;
  private File directory_documents;
  private File directory_downloads;
  private File directory_profiles;
  private OptionType<AdobeAdeptExecutorType> adobe_drm;
  private CatalogBookCoverGenerator cover_generator;
  private HTTPType http;
  private DownloaderType downloader;
  private ReaderReadiumEPUBLoaderType epub_loader;
  private ReaderHTTPMimeMapType mime;
  private ReaderHTTPServerType httpd;
  private BookCoverProviderType cover_provider;
  private OptionType<HelpstackType> helpstack;
  private ClockType clock;
  private DocumentStoreType documents;
  private OPDSFeedParserType feed_parser;
  private OPDSSearchParserType feed_search_parser;
  private OPDSFeedTransportType<OptionType<HTTPAuthType>> feed_transport;
  private FeedLoaderType feed_loader;
  private ProfilesDatabaseType profiles;
  private AccountProviderCollection account_providers;
  private NetworkConnectivity network_connectivity;
  private BookRegistryType book_registry;
  private Controller book_controller;
  private ListeningExecutorService exec_background;


  /**
   * Construct the application.
   */

  public Simplified() {

  }

  private static Simplified checkInitialized() {
    final Simplified i = Simplified.INSTANCE;
    if (i == null) {
      throw new IllegalStateException("Application is not yet initialized");
    }
    return i;
  }

  /**
   * @return The Card Creator
   */

  public static CardCreator getCardCreator() {
    final Simplified i = Simplified.checkInitialized();
    return i.cardcreator;
  }

  /**
   * @return The account providers
   */

  public static AccountProviderCollection getAccountProviders() {
    final Simplified i = Simplified.checkInitialized();
    return i.account_providers;
  }

  /**
   * @return The network connectivity interface
   */

  public static NetworkConnectivityType getNetworkConnectivity() {
    final Simplified i = Simplified.checkInitialized();
    return i.network_connectivity;
  }

  /**
   * @return The screen size controller interface
   */

  public static ScreenSizeInformationType getScreenSizeInformation() {
    final Simplified i = Simplified.checkInitialized();
    return i.screen;
  }

  /**
   * @return The book cover provider
   */

  public static BookCoverProviderType getCoverProvider() {
    final Simplified i = Simplified.checkInitialized();
    return i.cover_provider;
  }

  /**
   * @return The profiles controller
   */

  public static ProfilesControllerType getProfilesController() {
    final Simplified i = Simplified.checkInitialized();
    return i.book_controller;
  }

  /**
   * @return The books controller
   */

  public static BooksControllerType getBooksController() {
    final Simplified i = Simplified.checkInitialized();
    return i.book_controller;
  }

  /**
   * @return A general executor service for background tasks
   */

  public static ListeningExecutorService getBackgroundTaskExecutor() {
    final Simplified i = Simplified.checkInitialized();
    return i.exec_background;
  }

  /**
   * @return The books registry
   */

  public static BookRegistryReadableType getBooksRegistry() {
    final Simplified i = Simplified.checkInitialized();
    return i.book_registry;
  }

  /**
   * @return The feed loader
   */

  public static FeedLoaderType getFeedLoader() {
    final Simplified i = Simplified.checkInitialized();
    return i.feed_loader;
  }

  /**
   * @return The Helpstack interface, if one is available
   */

  public static OptionType<HelpstackType> getHelpStack() {
    final Simplified i = Simplified.checkInitialized();
    return i.helpstack;
  }

  @NonNull
  private static File determineDiskDataDirectory(
      final Context context) {

    /*
     * If external storage is mounted and is on a device that doesn't allow
     * the storage to be removed, use the external storage for data.
     */

    if (Environment.MEDIA_MOUNTED.equals(
        Environment.getExternalStorageState())) {

      LOG.debug("trying external storage");
      if (!Environment.isExternalStorageRemovable()) {
        final File r = context.getExternalFilesDir(null);
        LOG.debug("external storage is not removable, using it ({})", r);
        Assertions.checkPrecondition(r.isDirectory(), "Data directory {} is a directory", r);
        return NullCheck.notNull(r);
      }
    }

    /*
     * Otherwise, use internal storage.
     */

    final File r = context.getFilesDir();
    LOG.debug("no non-removable external storage, using internal storage ({})", r);
    Assertions.checkPrecondition(r.isDirectory(), "Data directory {} is a directory", r);
    return NullCheck.notNull(r);
  }

  private static ExecutorService createNamedThreadPool(
      final int count,
      final String base,
      final int priority) {

    LOG.debug("creating named thread pool: {} ({} threads at priority {})", base, count, priority);

    final ThreadFactory tf = Executors.defaultThreadFactory();

    final ThreadFactory named = new ThreadFactory() {
      private int id;

      @Override
      public Thread newThread(final @Nullable Runnable r) {

        /*
         * Apparently, it's necessary to use {@link android.os.Process} to set
         * the thread priority, rather than the standard Java thread
         * functions.
         */

        final Thread t = tf.newThread(
            new Runnable() {
              @Override
              public void run() {
                android.os.Process.setThreadPriority(priority);
                NullCheck.notNull(r).run();
              }
            });
        t.setName(String.format("simplified-%s-tasks-%d", base, this.id));
        ++this.id;
        return t;
      }
    };

    return NullCheck.notNull(Executors.newFixedThreadPool(count, named));
  }

  public static int getCurrentTheme() {
    final Simplified i = Simplified.checkInitialized();
    return i.currentTheme();
  }

  public static DocumentStoreType getDocumentStore() {
    final Simplified i = Simplified.checkInitialized();
    return i.documents;
  }

  private static int fetchUnusedHTTPPort() {
    // Fallback port
    Integer port = 8080;
    try {
      final ServerSocket s = new ServerSocket(0);
      port = s.getLocalPort();
      s.close();
    } catch (final IOException e) {
      // Ignore
    }

    LOG.debug("HTTP server will run on port {}", port);
    return port;
  }

  private static AccountProviderCollection createAccountProviders(
      final AssetManager asset_manager)
      throws IOException {

    try (InputStream stream = asset_manager.open("account_providers.json")) {
      return AccountProvidersJSON.deserializeFromStream(stream);
    }
  }

  private static ProfilesDatabaseType createProfileDatabase(
      final Resources resources,
      final AccountProviderCollection account_providers,
      final File directory)
      throws ProfileDatabaseException {

    /*
     * If profiles are enabled, then disable the anonymous profile.
     */

    final boolean anonymous = !resources.getBoolean(R.bool.feature_profiles_enabled);

    if (anonymous) {
      LOG.debug("opening profile database with anonymous profile");
      return ProfilesDatabase.openWithAnonymousAccountEnabled(
          AccountsDatabases.get(),
          account_providers.providerDefault(),
          directory);
    }

    LOG.debug("opening profile database without anonymous profile");
    return ProfilesDatabase.openWithAnonymousAccountDisabled(AccountsDatabases.get(), directory);
  }

  private static BookCoverProviderType createCoverProvider(
      final Context in_context,
      final CatalogBookCoverGeneratorType in_cover_generator,
      final BookRegistryReadableType in_book_registry,
      final ExecutorService in_exec_covers) {

    return BookCoverProvider.newCoverProvider(
        in_context,
        in_book_registry,
        in_cover_generator,
        in_exec_covers);
  }

  @NonNull
  private static OPDSFeedParserType createFeedParser() {
    return OPDSFeedParser.newParser(OPDSAcquisitionFeedEntryParser.newParser());
  }

  /**
   * Create a document store and conditionally enable each of the documents based on the
   * presence of assets.
   */

  private static DocumentStoreType createDocumentStore(
      final AssetManager assets,
      final Resources resources,
      final ClockType clock,
      final HTTPType http,
      final ExecutorService exec,
      final File directory) {

    final OPDSAuthenticationDocumentParserType auth_doc_parser =
        OPDSAuthenticationDocumentParser.get();

    /*
     * Default authentication document values.
     */

    final AuthenticationDocumentValuesType auth_doc_values =
        new AuthenticationDocumentValuesType() {
          @Override
          public String getLabelLoginUserID() {
            return resources.getString(R.string.settings_barcode);
          }

          @Override
          public String getLabelLoginPassword() {
            return resources.getString(R.string.settings_pin);
          }

          @Override
          public String getLabelLoginPatronName() {
            return resources.getString(R.string.settings_name);
          }
        };

    final DocumentStoreBuilderType documents_builder =
        DocumentStore.newBuilder(clock, http, exec, directory, auth_doc_values, auth_doc_parser);

    try {
      final InputStream stream = assets.open("eula.html");
      documents_builder.enableEULA(
          new FunctionType<Unit, InputStream>() {
            @Override
            public InputStream call(final Unit x) {
              return stream;
            }
          });
    } catch (final IOException e) {
      LOG.debug("No EULA defined: ", e);
    }

    try {
      final InputStream stream = assets.open("software-licenses.html");
      documents_builder.enableLicenses(
          new FunctionType<Unit, InputStream>() {
            @Override
            public InputStream call(final Unit x) {
              return stream;
            }
          });
    } catch (final IOException e) {
      LOG.debug("No licenses defined: ", e);
    }

    return documents_builder.build();
  }

  private int currentTheme() {
    return R.style.SimplifiedTheme;
  }

  private void initBugsnag(
      final OptionType<String> api_token_opt) {
    if (api_token_opt.isSome()) {
      final String api_token = ((Some<String>) api_token_opt).get();
      LOG.debug("IfBugsnag: init live interface");
      IfBugsnag.init(this, api_token);
    } else {
      LOG.debug("IfBugsnag: init no-op interface");
      IfBugsnag.init();
    }
  }

  @Override
  public void onCreate() {
    super.onCreate();

    LOG.debug("starting app: pid {}", android.os.Process.myPid());
    final Resources resources = this.getResources();
    final AssetManager asset_manager = this.getAssets();

    LOG.debug("creating thread pools");
    this.exec_catalog_feeds = Simplified.createNamedThreadPool(1, "catalog-feed", 19);
    this.exec_covers = Simplified.createNamedThreadPool(2, "cover", 19);
    this.exec_downloader = Simplified.createNamedThreadPool(4, "downloader", 19);
    this.exec_books = Simplified.createNamedThreadPool(1, "books", 19);
    this.exec_epub = Simplified.createNamedThreadPool(1, "epub", 19);
    this.exec_background =
        MoreExecutors.listeningDecorator(
            Simplified.createNamedThreadPool(1, "background", 19));

    LOG.debug("initializing Bugsnag");
    this.initBugsnag(Bugsnag.getApiToken(asset_manager));

    LOG.debug("initializing DRM (if required)");
    this.adobe_drm =
        AdobeDRMServices.newAdobeDRMOptional(this, AdobeDRMServices.getPackageOverride(resources));

    this.screen = new ScreenSizeInformation(LOG, resources);

    LOG.debug("initializing directories");
    this.directory_base = determineDiskDataDirectory(this);
    this.directory_downloads = new File(this.directory_base, "downloads");
    this.directory_documents = new File(this.directory_base, "documents");
    this.directory_profiles = new File(this.directory_base, "profiles");

    LOG.debug("directory_base:      {}", this.directory_base);
    LOG.debug("directory_downloads: {}", this.directory_downloads);
    LOG.debug("directory_documents: {}", this.directory_documents);
    LOG.debug("directory_profiles:  {}", this.directory_profiles);

    /*
     * Make sure the required directories exist. There is no sane way to
     * recover if they cannot be created!
     */

    try {
      DirectoryUtilities.directoryCreate(this.directory_base);
      DirectoryUtilities.directoryCreate(this.directory_downloads);
      DirectoryUtilities.directoryCreate(this.directory_documents);
      DirectoryUtilities.directoryCreate(this.directory_profiles);
    } catch (final IOException e) {
      LOG.error("could not create directories: {}", e.getMessage(), e);
      throw new IllegalStateException(e);
    }

    LOG.debug("initializing downloader");
    this.http = HTTP.newHTTP();
    this.downloader = DownloaderHTTP.newDownloader(
        this.exec_books, this.directory_downloads, this.http);

    LOG.debug("initializing book registry");
    this.book_registry = BookRegistry.create();

    LOG.debug("initializing cover generator");
    final TenPrintGeneratorType ten_print = TenPrintGenerator.newGenerator();
    this.cover_generator = new CatalogBookCoverGenerator(ten_print);
    this.cover_provider = createCoverProvider(
        this, this.cover_generator, this.book_registry, this.exec_covers);

    LOG.debug("initializing EPUB loader and HTTP server");
    this.mime = ReaderHTTPMimeMap.newMap("application/octet-stream");
    this.httpd = ReaderHTTPServerAAsync.newServer(asset_manager, this.mime, fetchUnusedHTTPPort());
    this.epub_loader = ReaderReadiumEPUBLoader.newLoader(this, this.exec_epub);
    this.clock = Clock.get();

    LOG.debug("initializing document store");
    this.documents = createDocumentStore(
        asset_manager,
        resources,
        this.clock,
        this.http,
        this.exec_downloader,
        this.directory_documents);

    try {
      LOG.debug("initializing account providers");
      this.account_providers = createAccountProviders(asset_manager);
      for (final URI id : this.account_providers.providers().keySet()) {
        LOG.debug("loaded account provider: {}", id);
      }
    } catch (final IOException e) {
      throw new IllegalStateException("Could not initialize account providers", e);
    }

    try {
      LOG.debug("initializing profiles and accounts");
      this.profiles = createProfileDatabase(
          resources, this.account_providers, this.directory_profiles);
    } catch (final ProfileDatabaseException e) {
      throw new IllegalStateException("Could not initialize profile database", e);
    }

    LOG.debug("initializing book controller");
    this.book_controller = Controller.createBookController(
        this.exec_books,
        this.http,
        this.profiles,
        this.book_registry,
        ignored -> this.account_providers);

    LOG.debug("initializing feed loader");
    this.feed_parser = createFeedParser();
    this.feed_search_parser = OPDSSearchParser.newParser();
    this.feed_transport = FeedHTTPTransport.newTransport(this.http);
    this.feed_loader = FeedLoader.newFeedLoader(
        this.exec_catalog_feeds,
        this.book_registry,
        this.feed_parser,
        this.feed_transport,
        this.feed_search_parser);

    LOG.debug("initializing network connectivity checker");
    this.network_connectivity = new NetworkConnectivity(this);

    LOG.debug("initializing CardCreator");
    this.cardcreator =
        new CardCreator(
            asset_manager,
            resources.getString(R.string.feature_environment),
            resources);

    LOG.debug("initializing HelpStack");
    this.helpstack = Helpstack.get(this, asset_manager);

    LOG.debug("finished booting");
    Simplified.INSTANCE = this;
  }

  private static final class NetworkConnectivity implements NetworkConnectivityType {

    private final Context context;

    NetworkConnectivity(
        final Context in_context) {
      this.context = NullCheck.notNull(in_context, "Context");
    }

    @Override
    public boolean isNetworkAvailable() {
      final NetworkInfo info =
          ((ConnectivityManager) this.context.getSystemService(
              Context.CONNECTIVITY_SERVICE)).getActiveNetworkInfo();

      if (info == null) {
        return false;
      }

      return info.isConnected();
    }
  }

  private static final class ScreenSizeInformation implements ScreenSizeInformationType {

    private final Resources resources;

    private ScreenSizeInformation(
        final Logger log,
        final Resources rr) {
      this.resources = NullCheck.notNull(rr);

      final DisplayMetrics dm = this.resources.getDisplayMetrics();
      final float dp_height = (float) dm.heightPixels / dm.density;
      final float dp_width = (float) dm.widthPixels / dm.density;
      log.debug("screen ({} x {})", dp_width, dp_height);
      log.debug("screen ({} x {})", dm.widthPixels, dm.heightPixels);
    }

    @Override
    public double screenDPToPixels(
        final int dp) {
      final float scale = this.resources.getDisplayMetrics().density;
      return ((double) (dp * scale) + 0.5);
    }

    @Override
    public double screenGetDPI() {
      final DisplayMetrics metrics = this.resources.getDisplayMetrics();
      return (double) metrics.densityDpi;
    }

    @Override
    public int screenGetHeightPixels() {
      final Resources rr = NullCheck.notNull(this.resources);
      final DisplayMetrics dm = rr.getDisplayMetrics();
      return dm.heightPixels;
    }

    @Override
    public int screenGetWidthPixels() {
      final Resources rr = NullCheck.notNull(this.resources);
      final DisplayMetrics dm = rr.getDisplayMetrics();
      return dm.widthPixels;
    }
  }
}
