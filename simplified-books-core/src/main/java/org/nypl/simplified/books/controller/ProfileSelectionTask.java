package org.nypl.simplified.books.controller;

import com.io7m.jfunctional.Unit;
import com.io7m.jnull.NullCheck;

import org.nypl.simplified.books.profiles.ProfileID;
import org.nypl.simplified.books.profiles.ProfilesDatabaseType;

import java.util.concurrent.Callable;

final class ProfileSelectionTask implements Callable<Unit> {

  private final ProfilesDatabaseType profiles;
  private final ProfileID profile_id;

  ProfileSelectionTask(
      final ProfilesDatabaseType in_profiles,
      final ProfileID in_id) {

    this.profiles =
        NullCheck.notNull(in_profiles, "Profiles");
    this.profile_id =
        NullCheck.notNull(in_id, "ID");
  }

  @Override
  public Unit call() {
    this.profiles.setProfileCurrent(this.profile_id);
    return Unit.unit();
  }
}
