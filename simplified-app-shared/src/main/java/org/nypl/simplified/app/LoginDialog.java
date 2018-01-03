package org.nypl.simplified.app;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.io7m.jfunctional.None;
import com.io7m.jfunctional.Option;
import com.io7m.jfunctional.OptionType;
import com.io7m.jfunctional.OptionVisitorType;
import com.io7m.jfunctional.ProcedureType;
import com.io7m.jfunctional.Some;
import com.io7m.jfunctional.Unit;
import com.io7m.jnull.NullCheck;
import com.io7m.jnull.Nullable;
import com.io7m.junreachable.UnreachableCodeException;

import org.nypl.simplified.app.utilities.UIThread;
import org.nypl.simplified.assertions.Assertions;
import org.nypl.simplified.books.accounts.AccountAuthenticationCredentials;
import org.nypl.simplified.books.accounts.AccountAuthenticationProvider;
import org.nypl.simplified.books.accounts.AccountBarcode;
import org.nypl.simplified.books.accounts.AccountEventLogin;
import org.nypl.simplified.books.accounts.AccountProvider;
import org.nypl.simplified.books.accounts.AccountProviderAuthenticationDescription;
import org.nypl.simplified.books.accounts.AccountProviderCollection;
import org.nypl.simplified.books.accounts.AccountType;
import org.nypl.simplified.books.accounts.AccountsDatabaseNonexistentProviderException;
import org.nypl.simplified.books.controller.ProfilesControllerType;
import org.nypl.simplified.books.accounts.AccountPIN;
import org.nypl.simplified.books.core.AuthenticationDocumentType;
import org.nypl.simplified.books.core.DocumentStoreType;
import org.nypl.simplified.books.core.EULAType;
import org.nypl.simplified.books.core.LogUtilities;
import org.slf4j.Logger;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.nypl.simplified.books.accounts.AccountEventLogin.*;

/**
 * A reusable login dialog.
 */

public final class LoginDialog extends DialogFragment {

  private static final String BARCODE_ID;
  private static final Logger LOG;
  private static final String PIN_ID;
  private static final String TEXT_ID;
  private static final String PIN_ALLOWS_LETTERS;
  private static final String PIN_LENGTH;

  static {
    LOG = LogUtilities.getLog(LoginDialog.class);
    BARCODE_ID = "org.nypl.simplified.app.LoginDialog.barcode";
    PIN_ID = "org.nypl.simplified.app.LoginDialog.pin";
    TEXT_ID = "org.nypl.simplified.app.LoginDialog.text";
    PIN_ALLOWS_LETTERS = "org.nypl.simplified.app.LoginDialog.pinAllowsLetters";
    PIN_LENGTH = "org.nypl.simplified.app.LoginDialog.pinLength";
  }

  private EditText barcode_edit;
  private Button login;
  private EditText pin_edit;
  private TextView text;
  private Button cancel;
  private ProfilesControllerType controller;
  private Runnable on_login_success;
  private ProcedureType<String> on_login_failure;
  private Runnable on_login_cancelled;
  private ListenableFuture<AccountEventLogin> login_task;
  private AccountType account;

  /**
   * Construct a new dialog.
   */

  public LoginDialog() {
    // Fragments must have no-arg constructors.
  }

  /**
   * @param resources The application resources
   * @param message   The error message returned by the device activation code
   * @return An appropriate humanly-readable error message
   */

  public static String getDeviceActivationErrorMessage(
      final Resources resources,
      final String message) {

    /*
     * This is absolutely not the way to do this. The nypl-drm-adobe
     * interfaces should be expanded to return values of an enum type. For now,
     * however, these are the only error codes that can be assigned useful
     * messages.
     */

    if (message.startsWith("E_ACT_TOO_MANY_ACTIVATIONS")) {
      return resources.getString(R.string.settings_login_failed_adobe_device_limit);
    } else if (message.startsWith("E_ADEPT_REQUEST_EXPIRED")) {
      return resources.getString(
          R.string.settings_login_failed_adobe_device_bad_clock);
    } else {
      return resources.getString(R.string.settings_login_failed_device);
    }
  }

  /**
   * Create a new login dialog. The given callback functions will be executed on the UI thread with
   * the results of the login operation. Any strings passed to the callbacks will be properly
   * localized and do not require further processing.
   *
   * @param on_login_success   A function evaluated on login success
   * @param on_login_cancelled A function evaluated on login cancellation
   * @param on_login_failure   A function evaluated on login failure
   * @return A new dialog
   */

