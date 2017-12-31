package org.nypl.simplified.books.controller;

import com.io7m.jnull.NullCheck;

import org.joda.time.LocalDate;
import org.nypl.simplified.books.accounts.AccountProvider;
import org.nypl.simplified.books.profiles.ProfileDatabaseException;
import org.nypl.simplified.books.profiles.ProfileEvent;
import org.nypl.simplified.books.profiles.ProfileType;
import org.nypl.simplified.books.profiles.ProfilesDatabaseType;
import org.nypl.simplified.observable.ObservableType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.Callable;

import static org.nypl.simplified.books.profiles.ProfileEvent.ProfileEventCreated;
import static org.nypl.simplified.books.profiles.ProfileEvent.ProfileEventCreationFailed;
import static org.nypl.simplified.books.profiles.ProfileEvent.ProfileEventCreationFailed.ErrorCode.ERROR_DISPLAY_NAME_ALREADY_USED;
import static org.nypl.simplified.books.profiles.ProfileEvent.ProfileEventCreationFailed.ErrorCode.ERROR_IO;

final class ProfileCreationTask implements Callable<ProfileEvent> {

  private static final Logger LOG = LoggerFactory.getLogger(ProfileCreationTask.class);

  private final ProfilesDatabaseType profiles;
  private final ObservableType<ProfileEvent> profile_events;
  private final String display_name;
  private final LocalDate date;
  private final AccountProvider account_provider;

  ProfileCreationTask(
      final ProfilesDatabaseType in_profiles,
      final ObservableType<ProfileEvent> in_profile_events,
      final AccountProvider in_account_provider,
      final String in_display_name,
      final LocalDate in_date) {

    this.profiles =
        NullCheck.notNull(in_profiles, "Profiles");
    this.profile_events =
        NullCheck.notNull(in_profile_events, "Profile events");
    this.account_provider =
        NullCheck.notNull(in_account_provider, "Account provider");
    this.display_name =
        NullCheck.notNull(in_display_name, "Display name");
    this.date =
        NullCheck.notNull(in_date, "Date");
  }

  @Override
  public ProfileEvent call() {

    if (profiles.findProfileWithDisplayName(this.display_name).isSome()) {
      return ProfileEventCreationFailed.of(this.display_name, ERROR_DISPLAY_NAME_ALREADY_USED);
    }

    try {
      final ProfileType profile =
          this.profiles.createProfile(this.account_provider, this.display_name);

      profile.preferencesUpdate(
          profile.preferences()
              .toBuilder()
              .setDateOfBirth(this.date)
              .build());

      final ProfileEventCreated event =
          ProfileEventCreated.of(this.display_name, profile.id());
      this.profile_events.send(event);
      return event;
    } catch (final ProfileDatabaseException | IOException e) {
      LOG.error("profile creation failed: ", e);
      final ProfileEventCreationFailed event =
          ProfileEventCreationFailed.of(this.display_name, ERROR_IO);
      this.profile_events.send(event);
      return event;
    }
  }
}
