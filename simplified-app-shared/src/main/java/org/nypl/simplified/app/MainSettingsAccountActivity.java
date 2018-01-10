package org.nypl.simplified.app;

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.io7m.jfunctional.OptionType;
import com.io7m.jfunctional.Some;
import com.io7m.jfunctional.Unit;
import com.io7m.jnull.NullCheck;
import com.io7m.jnull.Nullable;
import com.tenmiles.helpstack.HSHelpStack;
import com.tenmiles.helpstack.gears.HSDeskGear;

import org.nypl.simplified.app.utilities.ErrorDialogUtilities;
import org.nypl.simplified.app.utilities.UIThread;
import org.nypl.simplified.books.accounts.AccountAuthenticationCredentials;
import org.nypl.simplified.books.accounts.AccountBarcode;
import org.nypl.simplified.books.accounts.AccountEvent;
import org.nypl.simplified.books.accounts.AccountEventLogin;
import org.nypl.simplified.books.accounts.AccountEventLogin.AccountLoginFailed;
import org.nypl.simplified.books.accounts.AccountEventLogin.AccountLoginSucceeded;
import org.nypl.simplified.books.accounts.AccountEventLogout;
import org.nypl.simplified.books.accounts.AccountEventLogout.AccountLogoutFailed;
import org.nypl.simplified.books.accounts.AccountEventLogout.AccountLogoutSucceeded;
import org.nypl.simplified.books.accounts.AccountID;
import org.nypl.simplified.books.accounts.AccountPIN;
import org.nypl.simplified.books.accounts.AccountType;
import org.nypl.simplified.books.core.AuthenticationDocumentType;
import org.nypl.simplified.books.core.DocumentStoreType;
import org.nypl.simplified.books.core.EULAType;
import org.nypl.simplified.books.core.LogUtilities;
import org.nypl.simplified.books.profiles.ProfileNoneCurrentException;
import org.nypl.simplified.books.profiles.ProfileReadableType;
import org.nypl.simplified.observable.ObservableSubscriptionType;
import org.slf4j.Logger;

import java.net.URI;

/**
 * The activity displaying the settings for the application.
 */

public final class MainSettingsAccountActivity extends NavigationDrawerActivity {

  public static final String ACCOUNT_ID =
      "org.nypl.simplified.app.MainSettingsAccountActivity.account_id";

  private static final Logger LOG = LogUtilities.getLog(MainSettingsActivity.class);

  private TextView account_name_text;
  private TextView account_subtitle_text;
  private ImageView account_icon;
  private TextView barcode_text;
  private TextView pin_text;
  private TableLayout table_with_code;
  private TableLayout table_signup;
  private Button login;
  private TableRow report_issue;
  private TableRow support_center;
  private CheckBox eula_checkbox;
  private TextView barcode_label;
  private TextView pin_label;
  private CheckBox pin_reveal;
  private Button signup;
  private TableRow privacy;
  private TableRow license;
  private AccountType account;
  private ObservableSubscriptionType<AccountEvent> account_event_subscription;

  /**
   * Construct an activity.
   */

  public MainSettingsAccountActivity() {

  }

  /**
   * Get either the currently selected account, or the account that was passed explicitly to the
   * activity.
   */

