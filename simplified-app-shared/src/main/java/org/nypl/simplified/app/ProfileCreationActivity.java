package org.nypl.simplified.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.io7m.jfunctional.Option;
import com.io7m.jfunctional.Unit;
import com.io7m.jnull.NullCheck;
import com.io7m.junreachable.UnreachableCodeException;

import org.joda.time.LocalDate;
import org.nypl.simplified.app.utilities.ErrorDialogUtilities;
import org.nypl.simplified.app.utilities.UIThread;
import org.nypl.simplified.books.accounts.AccountProviderCollection;
import org.nypl.simplified.books.controller.ProfilesControllerType;
import org.nypl.simplified.books.core.LogUtilities;
import org.nypl.simplified.books.profiles.ProfileCreationEvent;
import org.nypl.simplified.books.profiles.ProfileCreationEvent.ProfileCreationFailed;
import org.nypl.simplified.books.profiles.ProfileCreationEvent.ProfileCreationSucceeded;
import org.nypl.simplified.books.profiles.ProfileEvent;
import org.nypl.simplified.datepicker.DatePicker;
import org.slf4j.Logger;

import static org.nypl.simplified.app.Simplified.WantActionBar.WANT_NO_ACTION_BAR;
import static org.nypl.simplified.books.profiles.ProfileCreationEvent.ProfileCreationFailed.ErrorCode.ERROR_GENERAL;

/**
 * An activity that allows for the creation of profiles.
 */

public final class ProfileCreationActivity extends Activity {

  private static final Logger LOG = LogUtilities.getLog(ProfileCreationActivity.class);

  private Button button;
  private DatePicker date;
  private EditText name;

  public ProfileCreationActivity() {

  }

  @Override
  protected void onCreate(final @Nullable Bundle state) {

    this.setTheme(Simplified.getCurrentTheme(WANT_NO_ACTION_BAR));
    super.onCreate(state);
    this.setContentView(R.layout.profiles_creation);

    this.button = NullCheck.notNull(this.findViewById(R.id.profileCreationCreate));
    this.button.setEnabled(false);
    this.button.setOnClickListener(view -> {
      view.setEnabled(false);
      createProfile();
    });

    this.date = NullCheck.notNull(this.findViewById(R.id.profileCreationDateSelection));
    this.name = NullCheck.notNull(this.findViewById(R.id.profileCreationEditName));
    this.name.addTextChangedListener(new ButtonTextWatcher(button));
  }

  private Unit onProfileCreationFailed(final ProfileCreationFailed e) {
    LOG.debug("onProfileCreationFailed: {}", e);

    ErrorDialogUtilities.showErrorWithRunnable(
        this,
        LOG,
        this.getResources().getString(messageForErrorCode(e.errorCode())),
        null,
        () -> this.button.setEnabled(true));

    return Unit.unit();
  }

  private int messageForErrorCode(final ProfileCreationFailed.ErrorCode code) {
    switch (code) {
      case ERROR_DISPLAY_NAME_ALREADY_USED:
        return R.string.profiles_creation_error_name_already_used;
      case ERROR_GENERAL:
        return R.string.profiles_creation_error_general;
    }
    throw new UnreachableCodeException();
  }

  private Unit onProfileCreationSucceeded(final ProfileCreationSucceeded e) {
    LOG.debug("onProfileCreationSucceeded: {}", e);
    UIThread.runOnUIThread(this::openSelectionActivity);
    return Unit.unit();
  }

  private Unit onProfileEvent(final ProfileEvent event) {
    LOG.debug("onProfileEvent: {}", event);
    if (event instanceof ProfileCreationEvent) {
      final ProfileCreationEvent event_create = (ProfileCreationEvent) event;
      return event_create.matchCreation(
          this::onProfileCreationSucceeded,
          this::onProfileCreationFailed);
    }
    return Unit.unit();
  }

  private void openSelectionActivity() {
    final Intent i = new Intent(this, ProfileSelectionActivity.class);
    this.startActivity(i);
    this.finish();
  }

  private void createProfile() {
    final String name_text = name.getText().toString().trim();
    final LocalDate date_value = this.date.getDate();
    LOG.debug("name: {}", name_text);
    LOG.debug("date: {}", date_value);

    final AccountProviderCollection providers = Simplified.getAccountProviders();
    final ProfilesControllerType profiles = Simplified.getProfilesController();
    final ListeningExecutorService exec = Simplified.getBackgroundTaskExecutor();

    final ListenableFuture<ProfileCreationEvent> task =
        profiles.profileCreate(
            providers.providerDefault(),
            name_text,
            date_value);

    FluentFuture.from(task)
        .catching(Exception.class, e -> ProfileCreationFailed.of(name_text, ERROR_GENERAL, Option.some(e)), exec)
        .transform(this::onProfileEvent, exec);
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
    public void afterTextChanged(final Editable editable) {

    }
  }
}