  public static LoginDialog newDialog(
      final ProfilesControllerType controller,
      final String text,
      final AccountType account,
      final Runnable on_login_success,
      final Runnable on_login_cancelled,
      final ProcedureType<String> on_login_failure) {

    NullCheck.notNull(controller, "Controller");
    NullCheck.notNull(text, "Text");
    NullCheck.notNull(account, "Account");
    NullCheck.notNull(on_login_success, "Success");
    NullCheck.notNull(on_login_cancelled, "Cancel");
    NullCheck.notNull(on_login_failure, "Failure");

    return account.provider().authentication().accept(
        new OptionVisitorType<AccountProviderAuthenticationDescription, LoginDialog>() {
          @Override
          public LoginDialog none(final None<AccountProviderAuthenticationDescription> none) {
            throw new IllegalArgumentException(
                "Attempted to log in on an account that does not require authentication!");
          }

          @Override
          public LoginDialog some(final Some<AccountProviderAuthenticationDescription> some) {
            final AccountProviderAuthenticationDescription authentication = some.get();

            final Bundle b = new Bundle();
            b.putSerializable(TEXT_ID, text);
            b.putSerializable(PIN_ID, "");
            b.putSerializable(BARCODE_ID, "");
            b.putSerializable(PIN_ALLOWS_LETTERS, authentication.passCodeMayContainLetters());
            b.putSerializable(PIN_LENGTH, authentication.passCodeLength());

            final LoginDialog d = new LoginDialog();
            d.setArguments(b);
            d.setRequiredArguments(
                controller,
                account,
                on_login_success,
                on_login_failure,
                on_login_cancelled);
            return d;
          }
        });
  }

  private void setRequiredArguments(
      final ProfilesControllerType controller,
      final AccountType account,
      final Runnable on_login_success,
      final ProcedureType<String> on_login_failure,
      final Runnable on_login_cancelled) {

    this.controller =
        NullCheck.notNull(controller, "controller");
    this.account =
        NullCheck.notNull(account, "Account");
    this.on_login_success =
        NullCheck.notNull(on_login_success, "On login success");
    this.on_login_failure =
        NullCheck.notNull(on_login_failure, "On login failure");
    this.on_login_cancelled =
        NullCheck.notNull(on_login_cancelled, "On login cancelled");
  }

  private void onAccountLoginFailure(
      final OptionType<Exception> error,
      final String message) {

    final String s = NullCheck.notNull(String.format("login failed: %s", message));
    LogUtilities.errorWithOptionalException(LOG, s, error);

    UIThread.runOnUIThread(() -> {
      this.text.setText(message);
      this.barcode_edit.setEnabled(true);
      this.pin_edit.setEnabled(true);
      this.login.setEnabled(true);
      this.cancel.setEnabled(true);
      this.on_login_failure.call(message);
    });
  }

  @Override
  public void onResume() {
    super.onResume();

    final Resources rr = NullCheck.notNull(this.getResources());
    final int h = (int) rr.getDimension(R.dimen.login_dialog_height);
    final int w = (int) rr.getDimension(R.dimen.login_dialog_width);

    final Dialog dialog = NullCheck.notNull(this.getDialog());
    final Window window = NullCheck.notNull(dialog.getWindow());
    window.setLayout(w, h);
    window.setGravity(Gravity.CENTER);
  }

  @Override
  public void onCancel(final @Nullable DialogInterface dialog) {
    LOG.debug("login aborted");

    UIThread.checkIsUIThread();
    this.on_login_cancelled.run();
  }

  @Override
  public void onCreate(final @Nullable Bundle state) {
    super.onCreate(state);
    this.setStyle(DialogFragment.STYLE_NORMAL, R.style.SimplifiedLoginDialog);
  }

