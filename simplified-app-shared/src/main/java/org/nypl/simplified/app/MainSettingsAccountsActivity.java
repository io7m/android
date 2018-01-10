package org.nypl.simplified.app;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.io7m.jfunctional.Unit;
import com.io7m.jnull.NullCheck;
import com.io7m.jnull.Nullable;

import org.nypl.simplified.app.utilities.ErrorDialogUtilities;
import org.nypl.simplified.app.utilities.UIThread;
import org.nypl.simplified.books.accounts.AccountEvent;
import org.nypl.simplified.books.accounts.AccountEventCreation;
import org.nypl.simplified.books.accounts.AccountEventCreation.AccountCreationFailed;
import org.nypl.simplified.books.accounts.AccountEventCreation.AccountCreationSucceeded;
import org.nypl.simplified.books.accounts.AccountEventDeletion;
import org.nypl.simplified.books.accounts.AccountEventDeletion.AccountDeletionFailed;
import org.nypl.simplified.books.accounts.AccountEventDeletion.AccountDeletionSucceeded;
import org.nypl.simplified.books.accounts.AccountID;
import org.nypl.simplified.books.accounts.AccountProvider;
import org.nypl.simplified.books.accounts.AccountsDatabaseNonexistentException;
import org.nypl.simplified.books.core.LogUtilities;
import org.nypl.simplified.books.profiles.ProfileAccountSelectEvent;
import org.nypl.simplified.books.profiles.ProfileAccountSelectEvent.ProfileAccountSelectFailed;
import org.nypl.simplified.books.profiles.ProfileAccountSelectEvent.ProfileAccountSelectSucceeded;
import org.nypl.simplified.books.profiles.ProfileEvent;
import org.nypl.simplified.books.profiles.ProfileNoneCurrentException;
import org.nypl.simplified.books.profiles.ProfileNonexistentAccountProviderException;
import org.nypl.simplified.observable.ObservableSubscriptionType;
import org.slf4j.Logger;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;

/**
 * The activity displaying the settings for the application.
 */

public final class MainSettingsAccountsActivity extends NavigationDrawerActivity {

  private static final Logger LOG = LogUtilities.getLog(MainSettingsAccountsActivity.class);

  private ArrayAdapter<AccountProvider> adapter_accounts;
  private ObservableSubscriptionType<AccountEvent> accounts_subscription;
  private ObservableSubscriptionType<ProfileEvent> profiles_subscription;
  private ArrayList<AccountProvider> adapter_accounts_array;
  private ListView account_list_view;
  private LinearLayout account_current_view;

  /**
   * Construct an activity.
   */

  public MainSettingsAccountsActivity() {

  }

  @Override
  protected SimplifiedPart navigationDrawerGetPart() {
    return SimplifiedPart.PART_SETTINGS;
  }

  @Override
  protected boolean navigationDrawerShouldShowIndicator() {
    return true;
  }

  @Override
  public void onCreate(final Bundle saved_instance_state) {
    super.onCreate(saved_instance_state);

    final ActionBar bar = this.getActionBar();
    if (android.os.Build.VERSION.SDK_INT < 21) {
      bar.setDisplayHomeAsUpEnabled(false);
      bar.setHomeButtonEnabled(true);
      bar.setIcon(R.drawable.ic_arrow_back);
    } else {
      bar.setHomeAsUpIndicator(R.drawable.ic_arrow_back);
      bar.setDisplayHomeAsUpEnabled(true);
      bar.setHomeButtonEnabled(false);
    }

    this.setContentView(R.layout.accounts);

    this.account_list_view =
        NullCheck.notNull(this.findViewById(R.id.account_list));
    this.account_current_view =
        NullCheck.notNull(this.findViewById(R.id.current_account));

    final LayoutInflater inflater = NullCheck.notNull(this.getLayoutInflater());

    this.adapter_accounts_array = new ArrayList<>();
    this.adapter_accounts =
        new ArrayAdapter<AccountProvider>(this, R.layout.account_list_item, this.adapter_accounts_array) {
          @Override
          public View getView(
              final int position,
              final @Nullable View reuse,
              final @Nullable ViewGroup parent) {

            final View v;
            if (reuse != null) {
              v = reuse;
            } else {
              v = inflater.inflate(R.layout.account_list_item, parent, false);
            }

            final AccountProvider account = NullCheck.notNull(adapter_accounts_array.get(position));
            final TextView item_title_view = NullCheck.notNull(v.findViewById(android.R.id.text1));
            item_title_view.setText(account.displayName());
            item_title_view.setTextColor(R.color.text_black);
            final TextView item_subtitle_view = NullCheck.notNull(v.findViewById(android.R.id.text2));
            item_subtitle_view.setText(account.subtitle());
            item_subtitle_view.setTextColor(R.color.text_black);
            final ImageView icon_view = NullCheck.notNull(v.findViewById(R.id.cellIcon));
            SimplifiedIconViews.configureIconViewFromURI(getAssets(), icon_view, account.logo());
            return v;
          }
        };

    this.account_list_view.setAdapter(this.adapter_accounts);
    this.account_list_view.setOnItemClickListener((adapter_view, view, position, id) -> {
      try {
        final AccountProvider selected_provider =
            NullCheck.notNull(this.adapter_accounts.getItem(position));
        this.openAccountSettings(
            Simplified.getProfilesController()
                .profileAccountFindByProvider(selected_provider.id()).id());
      } catch (final ProfileNoneCurrentException | AccountsDatabaseNonexistentException e) {
        throw new IllegalStateException(e);
      }
    });

    this.account_list_view.setOnItemLongClickListener((adapter_view, view, position, id) -> {
      final AccountProvider selected_provider = adapter_accounts.getItem(position);

      final CharSequence[] items = {"Delete " + selected_provider.displayName()};
      final AlertDialog.Builder builder = new AlertDialog.Builder(this);

      builder.setItems(items, (dialog, item) -> {
        final URI provider_id = selected_provider.id();
        final ListeningExecutorService executor = Simplified.getBackgroundTaskExecutor();
        FluentFuture
            .from(Simplified.getProfilesController().profileAccountDeleteByProvider(provider_id))
            .catching(Exception.class, AccountDeletionFailed::ofException, executor)
            .transform(this::onAccountDeletionEvent, executor);
      });

      builder.create().show();
      return true;
    });

    this.populateAccountsArray();

    try {
      this.updateCurrentAccountView(
          this.account_current_view,
          Simplified.getProfilesController()
              .profileCurrent()
              .accountCurrent()
              .id());
    } catch (final ProfileNoneCurrentException e) {
      throw new IllegalStateException(e);
    }

    this.accounts_subscription =
        Simplified.getProfilesController()
            .accountEvents()
            .subscribe(this::onAccountEvent);

    this.profiles_subscription =
        Simplified.getProfilesController()
            .profileEvents()
            .subscribe(this::onProfileEvent);
  }

