package org.nypl.simplified.books.profiles;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.io7m.jfunctional.Option;
import com.io7m.jfunctional.OptionType;
import com.io7m.jnull.NullCheck;
import com.io7m.jnull.Nullable;
import com.io7m.junreachable.UnimplementedCodeException;

import org.nypl.simplified.assertions.Assertions;
import org.nypl.simplified.books.accounts.AccountID;
import org.nypl.simplified.books.accounts.AccountProvider;
import org.nypl.simplified.books.accounts.AccountType;
import org.nypl.simplified.books.accounts.AccountsDatabase;
import org.nypl.simplified.books.accounts.AccountsDatabaseException;
import org.nypl.simplified.books.accounts.AccountsDatabaseType;
import org.nypl.simplified.books.core.LogUtilities;
import org.nypl.simplified.files.FileUtilities;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * The default implementation of the {@link ProfilesDatabaseType} interface.
 */

public final class ProfilesDatabase implements ProfilesDatabaseType {

  private static final Logger LOG = LogUtilities.getLog(ProfilesDatabase.class);

  private final File directory;
  private final SortedMap<ProfileID, Profile> profiles;
  private final SortedMap<ProfileID, ProfileType> profiles_read;
  private @Nullable ProfileID profile_current;

  private ProfilesDatabase(
      File directory,
      SortedMap<ProfileID, Profile> profiles) {
    this.directory = NullCheck.notNull(directory, "directory");
    this.profiles = NullCheck.notNull(profiles, "profiles");
    this.profiles_read = castMap(Collections.unmodifiableSortedMap(this.profiles));
    this.profile_current = null;

    for (Profile profile : this.profiles.values()) {
      profile.setOwner(this);
    }
  }

  /**
   * Perform an unchecked (but safe) cast of the given map type. The cast is safe because
   * {@code V <: VB}.
   */

  @SuppressWarnings("unchecked")
  private static <K, VB, V extends VB> SortedMap<K, VB> castMap(SortedMap<K, V> m) {
    return (SortedMap<K, VB>) m;
  }

  /**
   * Open a profile database from the given directory, creating a new database if one does not exist.
   *
   * @param directory The directory
   * @return A profile database
   * @throws ProfileDatabaseException If any errors occurred whilst trying to open the database
   */

  public static ProfilesDatabaseType open(
      File directory)
      throws ProfileDatabaseException {

    NullCheck.notNull(directory, "Directory");

    LOG.debug("opening profile database: {}", directory);

    final SortedMap<ProfileID, Profile> profiles = new TreeMap<>();
    final ObjectMapper jom = new ObjectMapper();

    final List<Exception> errors = new ArrayList<>();
    if (!directory.exists()) {
      directory.mkdirs();
    }

    if (!directory.isDirectory()) {
      errors.add(new IOException("Not a directory: " + directory));
    }

    final String[] profile_dirs = directory.list();
    if (profile_dirs != null) {
      for (int index = 0; index < profile_dirs.length; ++index) {
        String profile_id_name = profile_dirs[index];
        LOG.debug("opening profile: {}/{}", directory, profile_id_name);

        final int id;
        try {
          id = Integer.parseInt(profile_id_name);
        } catch (NumberFormatException e) {
          errors.add(new IOException("Could not parse directory name as profile ID", e));
          continue;
        }

        final File profile_dir = new File(directory, profile_id_name);
        final File profile_file = new File(profile_dir, "profile.json");
        final ProfileDescription desc;

        try {
          desc = ProfileDescriptionJSON.deserializeFromFile(jom, profile_file);
        } catch (IOException e) {
          errors.add(new IOException("Could not parse profile: " + profile_file, e));
          continue;
        }

        final ProfileID profile_id = ProfileID.create(id);
        final Profile profile = new Profile(null, profile_id, profile_dir, desc);
        final File profile_accounts = new File(profile_dir, "accounts");

        try {
          AccountsDatabaseType accounts = AccountsDatabase.open(profile, profile_accounts);
          profile.setAccounts(accounts);
        } catch (AccountsDatabaseException e) {
          errors.add(e);
          continue;
        }

        profiles.put(profile_id, profile);
      }
    }

    if (!errors.isEmpty()) {
      throw new ProfileDatabaseException(
          "One or more errors occurred whilst trying to open the profile database.", errors);
    }

    return new ProfilesDatabase(directory, profiles);
  }

  @Override
  public File directory() {
    return this.directory;
  }

