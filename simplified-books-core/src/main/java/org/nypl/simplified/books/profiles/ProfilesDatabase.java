package org.nypl.simplified.books.profiles;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.io7m.jfunctional.Option;
import com.io7m.jfunctional.OptionType;
import com.io7m.jfunctional.Unit;
import com.io7m.jnull.NullCheck;
import com.io7m.jnull.Nullable;
import com.io7m.junreachable.UnreachableCodeException;

import org.nypl.simplified.assertions.Assertions;
import org.nypl.simplified.books.accounts.AccountID;
import org.nypl.simplified.books.accounts.AccountProvider;
import org.nypl.simplified.books.accounts.AccountProviderCollection;
import org.nypl.simplified.books.accounts.AccountProviderCollectionType;
import org.nypl.simplified.books.accounts.AccountType;
import org.nypl.simplified.books.accounts.AccountsDatabaseException;
import org.nypl.simplified.books.accounts.AccountsDatabaseFactoryType;
import org.nypl.simplified.books.accounts.AccountsDatabaseNonexistentException;
import org.nypl.simplified.books.accounts.AccountsDatabaseType;
import org.nypl.simplified.books.core.LogUtilities;
import org.nypl.simplified.files.FileLocking;
import org.nypl.simplified.files.FileUtilities;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentSkipListMap;

import javax.annotation.concurrent.GuardedBy;

import static org.nypl.simplified.books.profiles.ProfilesDatabaseType.AnonymousProfileEnabled.ANONYMOUS_PROFILE_DISABLED;
import static org.nypl.simplified.books.profiles.ProfilesDatabaseType.AnonymousProfileEnabled.ANONYMOUS_PROFILE_ENABLED;

/**
 * The default implementation of the {@link ProfilesDatabaseType} interface.
 */

public final class ProfilesDatabase implements ProfilesDatabaseType {

  private static final Logger LOG = LogUtilities.getLog(ProfilesDatabase.class);

  private static final ProfileID ANONYMOUS_PROFILE_ID = ProfileID.create(0);

  private final File directory;
  private final ConcurrentSkipListMap<ProfileID, Profile> profiles;
  private final SortedMap<ProfileID, ProfileType> profiles_read;
  private final AnonymousProfileEnabled profile_anon_enabled;
  private final AccountsDatabaseFactoryType accounts_databases;
  private final Object profile_current_lock;
  private final AccountProviderCollectionType account_providers;
  private @GuardedBy("profile_current_lock") ProfileID profile_current;

  private ProfilesDatabase(
      final AccountProviderCollectionType account_providers,
      final AccountsDatabaseFactoryType accounts_databases,
      final File directory,
      final ConcurrentSkipListMap<ProfileID, Profile> profiles,
      final AnonymousProfileEnabled anonymous_enabled) {

    this.account_providers =
        NullCheck.notNull(account_providers, "Account providers");
    this.accounts_databases =
        NullCheck.notNull(accounts_databases, "Accounts databases");
    this.directory =
        NullCheck.notNull(directory, "directory");
    this.profiles =
        NullCheck.notNull(profiles, "profiles");
    this.profile_anon_enabled =
        NullCheck.notNull(anonymous_enabled, "Anonymous enabled");

    this.profiles_read = castMap(Collections.unmodifiableSortedMap(this.profiles));
    this.profile_current_lock = new Object();
    this.profile_current = null;

    for (final Profile profile : this.profiles.values()) {
      profile.setOwner(this);
    }
  }

  /**
   * Perform an unchecked (but safe) cast of the given map type. The cast is safe because
   * {@code V <: VB}.
   */

  @SuppressWarnings("unchecked")
  private static <K, VB, V extends VB> SortedMap<K, VB> castMap(final SortedMap<K, V> m) {
    return (SortedMap<K, VB>) m;
  }

  /**
   * Open a profile database from the given directory, creating a new database if one does not
   * exist. The anonymous account will not be enabled, and will be ignored even if one is present
   * in the on-disk database.
   *
   * @param account_providers The available account providers
   * @param directory         The directory
   * @return A profile database
   * @throws ProfileDatabaseException If any errors occurred whilst trying to open the database
   */

