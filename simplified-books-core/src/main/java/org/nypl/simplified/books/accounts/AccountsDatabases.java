package org.nypl.simplified.books.accounts;

import org.nypl.simplified.books.book_database.BookDatabaseFactoryType;
import org.nypl.simplified.books.book_database.BookDatabases;
import org.nypl.simplified.books.profiles.ProfileType;

import java.io.File;

public final class AccountsDatabases implements AccountsDatabaseFactoryType {

  private static final AccountsDatabases INSTANCE = new AccountsDatabases();

  public static AccountsDatabases get() {
    return INSTANCE;
  }

  private AccountsDatabases() {

  }

  @Override
  public AccountsDatabaseType openDatabase(
      final BookDatabaseFactoryType book_databases,
      final File directory)
      throws AccountsDatabaseException {
    return AccountsDatabase.open(book_databases, directory);
  }

  @Override
  public AccountsDatabaseType openDatabase(
      final File directory)
      throws AccountsDatabaseException {
    return AccountsDatabase.open(BookDatabases.get(), directory);
  }
}
