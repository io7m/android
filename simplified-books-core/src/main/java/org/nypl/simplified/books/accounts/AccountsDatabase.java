package org.nypl.simplified.books.accounts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.io7m.jnull.NullCheck;

import org.nypl.simplified.assertions.Assertions;
import org.nypl.simplified.books.core.LogUtilities;
import org.nypl.simplified.books.profiles.ProfileType;
import org.nypl.simplified.files.FileUtilities;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * The default implementation of the {@link AccountsDatabaseType} interface.
 */

public final class AccountsDatabase implements AccountsDatabaseType {

  private static final Logger LOG = LogUtilities.getLog(AccountsDatabase.class);

  private final File directory;
  private final SortedMap<AccountID, Account> accounts;
  private final SortedMap<AccountID, AccountType> accounts_read;
  private final ProfileType owner;

  private AccountsDatabase(
      final File directory,
      final SortedMap<AccountID, Account> accounts,
      final ProfileType owner) {
    this.directory = NullCheck.notNull(directory, "directory");
    this.accounts = NullCheck.notNull(accounts, "accounts");
    this.accounts_read = castMap(Collections.unmodifiableSortedMap(accounts));
    this.owner = NullCheck.notNull(owner, "owner");
  }

  /**
   * Open an accounts database from the given directory, creating a new database if one does not exist.
   *
   * @param owner     The profile that owns the database
   * @param directory The directory
   * @return A profile database
   * @throws AccountsDatabaseException If any errors occurred whilst trying to open the database
   */

  public static AccountsDatabaseType open(
      ProfileType owner,
      File directory)
      throws AccountsDatabaseException {

    NullCheck.notNull(owner, "Owner");
    NullCheck.notNull(directory, "Directory");

    LOG.debug("opening account database: {}", directory);

    final SortedMap<AccountID, Account> accounts = new ConcurrentSkipListMap<>();
    final ObjectMapper jom = new ObjectMapper();

    final List<Exception> errors = new ArrayList<>();
    if (!directory.exists()) {
      directory.mkdirs();
    }

    if (!directory.isDirectory()) {
      errors.add(new IOException("Not a directory: " + directory));
    }

    final String[] account_dirs = directory.list();
    if (account_dirs != null) {
      for (int index = 0; index < account_dirs.length; ++index) {
        String account_id_name = account_dirs[index];
        LOG.debug("opening account: {}/{}", directory, account_id_name);

        final int id;
        try {
          id = Integer.parseInt(account_id_name);
        } catch (NumberFormatException e) {
          errors.add(new IOException("Could not parse directory name as an account ID", e));
          continue;
        }

        File account_dir = new File(directory, account_id_name);
        File account_file = new File(account_dir, "account.json");

        try {
          AccountDescription desc = AccountDescriptionJSON.deserializeFromFile(jom, account_file);
          AccountID account_id = AccountID.create(id);
          Account account = new Account(account_id, account_dir, desc);
          accounts.put(account_id, account);
        } catch (IOException e) {
          errors.add(new IOException("Could not parse account: " + account_file, e));
          continue;
        }
      }
    }

    if (!errors.isEmpty()) {
      throw new AccountsDatabaseException(
          "One or more errors occurred whilst trying to open the account database.", errors);
    }

    return new AccountsDatabase(directory, accounts, owner);
  }

  /**
   * Perform an unchecked (but safe) cast of the given map type. The cast is safe because
   * {@code V <: VB}.
   */

  @SuppressWarnings("unchecked")
  private static <K, VB, V extends VB> SortedMap<K, VB> castMap(SortedMap<K, V> m) {
    return (SortedMap<K, VB>) m;
  }

  @Override
  public File directory() {
    return this.directory;
  }

  @Override
  public SortedMap<AccountID, AccountType> accounts() {
    return this.accounts_read;
  }

  @Override
  public ProfileType owner() {
    return this.owner;
  }

  @Override
  public AccountType createAccount(
      AccountProvider account_provider)
      throws AccountsDatabaseException {

    NullCheck.notNull(account_provider, "Account provider");

    final AccountID next;
    if (!this.accounts.isEmpty()) {
      next = AccountID.create(this.accounts.lastKey().id() + 1);
    } else {
      next = AccountID.create(0);
    }

    Assertions.checkInvariant(
        !this.accounts.containsKey(next),
        "Account ID %s cannot have been used", next);

    try {
      final File account_dir =
          new File(this.directory, Integer.toString(next.id()));
      final File account_file =
          new File(account_dir, "account.json");
      final File account_file_tmp =
          new File(account_dir, "account.json.tmp");

      // Ignore the return value, writing the file will raise an error if this call failed
      account_dir.mkdirs();

      AccountDescription desc = AccountDescription.create(account_provider.id());
      FileUtilities.fileWriteUTF8Atomically(
          account_file,
          account_file_tmp,
          AccountDescriptionJSON.serializeToString(new ObjectMapper(), desc));

      Account account = new Account(next, account_dir, desc);
      this.accounts.put(next, account);
      return account;
    } catch (IOException e) {
      throw new AccountsDatabaseException(
          "Could not write account data", Collections.singletonList((Exception) e));
    }
  }

  private static final class Account implements AccountType {

    private final AccountID id;
    private final File directory;
    private final AccountDescription description;

    Account(
        final AccountID id,
        final File directory,
        final AccountDescription description) {
      this.id = NullCheck.notNull(id, "id");
      this.directory = NullCheck.notNull(directory, "directory");
      this.description = NullCheck.notNull(description, "description");
    }

    @Override
    public AccountID id() {
      return this.id;
    }

    @Override
    public File directory() {
      return this.directory;
    }

    @Override
    public URI provider() {
      return this.description.provider();
    }
  }
}
