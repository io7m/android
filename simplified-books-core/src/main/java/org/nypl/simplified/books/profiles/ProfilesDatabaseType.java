package org.nypl.simplified.books.profiles;

import com.io7m.jfunctional.OptionType;

import org.nypl.simplified.books.accounts.AccountProvider;

import java.io.File;
import java.util.SortedMap;

/**
 * <p>The interface exposed by the profiles database.</p>
 * <p>
 * A profile database stores all of the profiles currently known to
 * the application. It also stores a reference to the current profile.
 * Exactly one profile may be current at any given time. It is the
 * responsibility of the application to have the user select a profile
 * when the application starts, or to create a select a default
 * profile if the application does not require profile functionality.
 * </p>
 */

public interface ProfilesDatabaseType {

  /**
   * @return The directory containing the on-disk profiles database
   */

  File directory();

  /**
   * @return A read-only view of the current profiles
   */

  SortedMap<ProfileID, ProfileType> profiles();

  /**
   * Create a profile using the given account provider and display name.
   *
   * @param account_provider The account provider for the default account
   * @param display_name     The display name
   * @return A newly created profile
   * @throws ProfileDatabaseException On profile creation errors
   */

  ProfileType createProfile(
      AccountProvider account_provider,
      String display_name)
      throws ProfileDatabaseException;

  /**
   * Find the profile with the given display name, if any.
   *
   * @param display_name The display name
   * @return The profile with the display name, or nothing if one does not exist
   */

  OptionType<ProfileType> findProfileWithDisplayName(
      String display_name);

  /**
   * Set the profile with the given ID as the current profile.
   *
   * @param profile The profile ID
   * @throws ProfileDatabaseException If no profile exists with the given ID
   */

  void setProfileCurrent(
      ProfileID profile)
      throws ProfileDatabaseException;

  /**
   * @return The current profile, if any
   */

  OptionType<ProfileID> currentProfile();
}
