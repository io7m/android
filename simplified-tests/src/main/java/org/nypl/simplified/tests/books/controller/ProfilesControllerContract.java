package org.nypl.simplified.tests.books.controller;

import com.io7m.jfunctional.FunctionType;
import com.io7m.jfunctional.Option;
import com.io7m.jfunctional.OptionType;
import com.io7m.jfunctional.Unit;

import org.hamcrest.core.IsInstanceOf;
import org.joda.time.LocalDate;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.nypl.simplified.books.accounts.AccountAuthenticationCredentials;
import org.nypl.simplified.books.accounts.AccountBarcode;
import org.nypl.simplified.books.accounts.AccountEvent;
import org.nypl.simplified.books.accounts.AccountEventCreation.AccountCreationSucceeded;
import org.nypl.simplified.books.accounts.AccountEventLogin.AccountLoginFailed;
import org.nypl.simplified.books.accounts.AccountEventLogin.AccountLoginSucceeded;
import org.nypl.simplified.books.accounts.AccountPIN;
import org.nypl.simplified.books.accounts.AccountProvider;
import org.nypl.simplified.books.accounts.AccountProviderAuthenticationDescription;
import org.nypl.simplified.books.accounts.AccountProviderCollection;
import org.nypl.simplified.books.accounts.AccountsDatabases;
import org.nypl.simplified.books.book_database.BookID;
import org.nypl.simplified.books.book_registry.BookRegistry;
import org.nypl.simplified.books.book_registry.BookRegistryType;
import org.nypl.simplified.books.controller.Controller;
import org.nypl.simplified.books.controller.ProfilesControllerType;
import org.nypl.simplified.books.feeds.FeedHTTPTransport;
import org.nypl.simplified.books.feeds.FeedLoader;
import org.nypl.simplified.books.feeds.FeedLoaderType;
import org.nypl.simplified.books.profiles.ProfileCreationEvent.ProfileCreationFailed;
import org.nypl.simplified.books.profiles.ProfileCreationEvent.ProfileCreationSucceeded;
import org.nypl.simplified.books.profiles.ProfileDatabaseException;
import org.nypl.simplified.books.profiles.ProfileEvent;
import org.nypl.simplified.books.profiles.ProfileNoneCurrentException;
import org.nypl.simplified.books.profiles.ProfilePreferencesChanged;
import org.nypl.simplified.books.profiles.ProfileReadableType;
import org.nypl.simplified.books.profiles.ProfileSelected;
import org.nypl.simplified.books.profiles.ProfilesDatabase;
import org.nypl.simplified.books.profiles.ProfilesDatabaseType;
import org.nypl.simplified.books.reader.ReaderBookLocation;
import org.nypl.simplified.downloader.core.DownloaderHTTP;
import org.nypl.simplified.downloader.core.DownloaderType;
import org.nypl.simplified.files.DirectoryUtilities;
import org.nypl.simplified.http.core.HTTPAuthType;
import org.nypl.simplified.http.core.HTTPResultError;
import org.nypl.simplified.http.core.HTTPResultOK;
import org.nypl.simplified.http.core.HTTPType;
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntryParser;
import org.nypl.simplified.opds.core.OPDSFeedParser;
import org.nypl.simplified.opds.core.OPDSFeedParserType;
import org.nypl.simplified.opds.core.OPDSFeedTransportType;
import org.nypl.simplified.opds.core.OPDSSearchParser;
import org.nypl.simplified.tests.http.MockingHTTP;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.nypl.simplified.books.profiles.ProfileCreationEvent.ProfileCreationFailed.ErrorCode.ERROR_DISPLAY_NAME_ALREADY_USED;

public abstract class ProfilesControllerContract {

  @Rule public ExpectedException expected = ExpectedException.none();

  private ExecutorService executor_downloads;
  private ExecutorService executor_books;
  private File directory_downloads;
  private File directory_profiles;
  private MockingHTTP http;
  private List<ProfileEvent> profile_events;
  private List<AccountEvent> account_events;
  private DownloaderType downloader;
  private BookRegistryType book_registry;

