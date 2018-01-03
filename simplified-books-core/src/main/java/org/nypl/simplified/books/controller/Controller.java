package org.nypl.simplified.books.controller;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.io7m.jfunctional.FunctionType;
import com.io7m.jfunctional.None;
import com.io7m.jfunctional.OptionVisitorType;
import com.io7m.jfunctional.Some;
import com.io7m.jfunctional.Unit;
import com.io7m.jnull.NullCheck;

import org.joda.time.LocalDate;
import org.nypl.simplified.books.accounts.AccountAuthenticationCredentials;
import org.nypl.simplified.books.accounts.AccountEvent;
import org.nypl.simplified.books.accounts.AccountEventCreation;
import org.nypl.simplified.books.accounts.AccountEventDeletion;
import org.nypl.simplified.books.accounts.AccountEventLogin;
import org.nypl.simplified.books.accounts.AccountEventLogout;
import org.nypl.simplified.books.accounts.AccountID;
import org.nypl.simplified.books.accounts.AccountProvider;
import org.nypl.simplified.books.accounts.AccountProviderCollection;
import org.nypl.simplified.books.accounts.AccountType;
import org.nypl.simplified.books.accounts.AccountsDatabaseNonexistentException;
import org.nypl.simplified.books.book_database.BookID;
import org.nypl.simplified.books.book_registry.BookRegistryType;
import org.nypl.simplified.books.profiles.ProfileAccountSelectEvent;
import org.nypl.simplified.books.profiles.ProfileCreationEvent;
import org.nypl.simplified.books.profiles.ProfileEvent;
import org.nypl.simplified.books.profiles.ProfileID;
import org.nypl.simplified.books.profiles.ProfileNoneCurrentException;
import org.nypl.simplified.books.profiles.ProfileNonexistentAccountProviderException;
import org.nypl.simplified.books.profiles.ProfileReadableType;
import org.nypl.simplified.books.profiles.ProfilesDatabaseType;
import org.nypl.simplified.books.profiles.ProfilesDatabaseType.AnonymousProfileEnabled;
import org.nypl.simplified.downloader.core.DownloadType;
import org.nypl.simplified.downloader.core.DownloaderType;
import org.nypl.simplified.http.core.HTTPType;
import org.nypl.simplified.observable.Observable;
import org.nypl.simplified.observable.ObservableReadableType;
import org.nypl.simplified.observable.ObservableType;
import org.nypl.simplified.opds.core.OPDSAcquisition;
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry;

import java.net.URI;
import java.util.ArrayList;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import static com.io7m.jfunctional.Unit.unit;

/**
 * The default controller implementation.
 */

public final class Controller implements BooksControllerType, ProfilesControllerType {

  private final ListeningExecutorService exec;
  private final ProfilesDatabaseType profiles;
  private final BookRegistryType book_registry;
  private final ObservableType<ProfileEvent> profile_events;
  private final FunctionType<Unit, AccountProviderCollection> account_providers;
  private final HTTPType http;
  private final ObservableType<AccountEvent> account_events;
  private final ConcurrentHashMap<BookID, DownloadType> downloads;
  private final DownloaderType downloader;

  private Controller(
      final ExecutorService in_exec,
      final HTTPType in_http,
      final DownloaderType in_downloader,
      final ProfilesDatabaseType in_profiles,
      final BookRegistryType in_book_registry,
      final FunctionType<Unit, AccountProviderCollection> in_account_providers) {

    this.exec =
        MoreExecutors.listeningDecorator(NullCheck.notNull(in_exec, "Executor"));
    this.http =
        NullCheck.notNull(in_http, "HTTP");
    this.downloader =
        NullCheck.notNull(in_downloader, "Downloader");
    this.profiles =
        NullCheck.notNull(in_profiles, "Profiles");
    this.book_registry =
        NullCheck.notNull(in_book_registry, "Book Registry");
    this.account_providers =
        NullCheck.notNull(in_account_providers, "Account providers");

    this.downloads = new ConcurrentHashMap<>(32);
    this.profile_events = Observable.create();
    this.account_events = Observable.create();
  }

  public static Controller createBookController(
      final ExecutorService in_exec,
      final HTTPType in_http,
      final DownloaderType in_downloader,
      final ProfilesDatabaseType in_profiles,
      final BookRegistryType in_book_registry,
      final FunctionType<Unit, AccountProviderCollection> in_account_providers) {

    return new Controller(
        in_exec,
        in_http,
        in_downloader,
        in_profiles,
        in_book_registry,
        in_account_providers);
  }

  /**
   * Perform an unchecked (but safe) cast of the given map type. The cast is safe because
   * {@code V <: VB}.
   */

  @SuppressWarnings("unchecked")
  private static <K, VB, V extends VB> SortedMap<K, VB> castMap(final SortedMap<K, V> m) {
    return (SortedMap<K, VB>) m;
  }

  @Override
  public SortedMap<ProfileID, ProfileReadableType> profiles() {
    return castMap(this.profiles.profiles());
  }

  @Override
  public AnonymousProfileEnabled profileAnonymousEnabled() {
    return this.profiles.anonymousProfileEnabled();
  }

  @Override
  public ProfileReadableType profileCurrent() throws ProfileNoneCurrentException {
    return this.profiles.currentProfileUnsafe();
  }

