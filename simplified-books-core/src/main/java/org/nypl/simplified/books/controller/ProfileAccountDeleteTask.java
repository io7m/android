package org.nypl.simplified.books.controller;

import com.io7m.jfunctional.FunctionType;
import com.io7m.jfunctional.Option;
import com.io7m.jfunctional.Unit;
import com.io7m.jnull.NullCheck;

import org.nypl.simplified.books.accounts.AccountEvent;
import org.nypl.simplified.books.accounts.AccountEvent.AccountChanged;
import org.nypl.simplified.books.accounts.AccountEvent.AccountDeletionEvent;
import org.nypl.simplified.books.accounts.AccountEvent.AccountDeletionEvent.AccountDeletionFailed;
import org.nypl.simplified.books.accounts.AccountEvent.AccountDeletionEvent.AccountDeletionSucceeded;
import org.nypl.simplified.books.accounts.AccountID;
import org.nypl.simplified.books.accounts.AccountProvider;
import org.nypl.simplified.books.accounts.AccountProviderCollection;
import org.nypl.simplified.books.accounts.AccountType;
import org.nypl.simplified.books.accounts.AccountsDatabaseException;
import org.nypl.simplified.books.accounts.AccountsDatabaseLastAccountException;
import org.nypl.simplified.books.accounts.AccountsDatabaseType;
import org.nypl.simplified.books.profiles.ProfileDatabaseException;
import org.nypl.simplified.books.profiles.ProfileType;
import org.nypl.simplified.books.profiles.ProfilesDatabaseType;
import org.nypl.simplified.observable.ObservableType;

import java.net.URI;
import java.util.concurrent.Callable;

import static org.nypl.simplified.books.accounts.AccountEvent.AccountDeletionEvent.AccountDeletionFailed.ErrorCode.ERROR_ACCOUNT_DATABASE_PROBLEM;
import static org.nypl.simplified.books.accounts.AccountEvent.AccountDeletionEvent.AccountDeletionFailed.ErrorCode.ERROR_ACCOUNT_ONLY_ONE_REMAINING;
import static org.nypl.simplified.books.accounts.AccountEvent.AccountDeletionEvent.AccountDeletionFailed.ErrorCode.ERROR_PROFILE_CONFIGURATION;

final class ProfileAccountDeleteTask implements Callable<AccountDeletionEvent> {

  private final ProfilesDatabaseType profiles;
  private final FunctionType<Unit, AccountProviderCollection> account_providers;
  private final URI provider_id;
  private final ObservableType<AccountEvent> account_events;

  ProfileAccountDeleteTask(
      final ProfilesDatabaseType profiles,
      final ObservableType<AccountEvent> account_events,
      final FunctionType<Unit, AccountProviderCollection> account_providers,
      final URI provider) {

    this.profiles =
        NullCheck.notNull(profiles, "Profiles");
    this.account_events =
        NullCheck.notNull(account_events, "Account events");
    this.account_providers =
        NullCheck.notNull(account_providers, "Account providers");
    this.provider_id =
        NullCheck.notNull(provider, "Provider");
  }

  @Override
  public AccountDeletionEvent call() {
    final AccountDeletionEvent event = run();
    this.account_events.send(event);
    return event;
  }

  private AccountDeletionEvent run() {

    try {
      final AccountProviderCollection providers_now = this.account_providers.call(Unit.unit());
      final AccountProvider provider = providers_now.providers().get(this.provider_id);

      if (provider != null) {
        final ProfileType profile = this.profiles.currentProfileUnsafe();
        final AccountType account_then = profile.accountCurrent();
        profile.deleteAccountByProvider(provider);
        final AccountType account_now = profile.accountCurrent();
        if (!account_now.id().equals(account_then.id())) {
          this.account_events.send(AccountChanged.of(account_now.id()));
        }
        return AccountDeletionSucceeded.of(provider);
      }

      throw new ProfileUnknownAccountProviderException("Unrecognized provider: " + this.provider_id);
    } catch (final ProfileControllerException | ProfileDatabaseException e) {
      return AccountDeletionFailed.of(ERROR_PROFILE_CONFIGURATION, Option.some(e));
    } catch (final AccountsDatabaseLastAccountException e) {
      return AccountDeletionFailed.of(ERROR_ACCOUNT_ONLY_ONE_REMAINING, Option.some(e));
    } catch (final AccountsDatabaseException e) {
      return AccountDeletionFailed.of(ERROR_ACCOUNT_DATABASE_PROBLEM, Option.some(e));
    }
  }
}