  public static ProfilesDatabaseType openWithAnonymousAccountDisabled(
      final AccountProviderCollectionType account_providers,
      final AccountsDatabaseFactoryType accounts_databases,
      final File directory)
      throws ProfileDatabaseException {

    NullCheck.notNull(account_providers, "Account providers");
    NullCheck.notNull(accounts_databases, "Accounts databases");
    NullCheck.notNull(directory, "Directory");

    LOG.debug("opening profile database: {}", directory);

    final ConcurrentSkipListMap<ProfileID, Profile> profiles = new ConcurrentSkipListMap<>();
    final ObjectMapper jom = new ObjectMapper();

    final List<Exception> errors = new ArrayList<>();
    openAllProfiles(account_providers, accounts_databases, directory, profiles, jom, errors);
    profiles.remove(ANONYMOUS_PROFILE_ID);

    if (!errors.isEmpty()) {
      for (final Exception e : errors) {
        LOG.error("error during profile database open: ", e);
      }

      throw new ProfileDatabaseOpenException(
          "One or more errors occurred whilst trying to open the profile database.",
          errors);
    }

    return new ProfilesDatabase(
        account_providers, accounts_databases, directory, profiles, ANONYMOUS_PROFILE_DISABLED);
  }

  private static void openAllProfiles(
      final AccountProviderCollectionType account_providers,
      final AccountsDatabaseFactoryType accounts_databases,
      final File directory,
      final SortedMap<ProfileID, Profile> profiles,
      final ObjectMapper jom,
      final List<Exception> errors) {

    if (!directory.exists()) {
      directory.mkdirs();
    }

    if (!directory.isDirectory()) {
      errors.add(new IOException("Not a directory: " + directory));
    }

    final String[] profile_dirs = directory.list();
    if (profile_dirs != null) {
      for (final String profile_id_name : profile_dirs) {
        LOG.debug("opening profile: {}/{}", directory, profile_id_name);
        final Profile profile =
            openOneProfile(
                account_providers, accounts_databases, jom, directory, errors, profile_id_name);
        if (profile == null) {
          continue;
        }
        profiles.put(profile.id, profile);
      }
    }
  }

  /**
   * Open a profile database from the given directory, creating a new database if one does not exist.
   * The anonymous account will be enabled and will use the given account provider as the default
   * account.
   *
   * @param account_providers The available account providers
   * @param account_provider  The account provider that will be used for the anonymous account
   * @param directory         The directory
   * @return A profile database
   * @throws ProfileDatabaseException If any errors occurred whilst trying to open the database
   */

  public static ProfilesDatabaseType openWithAnonymousAccountEnabled(
      final AccountProviderCollectionType account_providers,
      final AccountsDatabaseFactoryType accounts_databases,
      final AccountProvider account_provider,
      final File directory)
      throws ProfileDatabaseException {

    NullCheck.notNull(account_providers, "Account providers");
    NullCheck.notNull(accounts_databases, "Accounts databases");
    NullCheck.notNull(account_provider, "Account provider");
    NullCheck.notNull(directory, "Directory");

    LOG.debug("opening profile database: {}", directory);

    final ConcurrentSkipListMap<ProfileID, Profile> profiles = new ConcurrentSkipListMap<>();
    final ObjectMapper jom = new ObjectMapper();

    final List<Exception> errors = new ArrayList<>();
    openAllProfiles(account_providers, accounts_databases, directory, profiles, jom, errors);

    if (!profiles.containsKey(ANONYMOUS_PROFILE_ID)) {
      final Profile anon = createProfileActual(
          account_providers, accounts_databases, account_provider, directory, "", ANONYMOUS_PROFILE_ID);
      profiles.put(ANONYMOUS_PROFILE_ID, anon);
    }

    if (!errors.isEmpty()) {
      throw new ProfileDatabaseOpenException(
          "One or more errors occurred whilst trying to open the profile database.", errors);
    }

    final ProfilesDatabase database =
        new ProfilesDatabase(
            account_providers,
            accounts_databases,
            directory,
            profiles,
            ANONYMOUS_PROFILE_ENABLED);

    database.setCurrentProfile(ANONYMOUS_PROFILE_ID);
    return database;
  }

