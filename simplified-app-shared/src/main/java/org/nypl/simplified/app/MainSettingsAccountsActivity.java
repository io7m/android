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
import com.io7m.jfunctional.Unit;
import com.io7m.jnull.NullCheck;
import com.io7m.jnull.Nullable;

import org.nypl.simplified.app.utilities.UIThread;
import org.nypl.simplified.books.accounts.AccountEvent;
import org.nypl.simplified.books.accounts.AccountEvent.AccountChanged;
import org.nypl.simplified.books.accounts.AccountEvent.AccountCreationEvent;
import org.nypl.simplified.books.accounts.AccountEvent.AccountCreationEvent.AccountCreationFailed;
import org.nypl.simplified.books.accounts.AccountEvent.AccountCreationEvent.AccountCreationSucceeded;
import org.nypl.simplified.books.accounts.AccountEvent.AccountDeletionEvent.AccountDeletionFailed;
import org.nypl.simplified.books.accounts.AccountEvent.AccountDeletionEvent.AccountDeletionSucceeded;
import org.nypl.simplified.books.accounts.AccountEvent.AccountLoginEvent;
import org.nypl.simplified.books.accounts.AccountProvider;
import org.nypl.simplified.books.core.LogUtilities;
import org.nypl.simplified.observable.ObservableSubscriptionType;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;

/**
 * The activity displaying the settings for the application.
 */

public final class MainSettingsAccountsActivity extends SimplifiedActivity {

  private static final Logger LOG = LogUtilities.getLog(MainSettingsAccountsActivity.class);

  private ArrayAdapter<AccountProvider> adapter_accounts;
  private ObservableSubscriptionType<AccountEvent> accounts_subscription;
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

    updateCurrentAccountView(account_current_view);

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
      final AccountProvider selected_provider = adapter_accounts.getItem(position);
      final Bundle b = new Bundle();
      SimplifiedActivity.setActivityArguments(b, false);
      final Intent intent = new Intent();
      intent.setClass(this, MainSettingsAccountActivity.class);
      intent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
      intent.putExtras(b);
      this.startActivity(intent);
    });

    this.account_list_view.setOnItemLongClickListener((adapter_view, view, position, id) -> {
      final AccountProvider selected_provider = adapter_accounts.getItem(position);

      final CharSequence[] items = {"Delete " + selected_provider.displayName()};
      final AlertDialog.Builder builder = new AlertDialog.Builder(this);

      builder.setItems(items, (dialog, item) -> {
        Simplified.getProfilesController().profileAccountDelete(selected_provider.id());
      });

      builder.create().show();
      return true;
    });

    this.populateAccountsArray();
    this.accounts_subscription =
        Simplified.getProfilesController()
            .accountEvents()
            .subscribe(this::onAccountEvent);
  }

  private void updateCurrentAccountView(final LinearLayout current_account_view) {
    UIThread.checkIsUIThread();

    final AccountProvider account_provider =
        Simplified.getProfilesController().profileAccountProviderCurrent();

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

    current_account_view.setOnClickListener(view -> {
      final Bundle b = new Bundle();
      SimplifiedActivity.setActivityArguments(b, false);
      final Intent intent = new Intent();
      intent.setClass(MainSettingsAccountsActivity.this, MainSettingsAccountActivity.class);
      intent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
      intent.putExtras(b);
      this.startActivity(intent);
    });
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    this.accounts_subscription.unsubscribe();
  }

  private void onAccountEvent(final AccountEvent event) {
    LOG.debug("onAccountEvent: {}", event);
    event.match(
        this::onAccountCreationEvent,
        this::onAccountDeletionEvent,
        this::onAccountLoginEvent,
        this::onAccountChangedEvent);
  }

  private Unit onAccountChangedEvent(final AccountChanged event) {
    LOG.debug("onAccountChangedEvent: {}", event);

    UIThread.runOnUIThread(() -> this.updateCurrentAccountView(this.account_current_view));
    return Unit.unit();
  }

  private Unit onAccountLoginEvent(final AccountLoginEvent event) {
    LOG.debug("onAccountLoginEvent: {}", event);
    return Unit.unit();
  }

  private Unit onAccountCreationEvent(final AccountCreationEvent event) {
    return event.matchCreation(this::onAccountCreationSucceeded, this::onAccountCreationFailed);
  }

  private Unit onAccountDeletionEvent(final AccountEvent.AccountDeletionEvent event) {
    return event.matchDeletion(this::onAccountDeletionSucceeded, this::onAccountDeletionFailed);
  }

  private Unit onAccountDeletionFailed(final AccountDeletionFailed event) {
    LOG.debug("onAccountDeletionFailed: {}", event);
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

  /**
   * Fetch the currently used account providers and insert them all into the list view.
   */

  private void populateAccountsArray() {
    UIThread.checkIsUIThread();

    final ImmutableList<AccountProvider> providers =
        Simplified.getProfilesController().profileCurrentlyUsedAccountProviders();

    this.adapter_accounts_array.clear();
    this.adapter_accounts_array.addAll(providers);
    Collections.sort(this.adapter_accounts_array);
    this.adapter_accounts.notifyDataSetChanged();
  }

  @Override
  public boolean onOptionsItemSelected(final @Nullable MenuItem item_mn) {

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
        Simplified.getProfilesController().profileAccountCreate(provider.id());
        return true;
      });

      return true;
    }

    if (item.getItemId() == android.R.id.home) {
      onBackPressed();
      return true;
    }

    return super.onOptionsItemSelected(item);
  }

  /**
   * Create an options menu that shows a list of any account providers that are not currently
   * in use by the current profile. If the list would be empty, it isn't shown.
   */

  @Override
  public boolean onCreateOptionsMenu(final @Nullable Menu in_menu) {

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
  }
}