  private static AccountType getAccount(final Bundle extras) {

    try {
      final ProfileReadableType profile = Simplified.getProfilesController().profileCurrent();
      if (extras != null && extras.containsKey(ACCOUNT_ID)) {
        final AccountID account_id = (AccountID) extras.getSerializable(ACCOUNT_ID);
        return NullCheck.notNull(profile.accounts().get(account_id), "Account");
      } else {
        return profile.accountCurrent();
      }
    } catch (final ProfileNoneCurrentException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  protected SimplifiedPart navigationDrawerGetPart() {
    return SimplifiedPart.PART_ACCOUNT;
  }

  @Override
  protected boolean navigationDrawerShouldShowIndicator() {
    return true;
  }

  @Override
  protected void onActivityResult(final int request_code, final int result_code, final Intent data) {

    /*
     * Retrieve the PIN from the activity that was launched to collect it.
     */

    if (request_code == 1) {
      // Challenge completed, proceed with using cipher
      final CheckBox in_pin_reveal = NullCheck.notNull(
          this.findViewById(R.id.settings_reveal_password));

      if (result_code == RESULT_OK) {
        final TextView in_pin_text = NullCheck.notNull(this.findViewById(R.id.settings_pin_text));
        in_pin_text.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
        in_pin_reveal.setChecked(true);
      } else {
        // The user canceled or didn't complete the lock screen
        // operation. Go to error/cancellation flow.
        in_pin_reveal.setChecked(false);
      }
    }
  }

  @Override
  public boolean onOptionsItemSelected(final @Nullable MenuItem item_mn) {
    final MenuItem item = NullCheck.notNull(item_mn);

    if (item.getItemId() == R.id.show_eula) {
      final Intent eula_intent = new Intent(this, MainEULAActivity.class);
      this.account.provider().eula().map_(eula_uri -> {
        final Bundle b = new Bundle();
        MainEULAActivity.setActivityArguments(b, eula_uri.toString());
        eula_intent.putExtras(b);
        this.startActivity(eula_intent);
      });
      return true;
    }

    switch (item.getItemId()) {
      case android.R.id.home: {
        onBackPressed();
        return true;
      }

      default: {
        return super.onOptionsItemSelected(item);
      }
    }
  }

  @Override
  protected void onCreate(final @Nullable Bundle state) {
    super.onCreate(state);

    final LayoutInflater inflater = NullCheck.notNull(this.getLayoutInflater());
    final FrameLayout content_area = this.getContentFrame();
    final ViewGroup layout = NullCheck.notNull(
        (ViewGroup) inflater.inflate(R.layout.settings_account, content_area, false));
    content_area.addView(layout);
    content_area.requestLayout();

    final Bundle extras = getIntent().getExtras();
    this.account = getAccount(extras);

    this.account_name_text =
        NullCheck.notNull(this.findViewById(android.R.id.text1));
    this.account_subtitle_text =
        NullCheck.notNull(this.findViewById(android.R.id.text2));
    this.account_icon =
        NullCheck.notNull(this.findViewById(R.id.account_icon));
    this.table_with_code =
        NullCheck.notNull(this.findViewById(R.id.settings_login_table_with_code));
    this.barcode_label =
        NullCheck.notNull(this.findViewById(R.id.settings_barcode_label));
    this.barcode_text =
        NullCheck.notNull(this.findViewById(R.id.settings_barcode_text));
    this.pin_text =
        NullCheck.notNull(this.findViewById(R.id.settings_pin_text));
    this.pin_label =
        NullCheck.notNull(this.findViewById(R.id.settings_pin_label));
    this.pin_reveal =
        NullCheck.notNull(this.findViewById(R.id.settings_reveal_password));
    this.login =
        NullCheck.notNull(this.findViewById(R.id.settings_login));
    this.table_signup =
        NullCheck.notNull(this.findViewById(R.id.settings_signup_table));
    this.report_issue =
        NullCheck.notNull(this.findViewById(R.id.report_issue));
    this.support_center =
        NullCheck.notNull(this.findViewById(R.id.support_center));
    this.eula_checkbox =
        NullCheck.notNull(this.findViewById(R.id.eula_checkbox));
    this.signup =
        NullCheck.notNull(this.findViewById(R.id.settings_signup));
    this.privacy =
        NullCheck.notNull(this.findViewById(R.id.link_privacy));
    this.license =
        NullCheck.notNull(this.findViewById(R.id.link_license));

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

    this.account_name_text.setText(this.account.provider().displayName());
    this.account_subtitle_text.setText(this.account.provider().subtitle());

    /*
     * Show the "Support Center" section if the provider offers one.
     */

    if (this.account.provider().supportEmail().isSome()) {
      this.report_issue.setVisibility(View.VISIBLE);
      this.report_issue.setOnClickListener(view -> {
        final Intent intent = new Intent(MainSettingsAccountActivity.this, ReportIssueActivity.class);
        final Bundle b = new Bundle();
        b.putSerializable("selected_account", this.account.id());
        intent.putExtras(b);
        this.startActivity(intent);
      });
    } else {
      this.report_issue.setVisibility(View.GONE);
    }

    /*
     * Show the "Help Center" section if the provider offers one.
     */

    if (this.account.provider().supportsHelpCenter()) {
      this.support_center.setVisibility(View.VISIBLE);
      this.support_center.setOnClickListener(view -> {
        final HSHelpStack stack = HSHelpStack.getInstance(MainSettingsAccountActivity.this);
        final HSDeskGear gear = new HSDeskGear(" ", " ", null);
        stack.setGear(gear);
        stack.showHelp(MainSettingsAccountActivity.this);
      });
    } else {
      this.support_center.setVisibility(View.GONE);
    }

    /*
     * Show the "Card Creator" section if the provider supports it.
     */

    if (this.account.provider().supportsCardCreator()) {
      this.table_signup.setVisibility(View.VISIBLE);
      this.signup.setOnClickListener(v -> {
        final Intent cardcreator = new Intent(this, CardCreatorActivity.class);
        this.startActivity(cardcreator);
      });
      this.signup.setText(R.string.need_card_button);
    } else {
      this.table_signup.setVisibility(View.GONE);
    }

    /*
     * Configure the barcode and PIN entry section. This will be hidden entirely if the
     * provider doesn't support/require authentication.
     */

    // Get labels from the current authentication document.
    // XXX: This should be per-account
    final DocumentStoreType docs = Simplified.getDocumentStore();
    final AuthenticationDocumentType auth_doc = docs.getAuthenticationDocument();
    this.barcode_label.setText(auth_doc.getLabelLoginUserID());
    this.pin_label.setText(auth_doc.getLabelLoginPassword());

    this.pin_text.setTransformationMethod(PasswordTransformationMethod.getInstance());
    if (android.os.Build.VERSION.SDK_INT >= 21) {
      this.handle_pin_reveal(this.pin_text, this.pin_reveal);
    } else {
      this.pin_reveal.setVisibility(View.GONE);
    }

    if (this.account.provider().authentication().isSome()) {
      this.table_with_code.setVisibility(View.VISIBLE);
      this.login.setVisibility(View.VISIBLE);
      this.configureLoginFieldVisibilityAndContents();
    } else {
      this.table_with_code.setVisibility(View.GONE);
      this.login.setVisibility(View.GONE);
    }

    /*
     * Show the "Privacy Policy" section if the provider has one.
     */

    if (this.account.provider().privacyPolicy().isSome()) {
      this.privacy.setVisibility(View.VISIBLE);
      this.privacy.setOnClickListener(view -> {
        final Intent intent = new Intent(MainSettingsAccountActivity.this, WebViewActivity.class);
        final Bundle b = new Bundle();
        WebViewActivity.setActivityArguments(
            b,
            ((Some<URI>) this.account.provider().privacyPolicy()).get().toString(),
            "Privacy Policy",
            SimplifiedPart.PART_SETTINGS);
        intent.putExtras(b);
        this.startActivity(intent);
      });
    } else {
      this.privacy.setVisibility(View.GONE);
    }

    /*
     * Show the "Content License" section if the provider has one.
     */

    if (this.account.provider().license().isSome()) {
      this.license.setVisibility(View.VISIBLE);
      this.license.setOnClickListener(view -> {
        final Intent intent = new Intent(MainSettingsAccountActivity.this, WebViewActivity.class);
        final Bundle b = new Bundle();
        WebViewActivity.setActivityArguments(
            b,
            ((Some<URI>) this.account.provider().license()).get().toString(),
            "Content Licenses",
            SimplifiedPart.PART_SETTINGS);
        intent.putExtras(b);
        this.startActivity(intent);
      });
    } else {
      this.license.setVisibility(View.GONE);
    }

    this.navigationDrawerSetActionBarTitle();

    /*
     * Configure the EULA views if there is one.
     */

    final OptionType<EULAType> eula_opt = docs.getEULA();
    if (eula_opt.isSome()) {
      final Some<EULAType> some_eula = (Some<EULAType>) eula_opt;
      final EULAType eula = some_eula.get();
      this.eula_checkbox.setChecked(eula.eulaHasAgreed());
      this.eula_checkbox.setEnabled(true);
      this.eula_checkbox.setOnCheckedChangeListener((button, checked) -> eula.eulaSetHasAgreed(checked));

      if (eula.eulaHasAgreed()) {
        LOG.debug("EULA: agreed");
      } else {
        LOG.debug("EULA: not agreed");
      }
    } else {
      LOG.debug("EULA: unavailable");
    }
  }

  private Unit onAccountEvent(final AccountEvent event) {
    LOG.debug("onAccountEvent: {}", event);

    if (event instanceof AccountEventLogin) {
      final AccountEventLogin event_login = (AccountEventLogin) event;
      return event_login.matchLogin(
          this::onAccountEventLoginSucceeded,
          this::onAccountEventLoginFailed);
    }

    if (event instanceof AccountEventLogout) {
      final AccountEventLogout event_logout = (AccountEventLogout) event;
      return event_logout.matchLogout(
          this::onAccountEventLogoutSucceeded,
          this::onAccountEventLogoutFailed);
    }

    return Unit.unit();
  }

  private Unit onAccountEventLoginFailed(final AccountLoginFailed failed) {
    LOG.debug("onLoginFailed: {}", failed);

    ErrorDialogUtilities.showErrorWithRunnable(
        this,
        LOG,
        LoginDialog.loginErrorCodeToLocalizedMessage(this.getResources(), failed.errorCode()),
        null,
        () -> this.login.setEnabled(true));

    return Unit.unit();
  }

  private Unit onAccountEventLoginSucceeded(final AccountLoginSucceeded succeeded) {
    LOG.debug("onLoginSucceeded: {}", succeeded);

    UIThread.runOnUIThread(this::configureLoginFieldVisibilityAndContents);
    return Unit.unit();
  }

  private Unit onAccountEventLogoutFailed(final AccountLogoutFailed failed) {
    LOG.debug("onLogoutFailed: {}", failed);

    ErrorDialogUtilities.showErrorWithRunnable(
        this,
        LOG,
        this.getResources().getString(R.string.settings_logout_failed),
        null,
        () -> this.login.setEnabled(true));

    return Unit.unit();
  }

  private Unit onAccountEventLogoutSucceeded(final AccountLogoutSucceeded succeeded) {
    LOG.debug("onLogoutSucceeded: {}", succeeded);

    UIThread.runOnUIThread(this::configureLoginFieldVisibilityAndContents);
    return Unit.unit();
  }

  private void configureLoginFieldVisibilityAndContents() {
    final OptionType<AccountAuthenticationCredentials> credentials_opt = this.account.credentials();
    if (credentials_opt.isSome()) {
      final AccountAuthenticationCredentials credentials =
          ((Some<AccountAuthenticationCredentials>) credentials_opt).get();

      this.pin_text.setText(credentials.pin().value());
      this.pin_text.setEnabled(false);

      this.barcode_text.setText(credentials.barcode().value());
      this.barcode_text.setEnabled(false);

      this.login.setEnabled(true);
      this.login.setText(R.string.settings_log_out);
      this.login.setOnClickListener(this::tryLogout);
    } else {
      this.pin_text.setText("");
      this.pin_text.setEnabled(true);

      this.barcode_text.setText("");
      this.barcode_text.setEnabled(true);

      this.login.setEnabled(true);
      this.login.setText(R.string.settings_log_in);
      this.login.setOnClickListener(this::tryLogin);
    }
  }

  private void tryLogout(final View view) {
    this.login.setEnabled(false);

    final ListeningExecutorService exec = Simplified.getBackgroundTaskExecutor();

    FluentFuture
        .from(Simplified.getProfilesController().profileAccountLogout())
        .catching(Exception.class, AccountLogoutFailed::ofException, exec)
        .transform(this::onAccountEvent, exec);
  }

  private void tryLogin(final View view) {
    this.login.setEnabled(false);

    final AccountAuthenticationCredentials credentials =
        AccountAuthenticationCredentials.builder(
            AccountPIN.create(this.pin_text.getText().toString()),
            AccountBarcode.create(this.barcode_text.getText().toString()))
            .build();

    final ListeningExecutorService exec = Simplified.getBackgroundTaskExecutor();

    FluentFuture
        .from(Simplified.getProfilesController().profileAccountCurrentLogin(credentials))
        .catching(Exception.class, AccountLoginFailed::ofException, exec)
        .transform(this::onAccountEvent, exec);
  }

  @TargetApi(21)
  private void handle_pin_reveal(final TextView in_pin_text, final CheckBox in_pin_reveal) {

    /*
     * Add a listener that reveals/hides the password field.
     */

    in_pin_reveal.setOnCheckedChangeListener(
        (view, checked) -> {
          if (checked) {
            final KeyguardManager keyguard_manager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (!keyguard_manager.isKeyguardSecure()) {
              // Show a message that the user hasn't set up a lock screen.
              Toast.makeText(this, R.string.settings_screen_Lock_not_setup, Toast.LENGTH_LONG).show();
              in_pin_reveal.setChecked(false);
            } else {
              final Intent intent = keyguard_manager.createConfirmDeviceCredentialIntent(null, null);
              if (intent != null) {
                startActivityForResult(intent, 1);
              }
            }
          } else {
            in_pin_text.setTransformationMethod(
                PasswordTransformationMethod.getInstance());
          }
        });
  }

  @Override
  public boolean onCreateOptionsMenu(
      final @Nullable Menu in_menu) {

    final Menu menu_nn = NullCheck.notNull(in_menu);
    final MenuInflater inflater = this.getMenuInflater();
    inflater.inflate(R.menu.eula, menu_nn);
    return true;
  }
}