  private static @Nullable
  Profile openOneProfile(
      final AccountProviderCollectionType account_providers,
      final AccountsDatabaseFactoryType accounts_databases,
      final ObjectMapper jom,
      final File directory,
      final List<Exception> errors,
      final String profile_id_name) {

    final int id;
    try {
      id = Integer.parseInt(profile_id_name);
    } catch (final NumberFormatException e) {
      errors.add(new IOException("Could not parse directory name as profile ID", e));
      return null;
    }

    final File profile_dir = new File(directory, profile_id_name);
    final File profile_file = new File(profile_dir, "profile.json");
    final ProfileDescription desc;

    try {
      desc = ProfileDescriptionJSON.deserializeFromFile(jom, profile_file);
    } catch (final IOException e) {
      errors.add(new IOException("Could not parse profile: " + profile_file, e));
      return null;
    }

    final ProfileID profile_id = ProfileID.create(id);
    final File profile_accounts_dir = new File(profile_dir, "accounts");

    try {
      final AccountsDatabaseType accounts =
          accounts_databases.openDatabase(account_providers, profile_accounts_dir);
      final AccountType account =
          accounts.accounts().get(accounts.accounts().firstKey());

      return new Profile(null, profile_id, profile_dir, desc, accounts, account);
    } catch (final AccountsDatabaseException e) {
      errors.add(e);
      return null;
    }
  }

  @Override
  public AnonymousProfileEnabled anonymousProfileEnabled() {
    return this.profile_anon_enabled;
  }

  @Override
  public ProfileType anonymousProfile() throws ProfileAnonymousDisabledException {
    switch (this.profile_anon_enabled) {
      case ANONYMOUS_PROFILE_ENABLED:
        return this.profiles.get(ANONYMOUS_PROFILE_ID);
      case ANONYMOUS_PROFILE_DISABLED:
        throw new ProfileAnonymousDisabledException("The anonymous profile is not enabled");
    }
    throw new UnreachableCodeException();
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
      final AccountProvider account_provider,
      final String display_name)
      throws ProfileDatabaseException {

    NullCheck.notNull(account_provider, "Provider");
    NullCheck.notNull(display_name, "Display name");

    if (display_name.isEmpty()) {
      throw new ProfileCreateInvalidException("Display name cannot be empty");
    }

    final OptionType<ProfileType> existing = findProfileWithDisplayName(display_name);
    if (existing.isSome()) {
      throw new ProfileCreateDuplicateException("Display name is already used by an existing profile");
    }

    final ProfileID next;
    if (!this.profiles.isEmpty()) {
      next = ProfileID.create(this.profiles.lastKey().id() + 1);
    } else {
      next = ProfileID.create(1);
    }

    Assertions.checkInvariant(
        !this.profiles.containsKey(next),
        "Profile ID %s cannot have been used", next);

    final Profile profile = createProfileActual(
        this.account_providers,
        this.accounts_databases,
        account_provider,
        this.directory,
        display_name,
        next);

    this.profiles.put(profile.id(), profile);
    profile.setOwner(this);
    return profile;
  }

  /**
   * Do the actual work of creating the account.
   *
   * @param account_providers  The available account providers
   * @param accounts_databases A factory for account databases
   * @param account_provider   The account provider that will be used for the default account
   * @param directory          The profile directory
   * @param display_name       The display name for the account
   * @param id                 The account ID
   */

  private static Profile createProfileActual(
      final AccountProviderCollectionType account_providers,
      final AccountsDatabaseFactoryType accounts_databases,
      final AccountProvider account_provider,
      final File directory,
      final String display_name,
      final ProfileID id)
      throws ProfileDatabaseException {

    try {
      final File profile_dir =
          new File(directory, Integer.toString(id.id()));
      final File profile_accounts_dir =
          new File(profile_dir, "accounts");

      final ProfilePreferences prefs =
          ProfilePreferences.builder()
              .build();

      final ProfileDescription desc =
          ProfileDescription.builder(display_name, prefs)
              .build();

      try {
        final AccountsDatabaseType accounts =
            accounts_databases.openDatabase(account_providers, profile_accounts_dir);
        final AccountType account =
            accounts.createAccount(account_provider);

        final Profile profile =
            new Profile(null, id, profile_dir, desc, accounts, account);

        writeDescription(profile_dir, desc);
        return profile;
      } catch (final AccountsDatabaseException e) {
        throw new ProfileDatabaseAccountsException("Could not initialize accounts database", e);
      }
    } catch (final IOException e) {
      throw new ProfileDatabaseIOException("Could not write profile data", e);
    }
  }