  private static AccountProvider fakeProvider(final String provider_id) {
    return AccountProvider.builder()
        .setId(URI.create(provider_id))
        .setDisplayName("Fake Library")
        .setSubtitle("Imaginary books")
        .setLogo(URI.create("http://example.com/logo.png"))
        .setCatalogURI(URI.create("http://example.com/accounts0/feed.xml"))
        .setSupportEmail("postmaster@example.com")
        .build();
  }

  private ProfilesControllerType controller(
      final ExecutorService exec,
      final HTTPType http,
      final BookRegistryType books,
      final ProfilesDatabaseType profiles,
      final DownloaderType downloader,
      final FunctionType<Unit, AccountProviderCollection> account_providers) {

    final OPDSFeedParserType parser =
        OPDSFeedParser.newParser(OPDSAcquisitionFeedEntryParser.newParser());
    final OPDSFeedTransportType<OptionType<HTTPAuthType>> transport =
        FeedHTTPTransport.newTransport(http);
    final FeedLoaderType feed_loader =
        FeedLoader.newFeedLoader(exec, books, parser, transport, OPDSSearchParser.newParser());

    return Controller.createBookController(
        exec,
        http,
        parser,
        feed_loader,
        downloader,
        profiles,
        books,
        account_providers);
  }

  @Before
  public void setUp() throws Exception {
    this.http = new MockingHTTP();
    this.executor_downloads = Executors.newCachedThreadPool();
    this.executor_books = Executors.newCachedThreadPool();
    this.directory_downloads = DirectoryUtilities.directoryCreateTemporary();
    this.directory_profiles = DirectoryUtilities.directoryCreateTemporary();
    this.profile_events = Collections.synchronizedList(new ArrayList<ProfileEvent>());
    this.account_events = Collections.synchronizedList(new ArrayList<AccountEvent>());
    this.book_registry = BookRegistry.create();
    this.downloader = DownloaderHTTP.newDownloader(this.executor_downloads, this.directory_downloads, this.http);
  }

  @After
  public void tearDown() throws Exception {
    this.executor_books.shutdown();
    this.executor_downloads.shutdown();
  }

  /**
   * Trying to fetch the current profile without selecting one should fail.
   *
   * @throws Exception On errors
   */

  @Test(timeout = 3_000L)
  public final void testProfilesCurrentNoneCurrent() throws Exception {

    final ProfilesDatabaseType profiles =
        profilesDatabaseWithoutAnonymous(this.directory_profiles);
    final DownloaderType downloader =
        DownloaderHTTP.newDownloader(this.executor_downloads, this.directory_downloads, http);
    final ProfilesControllerType controller =
        controller(this.executor_books, http, this.book_registry, profiles, downloader, ProfilesControllerContract::accountProviders);

    this.expected.expect(ProfileNoneCurrentException.class);
    controller.profileCurrent();
  }

  /**
   * Selecting a profile works.
   *
   * @throws Exception On errors
   */

  @Test(timeout = 3_000L)
  public final void testProfilesCurrentSelectCurrent() throws Exception {

    final ProfilesDatabaseType profiles =
        profilesDatabaseWithoutAnonymous(this.directory_profiles);
    final DownloaderType downloader =
        DownloaderHTTP.newDownloader(this.executor_downloads, this.directory_downloads, http);
    final ProfilesControllerType controller =
        controller(this.executor_books, http, this.book_registry, profiles, downloader, ProfilesControllerContract::accountProviders);

    final LocalDate date = LocalDate.now();
    controller.profileCreate(
        accountProviders().providerDefault(), "Kermit", date).get();
    controller.profileSelect(
        profiles.profiles().firstKey()).get();

    final ProfileReadableType p = controller.profileCurrent();
    Assert.assertEquals("Kermit", p.displayName());
  }

  /**
   * Creating a profile with the same display name as an existing profile should fail.
   *
   * @throws Exception On errors
   */