  @Override
  public View onCreateView(
      final @Nullable LayoutInflater inflater_mn,
      final @Nullable ViewGroup container,
      final @Nullable Bundle state) {

    final LayoutInflater inflater = NullCheck.notNull(inflater_mn);
    final Bundle b = this.getArguments();
    final AccountPIN initial_pin =
        AccountPIN.create(b.getString(PIN_ID));
    final AccountBarcode initial_bar =
        AccountBarcode.create(b.getString(BARCODE_ID));
    final String initial_txt =
        NullCheck.notNull(b.getString(TEXT_ID));

    final int pin_length = b.getInt(PIN_LENGTH);
    final boolean pin_allows_letters = b.getBoolean(PIN_ALLOWS_LETTERS);

    final ViewGroup in_layout =
        NullCheck.notNull((ViewGroup) inflater.inflate(
            R.layout.login_dialog, container, false));

    final TextView in_text =
        NullCheck.notNull(in_layout.findViewById(R.id.login_dialog_text));
    final TextView in_barcode_label =
        NullCheck.notNull(in_layout.findViewById(R.id.login_dialog_barcode_text_view));
    final EditText in_barcode_edit =
        NullCheck.notNull(in_layout.findViewById(R.id.login_dialog_barcode_text_edit));
    final TextView in_pin_label =
        NullCheck.notNull(in_layout.findViewById(R.id.login_dialog_pin_text_view));
    final EditText in_pin_edit =
        NullCheck.notNull(in_layout.findViewById(R.id.login_dialog_pin_text_edit));

    if (!pin_allows_letters) {
      in_pin_edit.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
    }
    if (pin_length != 0) {
      in_pin_edit.setFilters(new InputFilter[]{new InputFilter.LengthFilter(pin_length)});
    }

    final Button in_login_button =
        NullCheck.notNull(in_layout.findViewById(R.id.login_dialog_ok));
    final Button in_login_cancel_button =
        NullCheck.notNull(in_layout.findViewById(R.id.login_dialog_cancel));
    final CheckBox in_eula_checkbox =
        NullCheck.notNull(in_layout.findViewById(R.id.eula_checkbox));
    final Button in_login_request_new_code =
        NullCheck.notNull(in_layout.findViewById(R.id.request_new_codes));

    final DocumentStoreType docs = Simplified.getDocumentStore();
    final AuthenticationDocumentType auth_doc = docs.getAuthenticationDocument();
    in_barcode_label.setText(auth_doc.getLabelLoginUserID());
    in_pin_label.setText(auth_doc.getLabelLoginPassword());

    final Resources resources = NullCheck.notNull(this.getResources());
    in_text.setText(initial_txt);
    in_barcode_edit.setText(initial_bar.toString());
    in_pin_edit.setText(initial_pin.toString());

    in_login_button.setEnabled(false);
    in_login_button.setOnClickListener(button -> {
      in_barcode_edit.setEnabled(false);
      in_pin_edit.setEnabled(false);
      in_login_button.setEnabled(false);
      in_login_cancel_button.setEnabled(false);

      final Editable barcode_edit_text = in_barcode_edit.getText();
      final Editable pin_edit_text = in_pin_edit.getText();

      final AccountBarcode barcode =
          AccountBarcode.create(NullCheck.notNull(barcode_edit_text.toString()));
      final AccountPIN pin =
          AccountPIN.create(NullCheck.notNull(pin_edit_text.toString()));
      final AccountAuthenticationProvider provider =
          AccountAuthenticationProvider.create(
              resources.getString(R.string.feature_default_auth_provider_name));

      final AccountAuthenticationCredentials creds =
          AccountAuthenticationCredentials.builder(pin, barcode)
              .setAuthenticationProvider(provider)
              .build();

      final ListeningExecutorService exec = Simplified.getBackgroundTaskExecutor();
      this.login_task = this.controller.profileAccountLogin(this.account.id(), creds);
      this.login_task.addListener(() -> onLoginTaskFinished(login_task), exec);
    });

    in_login_cancel_button.setOnClickListener(v -> {
      this.onCancel(null);
      this.dismiss();
    });

    final boolean request_new_code =
        resources.getBoolean(R.bool.feature_default_auth_provider_request_new_code);

    if (request_new_code) {
      in_login_request_new_code.setOnClickListener(v -> {
        final Intent browser_intent = new Intent(Intent.ACTION_VIEW, Uri.parse(resources.getString(R.string.feature_default_auth_provider_request_new_code_uri)));
        this.startActivity(browser_intent);
      });
    } else {
      in_login_request_new_code.setVisibility(View.GONE);
    }

    final AtomicBoolean in_barcode_empty = new AtomicBoolean(true);
    final AtomicBoolean in_pin_empty = new AtomicBoolean(true);

    final OptionType<EULAType> eula_opt = docs.getEULA();
    if (eula_opt.isSome()) {
      final Some<EULAType> some_eula = (Some<EULAType>) eula_opt;
      final EULAType eula = some_eula.get();

      in_eula_checkbox.setChecked(eula.eulaHasAgreed());
      in_eula_checkbox.setOnCheckedChangeListener((button, checked) -> {

        final boolean barcode_not_empty = !in_barcode_empty.get();
        final boolean pin_not_empty = !in_pin_empty.get();
        final boolean eula_checked = in_eula_checkbox.isChecked();

        eula.eulaSetHasAgreed(checked);
        in_login_button.setEnabled(barcode_not_empty && pin_not_empty && eula_checked);
      });

      if (eula.eulaHasAgreed()) {
        LOG.debug("EULA: agreed");
      } else {
        LOG.debug("EULA: not agreed");
      }
    } else {
      LOG.debug("EULA: unavailable");
    }

    in_barcode_edit.addTextChangedListener(
        new TextWatcher() {
          @Override
          public void afterTextChanged(
              final @Nullable Editable s) {
            // Nothing
          }

          @Override
          public void beforeTextChanged(
              final @Nullable CharSequence s,
              final int start,
              final int count,
              final int after) {
            // Nothing
          }

          @Override
          public void onTextChanged(
              final @Nullable CharSequence s,
              final int start,
              final int before,
              final int count) {

            final boolean barcode_not_empty = !in_barcode_empty.get();
            final boolean pin_not_empty = !in_pin_empty.get();
            final boolean eula_checked = in_eula_checkbox.isChecked();

            in_barcode_empty.set(NullCheck.notNull(s).length() == 0);
            in_login_button.setEnabled(barcode_not_empty && pin_not_empty && eula_checked);
          }
        });

    in_pin_edit.addTextChangedListener(
        new TextWatcher() {
          @Override
          public void afterTextChanged(
              final @Nullable Editable s) {
            // Nothing
          }

          @Override
          public void beforeTextChanged(
              final @Nullable CharSequence s,
              final int start,
              final int count,
              final int after) {
            // Nothing
          }

          @Override
          public void onTextChanged(
              final @Nullable CharSequence s,
              final int start,
              final int before,
              final int count) {

            final boolean barcode_not_empty = !in_barcode_empty.get();
            final boolean pin_not_empty = !in_pin_empty.get();
            final boolean eula_checked = in_eula_checkbox.isChecked();

            in_pin_empty.set(NullCheck.notNull(s).length() == 0);
            in_login_button.setEnabled(barcode_not_empty && pin_not_empty && eula_checked);
          }
        });

    this.barcode_edit = in_barcode_edit;
    this.pin_edit = in_pin_edit;
    this.login = in_login_button;
    this.cancel = in_login_cancel_button;
    this.text = in_text;

    final Dialog d = this.getDialog();
    if (d != null) {
      d.setCanceledOnTouchOutside(true);
    }

    return in_layout;
  }