  @Override
  public SortedMap<ProfileID, ProfileType> profiles() {
    return this.profiles_read;
  }

  @Override
  public ProfileType createProfile(
      AccountProvider account_provider,
      String display_name)
      throws ProfileDatabaseException {

    NullCheck.notNull(account_provider, "Provider");
    NullCheck.notNull(display_name, "Display name");

    OptionType<ProfileType> existing = findProfileWithDisplayName(display_name);
    if (existing.isSome()) {
      throw new ProfileDatabaseException(
          "Display name is already used by an existing profile",
          Collections.<Exception>emptyList());
    }

    final ProfileID next;
    if (!this.profiles.isEmpty()) {
      next = ProfileID.create(this.profiles.lastKey().id() + 1);
    } else {
      next = ProfileID.create(0);
    }

    Assertions.checkInvariant(
        !this.profiles.containsKey(next),
        "Profile ID %s cannot have been used", next);

    try {
      final File profile_dir =
          new File(this.directory, Integer.toString(next.id()));
      final File profile_file =
          new File(profile_dir, "profile.json");
      final File profile_file_tmp =
          new File(profile_dir, "profile.json.tmp");
      final File profile_accounts =
          new File(profile_dir, "accounts");

      // Ignore the return value, writing the file will raise an error if this call failed
      profile_dir.mkdirs();
      ProfileDescription desc = ProfileDescription.create(display_name);
      FileUtilities.fileWriteUTF8Atomically(
          profile_file,
          profile_file_tmp,
          ProfileDescriptionJSON.serializeToString(new ObjectMapper(), desc));

      Profile profile = new Profile(this, next, profile_dir, desc);
      try {
        AccountsDatabaseType accounts = AccountsDatabase.open(profile, profile_accounts);
        profile.setAccounts(accounts);

        this.profiles.put(next, profile);
        return profile;
      } catch (AccountsDatabaseException e) {
        throw new ProfileDatabaseException(
            "Could not initialize accounts database",
            Collections.singletonList((Exception) e));
      }
    } catch (IOException e) {
      throw new ProfileDatabaseException(
          "Could not write profile data", Collections.singletonList((Exception) e));
    }
  }

  @Override
  public OptionType<ProfileType> findProfileWithDisplayName(
      String display_name) {
    NullCheck.notNull(display_name, "Display name");

    for (Profile profile : this.profiles.values()) {
      if (profile.displayName().equals(display_name)) {
        return Option.some((ProfileType) profile);
      }
    }
    return Option.none();
  }

  @Override
  public void setProfileCurrent(
      ProfileID profile)
      throws ProfileDatabaseException {
    NullCheck.notNull(profile, "Profile");

    if (!profiles.containsKey(profile)) {
      throw new ProfileDatabaseException(
          "Profile does not exist",
          Collections.<Exception>emptyList());
    }

    this.profile_current = profile;
  }

  @Override
  public OptionType<ProfileID> currentProfile() {
    return Option.of(this.profile_current);
  }

  private static final class Profile implements ProfileType {

    private final ProfileDescription description;
    private final ProfileID id;
    private final File directory;
    private ProfilesDatabase owner;
    private AccountsDatabaseType accounts;

    private Profile(
        final @Nullable ProfilesDatabase in_owner,
        final ProfileID in_id,
        final File in_directory,
        final ProfileDescription in_description) {
      this.owner = in_owner;
      this.id = NullCheck.notNull(in_id, "id");
      this.directory = NullCheck.notNull(in_directory, "directory");
      this.description = NullCheck.notNull(in_description, "description");
    }

    private void setOwner(ProfilesDatabase owner) {
      this.owner = NullCheck.notNull(owner, "Owner");
    }

    @Override
    public ProfileID id() {
      return this.id;
    }

    @Override
    public File directory() {
      return this.directory;
    }

    @Override
    public String displayName() {
      return this.description.displayName();
    }

    @Override
    public boolean isCurrent() {
      NullCheck.notNull(this.owner, "Owner");
      return this.id.equals(this.owner.profile_current);
    }

    @Override
    public AccountID accountCurrent() {
      throw new UnimplementedCodeException();
    }

    @Override
    public SortedMap<AccountID, AccountType> accounts() {
      return this.accounts.accounts();
    }

    public void setAccounts(
        final AccountsDatabaseType accounts) {
      this.accounts = NullCheck.notNull(accounts, "Accounts");
    }
  }
}
