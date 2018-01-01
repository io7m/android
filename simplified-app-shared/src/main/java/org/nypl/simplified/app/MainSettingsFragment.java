package org.nypl.simplified.app;

import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.view.View;

import com.io7m.jfunctional.OptionType;
import com.io7m.jfunctional.ProcedureType;
import com.io7m.jnull.NullCheck;
import com.tenmiles.helpstack.HSHelpStack;
import com.tenmiles.helpstack.gears.HSDeskGear;

import org.nypl.simplified.books.accounts.AccountAuthenticationCredentials;
import org.nypl.simplified.books.core.DocumentStoreType;
import org.nypl.simplified.books.core.SyncedDocumentType;

class MainSettingsFragment extends PreferenceFragment implements LoginListenerType {

  /**
   * Construct an Fragment.
   */

  MainSettingsFragment() {

  }

  @Override
  public void onViewCreated(final View view, final Bundle state) {
    super.onViewCreated(view, state);
    view.setBackgroundColor(getResources().getColor(R.color.light_background));
  }

  @Override
  public void onCreate(final Bundle saved_instance_state) {
    super.onCreate(saved_instance_state);

    addPreferencesFromResource(R.xml.preferences);

    final Resources resources = NullCheck.notNull(this.getResources());
    final DocumentStoreType docs = Simplified.getDocumentStore();
    final OptionType<HelpstackType> helpstack = Simplified.getHelpStack();

    {
      final Preference preferences = findPreference(resources.getString(R.string.settings_accounts));
      preferences.setIntent(null);
      preferences.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
        @Override
        public boolean onPreferenceClick(final Preference preference) {

          final Bundle b = new Bundle();
          SimplifiedActivity.setActivityArguments(b, false);
          final Intent intent = new Intent();
          intent.setClass(getActivity(), MainSettingsAccountsActivity.class);
          intent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
          intent.putExtras(b);

          preferences.setIntent(intent);
          return false;
        }
      });
    }

    {
      if (helpstack.isSome()) {

        final Preference preference = findPreference(resources.getString(R.string.help));
        preference.setIntent(null);
        preference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
          @Override
          public boolean onPreferenceClick(final Preference preference) {
            final HSHelpStack stack =
                HSHelpStack.getInstance(getActivity());
            final HSDeskGear gear =
              new HSDeskGear("https://nypl.desk.com/", "4GBRmMv8ZKG8fGehhA", "12060");
            stack.setGear(gear);
            stack.showHelp(getActivity());
            return false;
          }
        });

      }
    }

    {
      final Intent intent = new Intent(
        MainSettingsFragment.this.getActivity(), WebViewActivity.class);
      final Bundle b = new Bundle();
      WebViewActivity.setActivityArguments(
        b,
        "http://www.librarysimplified.org/acknowledgments.html",
        resources.getString(R.string.settings_about),
        SimplifiedPart.PART_SETTINGS);
      intent.putExtras(b);
      intent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);

      final Preference preferences = findPreference(resources.getString(R.string.settings_about));
      preferences.setIntent(intent);
    }

    {
      final Intent intent =
        new Intent(MainSettingsFragment.this.getActivity(), WebViewActivity.class);
      final Bundle b = new Bundle();
      WebViewActivity.setActivityArguments(
        b,
        "http://www.librarysimplified.org/EULA.html",
        resources.getString(R.string.settings_eula),
        SimplifiedPart.PART_SETTINGS);
      intent.putExtras(b);
      intent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);

      final Preference preferences = findPreference(resources.getString(R.string.settings_eula));
      preferences.setIntent(intent);
    }

    {
      docs.getLicenses().map_(
        new ProcedureType<SyncedDocumentType>() {
          @Override
          public void call(final SyncedDocumentType licenses) {

            final Intent intent = new Intent(
              MainSettingsFragment.this.getActivity(), WebViewActivity.class);
            final Bundle b = new Bundle();
            WebViewActivity.setActivityArguments(
              b,
              licenses.documentGetReadableURL().toString(),
              resources.getString(R.string.settings_licence_software),
              SimplifiedPart.PART_SETTINGS);
            intent.putExtras(b);
            intent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);

            final Preference preferences = findPreference(resources.getString(R.string.settings_licence_software));
            preferences.setIntent(intent);
          }
        });
    }
  }

  @Override
  public void onLoginAborted() {
    // do nothing
  }

  @Override
  public void onLoginFailure(final OptionType<? extends Throwable> error, final String message) {
    // do nothing
  }

  @Override
  public void onLoginSuccess(final AccountAuthenticationCredentials creds) {
    final Intent account = new Intent(this.getActivity(), MainSettingsAccountActivity.class);
    this.startActivity(account);
  }
}