  @Override
  public OptionType<ProfileType> findProfileWithDisplayName(
      final String display_name) {

    NullCheck.notNull(display_name, "Display name");

    for (final Profile profile : this.profiles.values()) {
      if (profile.displayName().equals(display_name)) {
        return Option.some(profile);
      }
    }
    return Option.none();
  }

  @Override
  public void setProfileCurrent(
      final ProfileID profile)
      throws ProfileNonexistentException, ProfileAnonymousEnabledException {

    NullCheck.notNull(profile, "Profile");

    switch (this.profile_anon_enabled) {
      case ANONYMOUS_PROFILE_ENABLED: {
        throw new ProfileAnonymousEnabledException(
            "The anonymous profile is enabled; cannot set the current profile");
      }
      case ANONYMOUS_PROFILE_DISABLED: {
        if (!profiles.containsKey(profile)) {
          throw new ProfileNonexistentException("Profile does not exist");
        }

        setCurrentProfile(profile);
        break;
      }
    }
  }

  private void setCurrentProfile(final ProfileID profile) {
    LOG.debug("setCurrentProfile: {}", profile);
    synchronized (this.profile_current_lock) {
      this.profile_current = NullCheck.notNull(profile, "Profile");
    }
  }

  @Override
  public OptionType<ProfileType> currentProfile() {
    synchronized (this.profile_current_lock) {
      switch (this.profile_anon_enabled) {
        case ANONYMOUS_PROFILE_ENABLED: {
          try {
            return Option.some(this.anonymousProfile());
          } catch (final ProfileAnonymousDisabledException e) {
            throw new UnreachableCodeException(e);
          }
        }
        case ANONYMOUS_PROFILE_DISABLED: {
          return Option.of(this.profile_current).map(profiles::get);
        }
      }

      throw new UnreachableCodeException();
    }
  }

  @Override
  public ProfileType currentProfileUnsafe() throws ProfileNoneCurrentException {
    return currentProfileGet();
  }

  private Profile currentProfileGet() throws ProfileNoneCurrentException {
    synchronized (this.profile_current_lock) {
      final ProfileID id = this.profile_current;
      if (id != null) {
        return this.profiles.get(NullCheck.notNull(id, "ID"));
      }
      throw new ProfileNoneCurrentException("No profile is current");
    }
  }

  private static final class Profile implements ProfileType {

    private final Object description_lock;
    private @GuardedBy("description_lock") ProfileDescription description;

    private final Object account_current_lock;
    private @GuardedBy("account_current_lock") AccountType account_current;

    private ProfilesDatabase owner;
    private final AccountsDatabaseType accounts;
    private final ProfileID id;
    private final File directory;

    private Profile(
        final @Nullable ProfilesDatabase in_owner,
        final ProfileID in_id,
        final File in_directory,
        final ProfileDescription in_description,
        final AccountsDatabaseType in_accounts,
        final AccountType in_account_current) {

      this.id =
          NullCheck.notNull(in_id, "id");
      this.directory =
          NullCheck.notNull(in_directory, "directory");
      this.description =
          NullCheck.notNull(in_description, "description");
      this.accounts =
          NullCheck.notNull(in_accounts, "accounts");
      this.account_current =
          NullCheck.notNull(in_account_current, "account_current");

      this.account_current_lock = new Object();
      this.description_lock = new Object();
      this.owner = in_owner;
    }

    private void setOwner(final ProfilesDatabase owner) {
      this.owner = NullCheck.notNull(owner, "Owner");
    }

    @Override
    public ProfileID id() {
      return this.id;
    }

    @Override
    public boolean isAnonymous() {
      return this.id.equals(ANONYMOUS_PROFILE_ID);
    }

    @Override
    public File directory() {
      return this.directory;
    }