  @Test(timeout = 3_000L)
  public final void testProfilesCreateDuplicate() throws Exception {

    final ProfilesDatabaseType profiles =
        profilesDatabaseWithoutAnonymous(this.directory_profiles);
    final ProfilesControllerType controller =
        controller(this.executor_books, http, this.book_registry, profiles, downloader, ProfilesControllerContract::accountProviders);

    controller.profileEvents().subscribe(this.profile_events::add);

    final LocalDate date = LocalDate.now();
    final AccountProvider provider = accountProviders().providerDefault();
    controller.profileCreate(provider, "Kermit", date).get();
    controller.profileCreate(provider, "Kermit", date).get();

    Assert.assertEquals(2L, this.profile_events.size());
    Assert.assertThat(
        this.profile_events.get(0),
        IsInstanceOf.instanceOf(ProfileCreationSucceeded.class));
    Assert.assertThat(
        this.profile_events.get(1),
        IsInstanceOf.instanceOf(ProfileCreationFailed.class));
    Assert.assertEquals(
        ERROR_DISPLAY_NAME_ALREADY_USED,
        ((ProfileCreationFailed) this.profile_events.get(1)).errorCode());
  }

  /**
   * Trying to log in to an account with the wrong credentials should fail.
   *
   * @throws Exception On errors
   */

  @Test(timeout = 3_000L)
  public final void testProfilesAccountLoginFailed() throws Exception {

    final ProfilesDatabaseType profiles =
        profilesDatabaseWithoutAnonymous(this.directory_profiles);
    final ProfilesControllerType controller =
        controller(this.executor_books, http, this.book_registry, profiles, downloader, ProfilesControllerContract::accountProviders);

    controller.profileEvents().subscribe(this.profile_events::add);
    controller.accountEvents().subscribe(this.account_events::add);

    final LocalDate date = LocalDate.now();
    final AccountProvider provider = fakeAuthProvider("urn:fake-auth:0");
    controller.profileCreate(provider, "Kermit", date).get();
    controller.profileSelect(profiles.profiles().firstKey()).get();
    controller.profileAccountCreate(provider.id()).get();

    this.http.addResponse(
        "urn:fake-auth:0",
        new HTTPResultError<>(
            401,
            "UNAUTHORIZED",
            0L,
            new HashMap<>(),
            0L,
            new ByteArrayInputStream(new byte[0]),
            Option.none()));

    final AccountAuthenticationCredentials credentials =
        AccountAuthenticationCredentials.builder(
            AccountPIN.create("abcd"), AccountBarcode.create("1234"))
            .build();

    controller.profileAccountLogin(
        profiles.currentProfileUnsafe().accounts().firstKey(), credentials).get();

    Assert.assertEquals(2L, this.profile_events.size());
    Assert.assertThat(
        this.profile_events.get(0),
        IsInstanceOf.instanceOf(ProfileCreationSucceeded.class));
    Assert.assertThat(
        this.profile_events.get(1),
        IsInstanceOf.instanceOf(ProfileSelected.class));

    Assert.assertEquals(2L, this.account_events.size());
    Assert.assertThat(
        this.account_events.get(0),
        IsInstanceOf.instanceOf(AccountCreationSucceeded.class));
    Assert.assertThat(
        this.account_events.get(1),
        IsInstanceOf.instanceOf(AccountLoginFailed.class));

    Assert.assertTrue(
        "Credentials must not be saved",
        controller.profileAccountCurrent().credentials().isNone());
  }

  /**
   * Trying to log in to an account with the right credentials should succeed.
   *
   * @throws Exception On errors
   */

