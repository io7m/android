package org.nypl.simplified.books.book_database;

import org.nypl.simplified.books.accounts.AccountID;
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry;

import java.util.SortedMap;

/**
 * The type of book databases.
 */

public interface BookDatabaseType {

  /**
   * @return The account that owns the database
   */

  AccountID owner();

  /**
   * @return A read-only map of the books available in the database
   */

  SortedMap<BookID, Book> books();

  /**
   * Delete the book database.
   *
   * @throws BookDatabaseException On errors
   */

  void delete()
      throws BookDatabaseException;

  /**
   * Create a new, or update an existing, database entry for the given book ID.
   *
   * @param id    The book ID
   * @param entry The current OPDS entry for the book
   * @return A database entry
   * @throws BookDatabaseException On errors
   */

  BookDatabaseEntryType createOrUpdate(
      BookID id,
      OPDSAcquisitionFeedEntry entry)
      throws BookDatabaseException;

  /**
   * Find an existing database entry for the given book ID.
   *
   * @param id The book ID
   * @return A database entry
   * @throws BookDatabaseException On errors
   */

  BookDatabaseEntryType entry(BookID id)
      throws BookDatabaseException;
}