  private void updateCurrentAccountView(
      final LinearLayout current_account_view,
      final AccountID account) {

    try {
      UIThread.checkIsUIThread();

      final AccountProvider account_provider =
          Simplified.getProfilesController().profileCurrent().account(account).provider();

      final TextView title_text =
          NullCheck.notNull(current_account_view.findViewById(android.R.id.text1));
      title_text.setText(account_provider.displayName());
      title_text.setTextColor(R.color.text_black);

      final TextView subtitle_text =
          NullCheck.notNull(current_account_view.findViewById(android.R.id.text2));
      subtitle_text.setText(account_provider.subtitle());
      subtitle_text.setTextColor(R.color.text_black);

      final ImageView icon_view =
          NullCheck.notNull(current_account_view.findViewById(R.id.cellIcon));
      SimplifiedIconViews.configureIconViewFromURI(
          this.getAssets(), icon_view, account_provider.logo());

      current_account_view.setOnClickListener(view -> openAccountSettings(account));
    } catch (final ProfileNoneCurrentException | AccountsDatabaseNonexistentException e) {
      throw new IllegalStateException(e);
    }
  }

  private void openAccountSettings(final AccountID account) {
    final Bundle b = new Bundle();
    NavigationDrawerActivity.setActivityArguments(b, false);
    b.putSerializable(MainSettingsAccountActivity.ACCOUNT_ID, account);

    final Intent intent = new Intent();
    intent.setClass(this, MainSettingsAccountActivity.class);
    intent.putExtras(b);
    this.startActivity(intent);
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    this.profiles_subscription.unsubscribe();
  }

  private Unit onAccountEvent(final AccountEvent event) {
    LOG.debug("onAccountEvent: {}", event);
    if (event instanceof AccountEventCreation) {
      return onAccountCreationEvent((AccountEventCreation) event);
    }
    if (event instanceof AccountEventDeletion) {
      return onAccountDeletionEvent((AccountEventDeletion) event);
    }
    return Unit.unit();
  }

  private Unit onAccountCreationEvent(final AccountEventCreation event) {
    return event.matchCreation(this::onAccountCreationSucceeded, this::onAccountCreationFailed);
  }

  private Unit onAccountDeletionEvent(final AccountEventDeletion event) {
    return event.matchDeletion(this::onAccountDeletionSucceeded, this::onAccountDeletionFailed);
  }

  private Unit onAccountDeletionFailed(final AccountDeletionFailed event) {
    LOG.debug("onAccountDeletionFailed: {}", event);

    ErrorDialogUtilities.showError(
        this,
        LOG,
        this.getResources().getString(R.string.profiles_account_deletion_error_general),
        null);

    return Unit.unit();
  }

  private Unit onAccountDeletionSucceeded(final AccountDeletionSucceeded event) {
    LOG.debug("onAccountDeletionSucceeded: {}", event);

    UIThread.runOnUIThread(() -> {
      this.populateAccountsArray();
      this.invalidateOptionsMenu();
    });
    return Unit.unit();
  }