  @Test(timeout = 3_000L)
  public final void testProfilesAccountLoginSucceeded() throws Exception {

    final ProfilesDatabaseType profiles =
        profilesDatabaseWithoutAnonymous(this.directory_profiles);
    final ProfilesControllerType controller =
        controller(this.executor_books, http, this.book_registry, profiles, downloader, ProfilesControllerContract::accountProviders);

    controller.profileEvents().subscribe(this.profile_events::add);
    controller.accountEvents().subscribe(this.account_events::add);

    final LocalDate date = LocalDate.now();
    final AccountProvider provider = fakeAuthProvider("urn:fake-auth:0");
    controller.profileCreate(provider, "Kermit", date).get();
    controller.profileSelect(profiles.profiles().firstKey()).get();
    controller.profileAccountCreate(provider.id()).get();

    this.http.addResponse(
        "urn:fake-auth:0",
        new HTTPResultOK<>(
            "OK",
            200,
            new ByteArrayInputStream(new byte[0]),
            0L,
            new HashMap<>(),
            0L));

    final AccountAuthenticationCredentials credentials =
        AccountAuthenticationCredentials.builder(
            AccountPIN.create("abcd"), AccountBarcode.create("1234"))
            .build();

    controller.profileAccountLogin(
        profiles.currentProfileUnsafe().accounts().firstKey(), credentials).get();

    Assert.assertEquals(2L, this.profile_events.size());
    Assert.assertThat(
        this.profile_events.get(0),
        IsInstanceOf.instanceOf(ProfileCreationSucceeded.class));
    Assert.assertThat(
        this.profile_events.get(1),
        IsInstanceOf.instanceOf(ProfileSelected.class));

    Assert.assertEquals(2L, this.account_events.size());
    Assert.assertThat(
        this.account_events.get(0),
        IsInstanceOf.instanceOf(AccountCreationSucceeded.class));
    Assert.assertThat(
        this.account_events.get(1),
        IsInstanceOf.instanceOf(AccountLoginSucceeded.class));

    Assert.assertEquals(
        "Credentials must be saved",
        Option.some(credentials),
        controller.profileAccountCurrent().credentials());
  }

  /**
   * Trying to log in to an account with the wrong credentials should fail.
   *
   * @throws Exception On errors
   */

  @Test(timeout = 3_000L)
  public final void testProfilesAccountCurrentLoginFailed() throws Exception {

    final ProfilesDatabaseType profiles =
        profilesDatabaseWithoutAnonymous(this.directory_profiles);
    final ProfilesControllerType controller =
        controller(this.executor_books, http, this.book_registry, profiles, downloader, ProfilesControllerContract::accountProviders);

    controller.profileEvents().subscribe(this.profile_events::add);
    controller.accountEvents().subscribe(this.account_events::add);

    final LocalDate date = LocalDate.now();
    final AccountProvider provider = fakeAuthProvider("urn:fake-auth:0");
    controller.profileCreate(provider, "Kermit", date).get();
    controller.profileSelect(profiles.profiles().firstKey()).get();
    controller.profileAccountCreate(provider.id()).get();

    this.http.addResponse(
        "urn:fake-auth:0",
        new HTTPResultError<>(
            401,
            "UNAUTHORIZED",
            0L,
            new HashMap<>(),
            0L,
            new ByteArrayInputStream(new byte[0]),
            Option.none()));

    final AccountAuthenticationCredentials credentials =
        AccountAuthenticationCredentials.builder(
            AccountPIN.create("abcd"), AccountBarcode.create("1234"))
            .build();

    controller.profileAccountCurrentLogin(credentials).get();

    Assert.assertEquals(2L, this.profile_events.size());
    Assert.assertThat(
        this.profile_events.get(0),
        IsInstanceOf.instanceOf(ProfileCreationSucceeded.class));
    Assert.assertThat(
        this.profile_events.get(1),
        IsInstanceOf.instanceOf(ProfileSelected.class));

    Assert.assertEquals(2L, this.account_events.size());
    Assert.assertThat(
        this.account_events.get(0),
        IsInstanceOf.instanceOf(AccountCreationSucceeded.class));
    Assert.assertThat(
        this.account_events.get(1),
        IsInstanceOf.instanceOf(AccountLoginFailed.class));

    Assert.assertTrue(
        "Credentials must not be saved",
        controller.profileAccountCurrent().credentials().isNone());
  }

  /**
   * Trying to log in to an account with the right credentials should succeed.
   *
   * @throws Exception On errors
   */

