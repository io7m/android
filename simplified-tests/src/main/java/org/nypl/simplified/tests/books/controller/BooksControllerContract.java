package org.nypl.simplified.tests.books.controller;

import com.io7m.jfunctional.FunctionType;
import com.io7m.jfunctional.Option;
import com.io7m.jfunctional.OptionType;
import com.io7m.jfunctional.Unit;

import org.hamcrest.core.IsInstanceOf;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.nypl.simplified.books.accounts.AccountAuthenticationCredentials;
import org.nypl.simplified.books.accounts.AccountBarcode;
import org.nypl.simplified.books.accounts.AccountEvent;
import org.nypl.simplified.books.accounts.AccountPIN;
import org.nypl.simplified.books.accounts.AccountProvider;
import org.nypl.simplified.books.accounts.AccountProviderAuthenticationDescription;
import org.nypl.simplified.books.accounts.AccountProviderCollection;
import org.nypl.simplified.books.accounts.AccountType;
import org.nypl.simplified.books.accounts.AccountsDatabases;
import org.nypl.simplified.books.book_registry.BookRegistry;
import org.nypl.simplified.books.book_registry.BookRegistryType;
import org.nypl.simplified.books.controller.BooksControllerType;
import org.nypl.simplified.books.controller.Controller;
import org.nypl.simplified.books.feeds.FeedHTTPTransport;
import org.nypl.simplified.books.feeds.FeedLoader;
import org.nypl.simplified.books.feeds.FeedLoaderType;
import org.nypl.simplified.books.profiles.ProfileDatabaseException;
import org.nypl.simplified.books.profiles.ProfileEvent;
import org.nypl.simplified.books.profiles.ProfileType;
import org.nypl.simplified.books.profiles.ProfilesDatabase;
import org.nypl.simplified.books.profiles.ProfilesDatabaseType;
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
import org.nypl.simplified.opds.core.OPDSParseException;
import org.nypl.simplified.opds.core.OPDSSearchParser;
import org.nypl.simplified.tests.http.MockingHTTP;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class BooksControllerContract {

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
  private ProfilesDatabaseType profiles;

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

  private BooksControllerType controller(
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

    return Controller.create(
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
    this.profiles = profilesDatabaseWithoutAnonymous(this.directory_profiles);
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
   * If the remote side returns a non 401 error code, syncing should fail with an IO exception.
   *
   * @throws Exception On errors
   */

  @Test(timeout = 3_000L)
  public final void testBooksSyncRemoteNon401() throws Exception {

    final BooksControllerType controller =
        controller(this.executor_books, http, this.book_registry, this.profiles, this.downloader, BooksControllerContract::accountProviders);

    final AccountProvider provider = fakeAuthProvider("urn:fake-auth:0");
    final ProfileType profile = this.profiles.createProfile(provider, "Kermit");
    this.profiles.setProfileCurrent(profile.id());
    final AccountType account = profile.createAccount(provider);
    account.setCredentials(Option.of(
        AccountAuthenticationCredentials.builder(
            AccountPIN.create("1234"), AccountBarcode.create("abcd"))
            .build()));

    this.http.addResponse(
        "urn:fake-auth:0",
        new HTTPResultError<>(
            400,
            "BAD REQUEST",
            0L,
            new HashMap<>(),
            0L,
            new ByteArrayInputStream(new byte[0]),
            Option.none()));

    this.expected.expect(ExecutionException.class);
    this.expected.expectCause(IsInstanceOf.instanceOf(IOException.class));
    controller.booksSync(account).get();
  }

  /**
   * If the remote side returns a 401 error code, the current credentials should be thrown away.
   *
   * @throws Exception On errors
   */

  @Test(timeout = 3_000L)
  public final void testBooksSyncRemote401() throws Exception {

    final BooksControllerType controller =
        controller(this.executor_books, http, this.book_registry, this.profiles, this.downloader, BooksControllerContract::accountProviders);

    final AccountProvider provider = fakeAuthProvider("urn:fake-auth:0");
    final ProfileType profile = this.profiles.createProfile(provider, "Kermit");
    this.profiles.setProfileCurrent(profile.id());
    final AccountType account = profile.createAccount(provider);
    account.setCredentials(Option.of(
        AccountAuthenticationCredentials.builder(
            AccountPIN.create("1234"), AccountBarcode.create("abcd"))
            .build()));

    Assert.assertTrue(account.credentials().isSome());

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

    controller.booksSync(account).get();

    Assert.assertTrue(account.credentials().isNone());
  }

  /**
   * If the provider does not support authentication, then syncing is impossible and does nothing.
   *
   * @throws Exception On errors
   */

  @Test(timeout = 3_000L)
  public final void testBooksSyncWithoutAuthSupport() throws Exception {

    final BooksControllerType controller =
        controller(this.executor_books, http, this.book_registry, this.profiles, this.downloader, BooksControllerContract::accountProviders);

    final AccountProvider provider = fakeProvider("urn:fake:0");
    final ProfileType profile = this.profiles.createProfile(provider, "Kermit");
    this.profiles.setProfileCurrent(profile.id());
    final AccountType account = profile.createAccount(provider);
    account.setCredentials(Option.of(
        AccountAuthenticationCredentials.builder(
            AccountPIN.create("1234"), AccountBarcode.create("abcd"))
            .build()));

    Assert.assertTrue(account.credentials().isSome());
    controller.booksSync(account).get();
    Assert.assertTrue(account.credentials().isSome());
  }

  /**
   * If the remote side requires authentication but no credentials were provided, nothing happens.
   *
   * @throws Exception On errors
   */

  @Test(timeout = 3_000L)
  public final void testBooksSyncMissingCredentials() throws Exception {

    final BooksControllerType controller =
        controller(this.executor_books, http, this.book_registry, this.profiles, this.downloader, BooksControllerContract::accountProviders);

    final AccountProvider provider = fakeAuthProvider("urn:fake-auth:0");
    final ProfileType profile = this.profiles.createProfile(provider, "Kermit");
    this.profiles.setProfileCurrent(profile.id());
    final AccountType account = profile.createAccount(provider);

    Assert.assertTrue(account.credentials().isNone());
    controller.booksSync(account).get();
    Assert.assertTrue(account.credentials().isNone());
  }

  /**
   * If the remote side returns garbage for a feed, an error is raised.
   *
   * @throws Exception On errors
   */

  @Test(timeout = 3_000L)
  public final void testBooksSyncBadFeed() throws Exception {

    final BooksControllerType controller =
        controller(this.executor_books, http, this.book_registry, this.profiles, this.downloader, BooksControllerContract::accountProviders);

    final AccountProvider provider = fakeAuthProvider("urn:fake-auth:0");
    final ProfileType profile = this.profiles.createProfile(provider, "Kermit");
    this.profiles.setProfileCurrent(profile.id());
    final AccountType account = profile.createAccount(provider);
    account.setCredentials(Option.of(
        AccountAuthenticationCredentials.builder(
            AccountPIN.create("1234"), AccountBarcode.create("abcd"))
            .build()));

    this.http.addResponse(
        "urn:fake-auth:0",
        new HTTPResultOK<>(
            "OK",
            200,
            new ByteArrayInputStream(new byte[]{0x23, 0x10, 0x39, 0x59}),
            4L,
            new HashMap<>(),
            0L));

    this.expected.expect(ExecutionException.class);
    this.expected.expectCause(IsInstanceOf.instanceOf(OPDSParseException.class));
    controller.booksSync(account).get();
  }

  private ProfilesDatabaseType profilesDatabaseWithoutAnonymous(final File dir_profiles)
      throws ProfileDatabaseException {
    return ProfilesDatabase.openWithAnonymousAccountDisabled(
        accountProviders(Unit.unit()),
        AccountsDatabases.get(),
        dir_profiles);
  }
}
