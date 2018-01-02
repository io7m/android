package org.nypl.simplified.books.book_database;

import org.nypl.simplified.books.accounts.AccountID;

import java.io.IOException;
import java.util.SortedMap;

public interface BookDatabaseType {

  AccountID owner();

  SortedMap<BookID, Book> books();

  void delete() throws IOException;
}