  @Test(timeout = 3_000L)
  public final void testProfilesAccountCurrentLoginSucceeded() throws Exception {

    final ProfilesDatabaseType profiles =
        profilesDatabaseWithoutAnonymous(this.directory_profiles);
    final ProfilesControllerType controller =
        controller(this.executor_books, http, this.book_registry, profiles, downloader, ProfilesControllerContract::accountProviders);

    controller.profileEvents().subscribe(this.profile_events::add);
    controller.accountEvents().subscribe(this.account_events::add);

    final LocalDate date = LocalDate.now();
    final AccountProvider provider = fakeAuthProvider("urn:fake-auth:0");
    controller.profileCreate(provider, "Kermit", date).get();
    controller.profileSelect(profiles.profiles().firstKey()).get();
    controller.profileAccountCreate(provider.id()).get();

    this.http.addResponse(
        "urn:fake-auth:0",
        new HTTPResultOK<>(
            "OK",
            200,
            new ByteArrayInputStream(new byte[0]),
            0L,
            new HashMap<>(),
            0L));

    final AccountAuthenticationCredentials credentials =
        AccountAuthenticationCredentials.builder(
            AccountPIN.create("abcd"), AccountBarcode.create("1234"))
            .build();

    controller.profileAccountCurrentLogin(credentials).get();

    Assert.assertEquals(2L, this.profile_events.size());
    Assert.assertThat(
        this.profile_events.get(0),
        IsInstanceOf.instanceOf(ProfileCreationSucceeded.class));
    Assert.assertThat(
        this.profile_events.get(1),
        IsInstanceOf.instanceOf(ProfileSelected.class));

    Assert.assertEquals(2L, this.account_events.size());
    Assert.assertThat(
        this.account_events.get(0),
        IsInstanceOf.instanceOf(AccountCreationSucceeded.class));
    Assert.assertThat(
        this.account_events.get(1),
        IsInstanceOf.instanceOf(AccountLoginSucceeded.class));

    Assert.assertEquals(
        "Credentials must be saved",
        Option.some(credentials),
        controller.profileAccountCurrent().credentials());
  }

  /**
   * Trying to log in to an account that doesn't require authentication should trivially succeed.
   *
   * @throws Exception On errors
   */

  @Test(timeout = 3_000L)
  public final void testProfilesAccountCurrentLoginNoAuthSucceeded() throws Exception {

    final ProfilesDatabaseType profiles =
        profilesDatabaseWithoutAnonymous(this.directory_profiles);
    final ProfilesControllerType controller =
        controller(this.executor_books, http, this.book_registry, profiles, downloader, ProfilesControllerContract::accountProviders);

    controller.profileEvents().subscribe(this.profile_events::add);
    controller.accountEvents().subscribe(this.account_events::add);

    final LocalDate date = LocalDate.now();
    final AccountProvider provider = fakeProvider("urn:fake:0");
    controller.profileCreate(provider, "Kermit", date).get();
    controller.profileSelect(profiles.profiles().firstKey()).get();
    controller.profileAccountCreate(provider.id()).get();

    final AccountAuthenticationCredentials credentials =
        AccountAuthenticationCredentials.builder(
            AccountPIN.create("abcd"), AccountBarcode.create("1234"))
            .build();

    controller.profileAccountCurrentLogin(credentials).get();

    Assert.assertEquals(2L, this.profile_events.size());
    Assert.assertThat(
        this.profile_events.get(0),
        IsInstanceOf.instanceOf(ProfileCreationSucceeded.class));
    Assert.assertThat(
        this.profile_events.get(1),
        IsInstanceOf.instanceOf(ProfileSelected.class));

    Assert.assertEquals(2L, this.account_events.size());
    Assert.assertThat(
        this.account_events.get(0),
        IsInstanceOf.instanceOf(AccountCreationSucceeded.class));
    Assert.assertThat(
        this.account_events.get(1),
        IsInstanceOf.instanceOf(AccountLoginSucceeded.class));

    Assert.assertTrue(
        "Credentials must not be saved",
        controller.profileAccountCurrent().credentials().isNone());
  }

