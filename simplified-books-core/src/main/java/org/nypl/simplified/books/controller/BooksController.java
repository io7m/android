package org.nypl.simplified.books.controller;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.io7m.jfunctional.FunctionType;
import com.io7m.jfunctional.None;
import com.io7m.jfunctional.OptionType;
import com.io7m.jfunctional.OptionVisitorType;
import com.io7m.jfunctional.Some;
import com.io7m.jfunctional.Unit;
import com.io7m.jnull.NullCheck;

import org.joda.time.LocalDate;
import org.nypl.simplified.books.accounts.AccountProvider;
import org.nypl.simplified.books.accounts.AccountProviderCollection;
import org.nypl.simplified.books.accounts.AccountType;
import org.nypl.simplified.books.book_registry.BookRegistryType;
import org.nypl.simplified.books.profiles.ProfileEvent;
import org.nypl.simplified.books.profiles.ProfileID;
import org.nypl.simplified.books.profiles.ProfileNoneCurrentException;
import org.nypl.simplified.books.profiles.ProfileReadableType;
import org.nypl.simplified.books.profiles.ProfilesDatabaseType;
import org.nypl.simplified.books.profiles.ProfilesDatabaseType.AnonymousProfileEnabled;
import org.nypl.simplified.observable.Observable;
import org.nypl.simplified.observable.ObservableReadableType;
import org.nypl.simplified.observable.ObservableType;

import java.net.URI;
import java.util.SortedMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

import static com.io7m.jfunctional.Unit.unit;

public final class BooksController implements BooksControllerType, ProfilesControllerType {

  private final ListeningExecutorService exec;
  private final ProfilesDatabaseType profiles;
  private final BookRegistryType book_registry;
  private final ObservableType<ProfileEvent> profile_events;
  private final FunctionType<Unit, AccountProviderCollection> account_providers;

  private BooksController(
      final ExecutorService in_exec,
      final ProfilesDatabaseType in_profiles,
      final BookRegistryType in_book_registry,
      final FunctionType<Unit, AccountProviderCollection> in_account_providers) {

    this.exec = MoreExecutors.listeningDecorator(NullCheck.notNull(in_exec, "Executor"));
    this.profiles = NullCheck.notNull(in_profiles, "Profiles");
    this.book_registry = NullCheck.notNull(in_book_registry, "Book Registry");
    this.account_providers = NullCheck.notNull(in_account_providers, "Account providers");
    this.profile_events = Observable.create();
  }

  public static BooksController createBookController(
      final ExecutorService in_exec,
      final ProfilesDatabaseType in_profiles,
      final BookRegistryType in_book_registry,
      final FunctionType<Unit, AccountProviderCollection> in_account_providers) {

    return new BooksController(in_exec, in_profiles, in_book_registry, in_account_providers);
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
  public AccountProvider profileAccountProviderCurrent()
      throws ProfileNoneCurrentException,
      ProfileUnknownAccountProviderException {

    final ProfileReadableType profile = this.profileCurrent();
    final URI provider_name = profile.accountCurrent().provider();
    final AccountProviderCollection providers_now = this.account_providers.call(Unit.unit());
    final AccountProvider provider = providers_now.providers().get(provider_name);

    if (provider != null) {
      return provider;
    }

    throw new ProfileUnknownAccountProviderException("Unrecognized provider: " + provider_name);
  }

  @Override
  public ObservableReadableType<ProfileEvent> profileEvents() {
    return this.profile_events;
  }

  @Override
  public ListenableFuture<ProfileEvent> profileCreate(
      final AccountProvider account_provider,
      final String display_name,
      final LocalDate date) {

    NullCheck.notNull(account_provider, "Account provider");
    NullCheck.notNull(display_name, "Display name");
    NullCheck.notNull(date, "Date");
    return exec.submit(new ProfileCreationTask(
        this.profiles, this.profile_events, account_provider, display_name, date));
  }

  @Override
  public ListenableFuture<Unit> profileSelect(final ProfileID id) {

    NullCheck.notNull(id, "ID");
    return exec.submit(new ProfileSelectionTask(this.profiles, id));
  }

  @Override
  public URI profileCurrentCatalogRootURI()
      throws ProfileNoneCurrentException, ProfileUnknownAccountProviderException {

    final ProfileReadableType profile = this.profileCurrent();
    final AccountType account = profile.accountCurrent();
    final AccountProviderCollection providers = this.account_providers.call(unit());
    final AccountProvider provider = providers.providers().get(account.provider());

    if (provider != null) {
      return profile.preferences().dateOfBirth().accept(
          new OptionVisitorType<LocalDate, URI>() {
            @Override
            public URI none(final None<LocalDate> date_none) {
              return provider.catalogURI();
            }

            @Override
            public URI some(final Some<LocalDate> data_some) {
              final LocalDate date_now = LocalDate.now();
              final LocalDate date_birth = data_some.get();
              return provider.catalogURIForAge(date_now.getYear() - date_birth.getYear());
            }
          });
    }

    throw new ProfileUnknownAccountProviderException("Unrecognized provider: " + account.provider());
  }
}