  private Unit onAccountCreationFailed(final AccountCreationFailed event) {
    LOG.debug("onAccountCreationFailed: {}", event);

    ErrorDialogUtilities.showError(
        this,
        LOG,
        this.getResources().getString(R.string.profiles_account_creation_error_general),
        null);

    return Unit.unit();
  }

  private Unit onAccountCreationSucceeded(final AccountCreationSucceeded event) {
    LOG.debug("onAccountCreationSucceeded: {}", event);

    UIThread.runOnUIThread(() -> {
      this.populateAccountsArray();
      this.invalidateOptionsMenu();
    });
    return Unit.unit();
  }

  private Unit onProfileEvent(final ProfileEvent event) {
    if (event instanceof ProfileAccountSelectEvent) {
      final ProfileAccountSelectEvent event_select = (ProfileAccountSelectEvent) event;
      return event_select.matchSelect(
          this::onProfileAccountSelectSucceeded,
          this::onProfileAccountSelectFailed);

    }
    return Unit.unit();
  }

  private Unit onProfileAccountSelectFailed(final ProfileAccountSelectFailed event) {
    LOG.debug("onProfileAccountSelectFailed: {}", event);

    ErrorDialogUtilities.showError(
        this,
        LOG,
        this.getResources().getString(R.string.profiles_account_selection_error_general),
        null);

    return Unit.unit();
  }

  private Unit onProfileAccountSelectSucceeded(final ProfileAccountSelectSucceeded event) {
    LOG.debug("onProfileAccountSelectSucceeded: {}", event);

    UIThread.runOnUIThread(() -> {
      this.updateCurrentAccountView(this.account_current_view, event.accountCurrent());
    });
    return Unit.unit();
  }

  /**
   * Fetch the currently used account providers and insert them all into the list view.
   */

  private void populateAccountsArray() {
    try {

      UIThread.checkIsUIThread();

      final ImmutableList<AccountProvider> providers =
          Simplified.getProfilesController().profileCurrentlyUsedAccountProviders();

      this.adapter_accounts_array.clear();
      this.adapter_accounts_array.addAll(providers);
      Collections.sort(this.adapter_accounts_array);
      this.adapter_accounts.notifyDataSetChanged();

    } catch (final ProfileNoneCurrentException | ProfileNonexistentAccountProviderException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public boolean onOptionsItemSelected(final @Nullable MenuItem item_mn) {

    try {
      final MenuItem item = NullCheck.notNull(item_mn);

      if (item.getItemId() == R.id.add_account) {

        /*
         * Display a list of all of the account providers that are not currently in use
         * by the current profile.
         */

        final PopupMenu menu =
            new PopupMenu(getApplicationContext(), this.findViewById(R.id.add_account));

        final ImmutableList<AccountProvider> used_account_providers =
            Simplified.getProfilesController().profileCurrentlyUsedAccountProviders();
        final ImmutableList<AccountProvider> available_account_providers =
            ImmutableList.sortedCopyOf(Simplified.getAccountProviders().providers().values());

        for (int index = 0; index < available_account_providers.size(); ++index) {
          final AccountProvider provider = available_account_providers.get(index);
          if (!used_account_providers.contains(provider)) {
            menu.getMenu().add(Menu.NONE, index, Menu.NONE, provider.displayName());
          }
        }

        menu.show();
        menu.setOnMenuItemClickListener(menu_item -> {
          final AccountProvider provider = available_account_providers.get(menu_item.getItemId());
          final ListeningExecutorService exec = Simplified.getBackgroundTaskExecutor();
          FluentFuture
              .from(Simplified.getProfilesController().profileAccountCreate(provider.id()))
              .catching(Exception.class, AccountCreationFailed::of, exec)
              .transform(this::onAccountCreationEvent, exec);
          return true;
        });

        return true;
      }

      if (item.getItemId() == android.R.id.home) {
        onBackPressed();
        return true;
      }

      return super.onOptionsItemSelected(item);
    } catch (final ProfileNoneCurrentException | ProfileNonexistentAccountProviderException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Create an options menu that shows a list of any account providers that are not currently
   * in use by the current profile. If the list would be empty, it isn't shown.
   */

  @Override
  public boolean onCreateOptionsMenu(final @Nullable Menu in_menu) {

    try {
      final ImmutableList<AccountProvider> used_account_providers =
          Simplified.getProfilesController().profileCurrentlyUsedAccountProviders();
      final ImmutableList<AccountProvider> available_account_providers =
          ImmutableList.sortedCopyOf(Simplified.getAccountProviders().providers().values());

      if (used_account_providers.size() != available_account_providers.size()) {
        final Menu menu_nn = NullCheck.notNull(in_menu);
        final MenuInflater inflater = this.getMenuInflater();
        inflater.inflate(R.menu.add_account, menu_nn);
      }

      return true;
    } catch (final ProfileNoneCurrentException | ProfileNonexistentAccountProviderException e) {
      throw new IllegalStateException(e);
    }
  }
}