  /**
   * Setting and getting bookmarks works.
   *
   * @throws Exception On errors
   */

  @Test(timeout = 3_000L)
  public final void testProfilesBookmarks() throws Exception {

    final ProfilesDatabaseType profiles =
        profilesDatabaseWithoutAnonymous(this.directory_profiles);
    final ProfilesControllerType controller =
        controller(this.executor_books, http, book_registry, profiles, downloader, ProfilesControllerContract::accountProviders);

    final LocalDate date = LocalDate.now();
    final AccountProvider provider = fakeProvider("urn:fake:0");
    controller.profileCreate(provider, "Kermit", date).get();
    controller.profileSelect(profiles.profiles().firstKey()).get();
    controller.profileAccountCreate(provider.id()).get();
    controller.profileEvents().subscribe(this.profile_events::add);

    controller.profileBookmarkSet(
        BookID.create("aaaa"),
        ReaderBookLocation.create(Option.none(), "1")).get();

    Assert.assertEquals(
        "Bookmark must have been saved",
        Option.some(ReaderBookLocation.create(Option.none(), "1")),
        controller.profileBookmarkGet(BookID.create("aaaa")));

    controller.profileBookmarkSet(
        BookID.create("aaaa"),
        ReaderBookLocation.create(Option.none(), "2")).get();

    Assert.assertEquals(
        "Bookmark must have been saved",
        Option.some(ReaderBookLocation.create(Option.none(), "2")),
        controller.profileBookmarkGet(BookID.create("aaaa")));

    Assert.assertEquals(2L, this.profile_events.size());
    Assert.assertThat(
        this.profile_events.get(0),
        IsInstanceOf.instanceOf(ProfilePreferencesChanged.class));

    {
      final ProfilePreferencesChanged change = (ProfilePreferencesChanged) this.profile_events.get(0);
      Assert.assertTrue("Preferences must not have changed", !change.changedReaderPreferences());
      Assert.assertTrue("Bookmarks must have changed", change.changedReaderBookmarks());
    }

    Assert.assertThat(
        this.profile_events.get(1),
        IsInstanceOf.instanceOf(ProfilePreferencesChanged.class));

    {
      final ProfilePreferencesChanged change = (ProfilePreferencesChanged) this.profile_events.get(1);
      Assert.assertTrue("Preferences must not have changed", !change.changedReaderPreferences());
      Assert.assertTrue("Bookmarks must have changed", change.changedReaderBookmarks());
    }
  }

  private ProfilesDatabaseType profilesDatabaseWithoutAnonymous(final File dir_profiles)
      throws ProfileDatabaseException {
    return ProfilesDatabase.openWithAnonymousAccountDisabled(
        accountProviders(Unit.unit()),
        AccountsDatabases.get(),
        dir_profiles);
  }

  private static AccountProviderCollection accountProviders(final Unit unit) {
    return accountProviders();
  }

  private static AccountProviderCollection accountProviders() {
    final AccountProvider fake0 = fakeProvider("urn:fake:0");
    final AccountProvider fake1 = fakeProvider("urn:fake:1");
    final AccountProvider fake2 = fakeProvider("urn:fake:2");
    final AccountProvider fake3 = fakeAuthProvider("urn:fake-auth:0");

    final SortedMap<URI, AccountProvider> providers = new TreeMap<>();
    providers.put(fake0.id(), fake0);
    providers.put(fake1.id(), fake1);
    providers.put(fake2.id(), fake2);
    providers.put(fake3.id(), fake3);
    return AccountProviderCollection.create(fake0, providers);
  }

  private static AccountProvider fakeAuthProvider(final String uri) {
    return fakeProvider(uri)
        .toBuilder()
        .setAuthentication(Option.some(AccountProviderAuthenticationDescription.builder()
            .setLoginURI(URI.create(uri))
            .setPassCodeLength(4)
            .setPassCodeMayContainLetters(true)
            .build()))
        .build();
  }
}