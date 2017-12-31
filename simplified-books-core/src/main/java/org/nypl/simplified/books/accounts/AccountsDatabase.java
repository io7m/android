package org.nypl.simplified.books.accounts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.io7m.jnull.NullCheck;

import org.nypl.simplified.assertions.Assertions;
import org.nypl.simplified.books.book_database.BookDatabaseException;
import org.nypl.simplified.books.book_database.BookDatabaseFactoryType;
import org.nypl.simplified.books.book_database.BookDatabaseType;
import org.nypl.simplified.books.core.LogUtilities;
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

/**
 * The default implementation of the {@link AccountsDatabaseType} interface.
 */

public final class AccountsDatabase implements AccountsDatabaseType {

  private static final Logger LOG = LogUtilities.getLog(AccountsDatabase.class);

  private final File directory;
  private final SortedMap<AccountID, Account> accounts;
  private final SortedMap<URI, Account> accounts_by_provider;
  private final SortedMap<AccountID, AccountType> accounts_read;
  private final SortedMap<URI, AccountType> accounts_by_provider_read;
  private final BookDatabaseFactoryType book_databases;

  private AccountsDatabase(
      final File directory,
      final SortedMap<AccountID, Account> accounts,
      final SortedMap<URI, Account> accounts_by_provider,
      final BookDatabaseFactoryType book_databases) {

    this.directory =
        NullCheck.notNull(directory, "directory");
    this.accounts =
        NullCheck.notNull(accounts, "accounts");
    this.accounts_by_provider =
        NullCheck.notNull(accounts_by_provider, "accounts_by_provider");
    this.book_databases =
        NullCheck.notNull(book_databases, "book databases");

    this.accounts_read =
        castMap(Collections.unmodifiableSortedMap(accounts));
    this.accounts_by_provider_read =
        castMap(Collections.unmodifiableSortedMap(accounts_by_provider));
  }

  /**
   * Open an accounts database from the given directory, creating a new database if one does not exist.
   *
   * @param book_databases A factory for book databases
   * @param directory      The directory
   * @return An accounts database
   * @throws AccountsDatabaseException If any errors occurred whilst trying to open the database
   */

  public static AccountsDatabaseType open(
      final BookDatabaseFactoryType book_databases,
      final File directory)
      throws AccountsDatabaseException {

    NullCheck.notNull(directory, "Directory");

    LOG.debug("opening account database: {}", directory);

    final SortedMap<AccountID, Account> accounts = new ConcurrentSkipListMap<>();
    final SortedMap<URI, Account> accounts_by_provider = new ConcurrentSkipListMap<>();
    final ObjectMapper jom = new ObjectMapper();

    final List<Exception> errors = new ArrayList<>();
    if (!directory.exists()) {
      directory.mkdirs();
    }

    if (!directory.isDirectory()) {
      errors.add(new IOException("Not a directory: " + directory));
    }

    openAllAccounts(book_databases, directory, accounts, accounts_by_provider, jom, errors);

    if (!errors.isEmpty()) {
      throw new AccountsDatabaseException(
          "One or more errors occurred whilst trying to open the account database.", errors);
    }

    return new AccountsDatabase(directory, accounts, accounts_by_provider, book_databases);
  }

  private static void openAllAccounts(
      final BookDatabaseFactoryType book_databases,
      final File directory,
      final SortedMap<AccountID, Account> accounts,
      final SortedMap<URI, Account> accounts_by_provider,
      final ObjectMapper jom,
      final List<Exception> errors) {

    final String[] account_dirs = directory.list();
    if (account_dirs != null) {
      for (int index = 0; index < account_dirs.length; ++index) {
        final String account_id_name = account_dirs[index];
        LOG.debug("opening account: {}/{}", directory, account_id_name);

        final Account account =
            openOneAccount(book_databases, directory, jom, errors, account_id_name);

        if (account != null) {
          if (accounts_by_provider.containsKey(account.provider())) {
            errors.add(new AccountsDatabaseException(
                "Multiple accounts using the same provider: " + account.provider(),
                Collections.<Exception>emptyList()));
          }

          accounts.put(account.id, account);
          accounts_by_provider.put(account.provider(), account);
        }
      }
    }
  }

  private static Account openOneAccount(
      final BookDatabaseFactoryType book_databases,
      final File directory,
      final ObjectMapper jom,
      final List<Exception> errors,
      final String account_id_name) {

    final int id;
    try {
      id = Integer.parseInt(account_id_name);
    } catch (final NumberFormatException e) {
      errors.add(new IOException("Could not parse directory name as an account ID", e));
      return null;
    }

    final AccountID account_id = AccountID.create(id);
    final File account_dir = new File(directory, account_id_name);
    final File account_file = new File(account_dir, "account.json");
    final File books_dir = new File(account_dir, "books");

    try {
      final BookDatabaseType book_database =
          book_databases.openDatabase(account_id, books_dir);
      final AccountDescription desc =
          AccountDescriptionJSON.deserializeFromFile(jom, account_file);

      return new Account(account_id, account_dir, desc, book_database);
    } catch (final IOException e) {
      errors.add(new IOException("Could not parse account: " + account_file, e));
      return null;
    } catch (final BookDatabaseException e) {
      errors.add(e);
      return null;
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

  @Override
  public File directory() {
    return this.directory;
  }

  @Override
  public SortedMap<AccountID, AccountType> accounts() {
    return this.accounts_read;
  }

  @Override
  public SortedMap<URI, AccountType> accountsByProvider() {
    return this.accounts_by_provider_read;
  }

  @Override
  public AccountType createAccount(
      final AccountProvider account_provider)
      throws AccountsDatabaseException {

    NullCheck.notNull(account_provider, "Account provider");

    final AccountID next;
    if (!this.accounts.isEmpty()) {
      next = AccountID.create(this.accounts.lastKey().id() + 1);
    } else {
      next = AccountID.create(0);
    }

    LOG.debug("creating account {} (provider {})", next.id(), account_provider.id());

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
      final File books_dir =
          new File(account_dir, "books");

      account_dir.mkdirs();

      final BookDatabaseType book_database =
          this.book_databases.openDatabase(next, books_dir);

      final AccountDescription desc = AccountDescription.create(account_provider.id());
      FileUtilities.fileWriteUTF8Atomically(
          account_file,
          account_file_tmp,
          AccountDescriptionJSON.serializeToString(new ObjectMapper(), desc));

      final Account account = new Account(next, account_dir, desc, book_database);
      this.accounts.put(next, account);
      return account;
    } catch (final IOException e) {
      throw new AccountsDatabaseException(
          "Could not write account data", Collections.singletonList((Exception) e));
    } catch (final BookDatabaseException e) {
      throw new AccountsDatabaseException(
          "Could not create book database", Collections.singletonList((Exception) e));
    }
  }

  private static final class Account implements AccountType {

    private final AccountID id;
    private final File directory;
    private final AccountDescription description;
    private final BookDatabaseType book_database;

    Account(
        final AccountID id,
        final File directory,
        final AccountDescription description,
        final BookDatabaseType book_database) {

      this.id = NullCheck.notNull(id, "id");
      this.directory = NullCheck.notNull(directory, "directory");
      this.description = NullCheck.notNull(description, "description");
      this.book_database = NullCheck.notNull(book_database, "book database");
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

    @Override
    public BookDatabaseType bookDatabase() {
      return this.book_database;
    }
  }
}
