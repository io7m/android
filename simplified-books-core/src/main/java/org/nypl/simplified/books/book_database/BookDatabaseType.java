package org.nypl.simplified.books.book_database;

import org.nypl.simplified.books.accounts.AccountID;
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry;

import java.util.SortedMap;

public interface BookDatabaseType {

  AccountID owner();

  SortedMap<BookID, Book> books();

  void delete()
      throws BookDatabaseException;

  BookDatabaseEntryType create(
      BookID id,
      OPDSAcquisitionFeedEntry entry)
      throws BookDatabaseException;

  BookDatabaseEntryType entry(BookID id)
      throws BookDatabaseException;
}