    @Override
    public String displayName() {
      synchronized (this.description_lock) {
        return this.description.displayName();
      }
    }

    @Override
    public boolean isCurrent() {
      synchronized (this.owner.profile_current_lock) {
        return this.id.equals(this.owner.profile_current);
      }
    }

    @Override
    public AccountType accountCurrent() {
      synchronized (this.account_current_lock) {
        return this.account_current;
      }
    }

    @Override
    public SortedMap<AccountID, AccountType> accounts() {
      return this.accounts.accounts();
    }

    @Override
    public ProfilePreferences preferences() {
      synchronized (this.description_lock) {
        return this.description.preferences();
      }
    }

    @Override
    public SortedMap<URI, AccountType> accountsByProvider() {
      return this.accounts.accountsByProvider();
    }

    @Override
    public AccountType account(final AccountID account_id)
        throws AccountsDatabaseNonexistentException {

      final AccountType account =
          this.accounts().get(NullCheck.notNull(account_id, "Account ID"));

      if (account == null) {
        throw new AccountsDatabaseNonexistentException("Nonexistent account: " + account_id.id());
      }

      return account;
    }

    @Override
    public AccountsDatabaseType accountsDatabase() {
      return this.accounts;
    }

    @Override
    public void preferencesUpdate(final ProfilePreferences preferences)
        throws IOException {

      NullCheck.notNull(preferences, "Preferences");

      final ProfileDescription new_desc;
      synchronized (this.description_lock) {
        new_desc =
            this.description.toBuilder()
                .setPreferences(preferences)
                .build();

        writeDescription(this.directory, new_desc);
        this.description = new_desc;
      }
    }

    @Override
    public AccountType createAccount(final AccountProvider account_provider)
        throws AccountsDatabaseException {

      NullCheck.notNull(account_provider, "Account provider");
      return this.accounts.createAccount(account_provider);
    }

    @Override
    public AccountID deleteAccountByProvider(final AccountProvider account_provider)
        throws AccountsDatabaseException {

      NullCheck.notNull(account_provider, "Account provider");
      final AccountID deleted = this.accounts.deleteAccountByProvider(account_provider);

      synchronized (this.account_current_lock) {
        if (this.account_current.id().equals(deleted)) {
          this.account_current = NullCheck.notNull(this.accounts().get(accounts().firstKey()));
        }
        return deleted;
      }
    }

    @Override
    public AccountType selectAccount(final AccountProvider account_provider)
        throws AccountsDatabaseNonexistentException {

      NullCheck.notNull(account_provider, "Account provider");
      final AccountType account = this.accounts.accountsByProvider().get(account_provider.id());
      if (account != null) {
        setAccountCurrent(account.id());
        return account;
      }

      throw new AccountsDatabaseNonexistentException(
          "No account with provider: " + account_provider.id());
    }

    @Override
    public int compareTo(final ProfileReadableType other) {
      return this.displayName().compareTo(NullCheck.notNull(other, "Other").displayName());
    }

    void setAccountCurrent(final AccountID id)
        throws AccountsDatabaseNonexistentException {

      NullCheck.notNull(id, "ID");
      synchronized (this.account_current_lock) {
        final AccountType account = this.accounts.accounts().get(id);
        if (account != null) {
          this.account_current = account;
        } else {
          throw new AccountsDatabaseNonexistentException("No such account: " + id.id());
        }
      }
    }
  }

  private static void writeDescription(
      final File directory,
      final ProfileDescription new_desc)
      throws IOException {

    final File profile_lock =
        new File(directory, "lock");
    final File profile_file =
        new File(directory, "profile.json");
    final File profile_file_tmp =
        new File(directory, "profile.json.tmp");

    FileLocking.withFileThreadLocked(
        profile_lock,
        1000L,
        ignored -> {

          /*
           * Ignore the return value here; the write call will immediately fail if this
           * call fails anyway.
           */

          directory.mkdirs();

          FileUtilities.fileWriteUTF8Atomically(
              profile_file,
              profile_file_tmp,
              ProfileDescriptionJSON.serializeToString(new ObjectMapper(), new_desc));
          return Unit.unit();
        });
  }
}
