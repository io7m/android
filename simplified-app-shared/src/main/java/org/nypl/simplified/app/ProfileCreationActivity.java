package org.nypl.simplified.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;

import com.google.common.util.concurrent.ListenableFuture;
import com.io7m.jfunctional.Unit;
import com.io7m.jnull.NullCheck;
import com.io7m.junreachable.UnreachableCodeException;

import org.joda.time.LocalDate;
import org.nypl.simplified.app.utilities.UIThread;
import org.nypl.simplified.books.accounts.AccountProviderCollection;
import org.nypl.simplified.books.controller.ProfilesControllerType;
import org.nypl.simplified.books.core.LogUtilities;
import org.nypl.simplified.books.profiles.ProfileEvent;
import org.nypl.simplified.books.profiles.ProfileEvent.ProfileEventCreationFailed;
import org.slf4j.Logger;

import java.util.concurrent.ExecutionException;

import static org.nypl.simplified.books.profiles.ProfileEvent.ProfileEventCreationFailed.ErrorCode.ERROR_IO;

public final class ProfileCreationActivity extends Activity {

  private static final Logger LOG = LogUtilities.getLog(ProfileCreationActivity.class);

  private Button button;
  private DatePicker date;
  private EditText name;

  public ProfileCreationActivity() {

  }

  @Override
  protected void onCreate(
      final @Nullable Bundle state) {

    this.setTheme(Simplified.getCurrentTheme());
    super.onCreate(state);
    this.setContentView(R.layout.profiles_creation);

    this.button = NullCheck.notNull((Button) this.findViewById(R.id.profileCreationCreate));
    this.button.setEnabled(false);
    this.button.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(final View view) {
        view.setEnabled(false);
        createProfile();
      }
    });

    this.date = NullCheck.notNull((DatePicker) this.findViewById(R.id.profileCreationDateSelection));
    this.name = NullCheck.notNull((EditText) this.findViewById(R.id.profileCreationEditName));
    this.name.addTextChangedListener(new ButtonTextWatcher(button));
  }

  private void onProfileEventCreationFailed(
      final ProfileEventCreationFailed e) {

    LOG.debug("onProfileEventCreationFailed: {}", e);

    UIThread.runOnUIThread(new Runnable() {
      @Override
      public void run() {
        button.setEnabled(true);
        final AlertDialog.Builder alert_builder =
            new AlertDialog.Builder(ProfileCreationActivity.this);
        alert_builder.setMessage(messageForErrorCode(e.errorCode()));
        alert_builder.setCancelable(true);

        final AlertDialog alert = alert_builder.create();
        alert.show();
      }
    });
  }

  private int messageForErrorCode(
      final ProfileEventCreationFailed.ErrorCode code) {
    switch (code) {
      case ERROR_DISPLAY_NAME_ALREADY_USED:
        return R.string.profiles_error_name_already_used;
      case ERROR_IO:
        return R.string.profiles_error_io;
    }
    throw new UnreachableCodeException();
  }

  private void onProfileEventCreated(
      final ProfileEvent.ProfileEventCreated e) {

    LOG.debug("onProfileEventCreated: {}", e);

    UIThread.runOnUIThread(new Runnable() {
      @Override
      public void run() {
        openSelectionActivity();
      }
    });
  }

  private void onProfileEvent(
      final ProfileEvent e) {

    LOG.debug("onProfileEvent: {}", e);

    e.matchEvent(new ProfileEvent.MatcherType<Unit, RuntimeException>() {
      @Override
      public Unit onProfileEventCreated(
          final ProfileEvent.ProfileEventCreated e)
          throws RuntimeException {
        ProfileCreationActivity.this.onProfileEventCreated(e);
        return Unit.unit();
      }

      @Override
      public Unit onProfileEventCreationFailed(
          final ProfileEventCreationFailed e)
          throws RuntimeException {
        ProfileCreationActivity.this.onProfileEventCreationFailed(e);
        return Unit.unit();
      }
    });
  }

  private void openSelectionActivity() {
    final Intent i = new Intent(this, ProfileSelectionActivity.class);
    this.startActivity(i);
    this.finish();
  }

  private void createProfile() {
    final String name_text = name.getText().toString().trim();
    LOG.debug("name: {}", name_text);
    LOG.debug("date: {}-{}-{}", date.getYear(), date.getMonth(), date.getDayOfMonth());

    final AccountProviderCollection providers = Simplified.getAccountProviders();
    final ProfilesControllerType profiles = Simplified.getProfilesController();

    final ListenableFuture<ProfileEvent> task =
        profiles.profileCreate(
            providers.providerDefault(),
            name_text,
            new LocalDate(date.getYear(), date.getMonth(), date.getDayOfMonth()));

    task.addListener(new Runnable() {
      @Override
      public void run() {
        try {
          onProfileEvent(task.get());
        } catch (final InterruptedException | ExecutionException e) {
          LOG.error("profile creation failed: ", e);
          onProfileEventCreationFailed(ProfileEventCreationFailed.of(name_text, ERROR_IO));
        }
      }
    }, Simplified.getBackgroundTaskExecutor());
  }

  /**
   * A text watcher that enables and disables a button based on whether or not the
   * text field is empty.
   */

  private static final class ButtonTextWatcher implements TextWatcher {

    private final Button button;

    ButtonTextWatcher(final Button in_button) {
      this.button = NullCheck.notNull(in_button, "Button");
    }

    @Override
    public void beforeTextChanged(
        final CharSequence text,
        final int i,
        final int i1,
        final int i2) {
    }

    @Override
    public void onTextChanged(
        final CharSequence text,
        final int i,
        final int i1,
        final int i2) {
      this.button.setEnabled(!text.toString().trim().isEmpty());
    }

    @Override
    public void afterTextChanged(
        final Editable editable) {

    }
  }
}
