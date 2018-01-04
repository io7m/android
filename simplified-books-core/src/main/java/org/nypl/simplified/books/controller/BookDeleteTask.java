package org.nypl.simplified.books.controller;

import com.io7m.jfunctional.Unit;
import com.io7m.jnull.NullCheck;

import org.nypl.simplified.books.accounts.AccountType;
import org.nypl.simplified.books.book_database.Book;
import org.nypl.simplified.books.book_database.BookDatabaseEntryType;
import org.nypl.simplified.books.book_database.BookDatabaseException;
import org.nypl.simplified.books.book_database.BookDatabaseType;
import org.nypl.simplified.books.book_database.BookID;
import org.nypl.simplified.books.book_registry.BookRegistryType;
import org.nypl.simplified.books.book_registry.BookWithStatus;
import org.nypl.simplified.books.core.BookStatus;

import java.util.concurrent.Callable;

final class BookDeleteTask implements Callable<Unit> {

  private final AccountType account;
  private final BookRegistryType book_registry;
  private final BookID book_id;

  BookDeleteTask(
      final AccountType account,
      final BookRegistryType book_registry,
      final BookID book_id) {

    this.account =
        NullCheck.notNull(account, "Account");
    this.book_registry =
        NullCheck.notNull(book_registry, "Book registry");
    this.book_id =
        NullCheck.notNull(book_id, "Book id");
  }

  @Override
  public Unit call() throws Exception {
    execute();
    return Unit.unit();
  }

  private void execute() throws BookDatabaseException {
    final BookDatabaseType book_database = this.account.bookDatabase();
    final BookDatabaseEntryType entry = book_database.entry(this.book_id);
    entry.deleteEPUB();
    final Book book = entry.book();
    this.book_registry.update(BookWithStatus.create(book, BookStatus.fromBook(book)));
  }
}