  private void onLoginTaskFinished(final ListenableFuture<AccountEventLogin> login_task) {

    Assertions.checkPrecondition(
        login_task.isDone() || login_task.isCancelled(),
        "Login task is completed");

    final Resources resources = NullCheck.notNull(this.getResources());

    try {
      final AccountEventLogin event = login_task.get();
      event.matchLogin(this::onLoginTaskSucceeded, this::onLoginTaskFailed);
    } catch (final InterruptedException | ExecutionException e) {
      /// XXX: This is not correct, need a new translation string for local device errors
      this.onAccountLoginFailure(
          Option.some(e), resources.getString(R.string.settings_login_failed_server));
    }
  }

  /**
   * Transform the given login error code to a localized message.
   *
   * @param resources The resources
   * @param error     The error code
   * @return A localized message
   */

  public static String loginErrorCodeToLocalizedMessage(
      final Resources resources,
      final AccountLoginFailed.ErrorCode error) {
    NullCheck.notNull(resources, "Resources");
    NullCheck.notNull(error, "Error");

    switch (error) {
      case ERROR_PROFILE_CONFIGURATION:
        /// XXX: This is not correct, need a new translation string for network errors
        return resources.getString(R.string.settings_login_failed_server);

      case ERROR_NETWORK_EXCEPTION:
        /// XXX: This is not correct, need a new translation string for network errors
        return resources.getString(R.string.settings_login_failed_server);

      case ERROR_CREDENTIALS_INCORRECT:
        return resources.getString(R.string.settings_login_failed_credentials);

      case ERROR_SERVER_ERROR:
        return resources.getString(R.string.settings_login_failed_server);
    }

    throw new UnreachableCodeException();
  }

  private Unit onLoginTaskFailed(final AccountLoginFailed failed) {
    final Resources resources = NullCheck.notNull(this.getResources());
    this.onAccountLoginFailure(failed.exception(),
        loginErrorCodeToLocalizedMessage(resources, failed.errorCode()));
    return Unit.unit();
  }

  private Unit onLoginTaskSucceeded(final AccountLoginSucceeded succeeded) {
    UIThread.runOnUIThread(() -> {
      this.dismiss();
      this.on_login_success.run();
    });
    return Unit.unit();
  }
}