  @Override
  public ObservableReadableType<ProfileEvent> profileEvents() {
    return this.profile_events;
  }

  @Override
  public ListenableFuture<ProfileCreationEvent> profileCreate(
      final AccountProvider account_provider,
      final String display_name,
      final LocalDate date) {

    NullCheck.notNull(account_provider, "Account provider");
    NullCheck.notNull(display_name, "Display name");
    NullCheck.notNull(date, "Date");

    return this.exec.submit(new ProfileCreationTask(
        this.profiles, this.profile_events, account_provider, display_name, date));
  }

  @Override
  public ListenableFuture<Unit> profileSelect(final ProfileID id) {
    NullCheck.notNull(id, "ID");
    return this.exec.submit(new ProfileSelectionTask(this.profiles, id));
  }

  @Override
  public AccountType profileAccountCurrent() throws ProfileNoneCurrentException {
    final ProfileReadableType profile = this.profileCurrent();
    return profile.accountCurrent();
  }

  @Override
  public ListenableFuture<AccountEventLogin> profileAccountCurrentLogin(
      final AccountAuthenticationCredentials credentials) {
    NullCheck.notNull(credentials, "Credentials");
    return this.exec.submit(new ProfileAccountLoginTask(
        this.http,
        this.profiles,
        this.account_events,
        ProfileReadableType::accountCurrent,
        credentials));
  }

  @Override
  public ListenableFuture<AccountEventLogin> profileAccountLogin(
      final AccountID account,
      final AccountAuthenticationCredentials credentials) {
    NullCheck.notNull(account, "Account");
    NullCheck.notNull(credentials, "Credentials");
    return this.exec.submit(new ProfileAccountLoginTask(
        this.http,
        this.profiles,
        this.account_events,
        p -> p.account(account),
        credentials));
  }

  @Override
  public ListenableFuture<AccountEventCreation> profileAccountCreate(final URI provider) {
    NullCheck.notNull(provider, "Provider");
    return this.exec.submit(new ProfileAccountCreateTask(
        this.profiles, this.account_events, this.account_providers, provider));
  }

  @Override
  public ListenableFuture<AccountEventDeletion> profileAccountDeleteByProvider(final URI provider) {
    NullCheck.notNull(provider, "Provider");
    return this.exec.submit(new ProfileAccountDeleteTask(
        this.profiles, this.account_events, this.profile_events, this.account_providers, provider));
  }

  @Override
  public ListenableFuture<ProfileAccountSelectEvent> profileAccountSelectByProvider(
      final URI provider) {
    NullCheck.notNull(provider, "Provider");
    return this.exec.submit(new ProfileAccountSelectionTask(
        this.profiles, this.profile_events, this.account_providers, provider));
  }

  @Override
  public AccountType profileAccountFindByProvider(
      final URI provider)
      throws ProfileNoneCurrentException, AccountsDatabaseNonexistentException {
    NullCheck.notNull(provider, "Provider");

    final ProfileReadableType profile = this.profileCurrent();
    final AccountType account = profile.accountsByProvider().get(provider);
    if (account == null) {
      throw new AccountsDatabaseNonexistentException("No account with provider: " + provider);
    }
    return account;
  }

  @Override
  public ObservableReadableType<AccountEvent> accountEvents() {
    return this.account_events;
  }

  @Override
  public ImmutableList<AccountProvider> profileCurrentlyUsedAccountProviders()
      throws ProfileNoneCurrentException, ProfileNonexistentAccountProviderException {

    final ArrayList<AccountProvider> accounts = new ArrayList<>();
    final AccountProviderCollection account_providers =
        this.account_providers.call(Unit.unit());
    final ProfileReadableType profile =
        this.profileCurrent();

    for (final AccountType account : profile.accounts().values()) {
      final AccountProvider provider = account.provider();
      if (account_providers.providers().containsKey(provider.id())) {
        final AccountProvider account_provider =
            account_providers.providers().get(provider.id());
        accounts.add(account_provider);
      }
    }

    return ImmutableList.sortedCopyOf(accounts);
  }

  @Override
  public ListenableFuture<AccountEventLogout> profileAccountLogout() {
    return this.exec.submit(new ProfileAccountLogoutTask(
        this.profiles,
        this.book_registry,
        this.account_events
    ));
  }

  @Override
  public void bookBorrow(
      final BookID id,
      final AccountType account,
      final OPDSAcquisition acquisition,
      final OPDSAcquisitionFeedEntry entry) {

    NullCheck.notNull(id, "Book ID");
    NullCheck.notNull(account, "Account");
    NullCheck.notNull(acquisition, "Acquisition");
    NullCheck.notNull(entry, "Entry");

    this.exec.submit(new BookBorrowTask(
        this.downloader,
        this.downloads,
        this.book_registry,
        id,
        account,
        acquisition,
        entry));
  }

  @Override
  public void bookBorrowFailedDismiss(
      final BookID id,
      final AccountType account) {

    NullCheck.notNull(id, "Book ID");
    NullCheck.notNull(account, "Account");

    this.exec.submit(new BookBorrowFailedDismissTask(
        this.downloader,
        this.downloads,
        account.bookDatabase(),
        this.book_registry,
        id));
  }
}
