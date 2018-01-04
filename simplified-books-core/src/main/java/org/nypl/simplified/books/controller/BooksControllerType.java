package org.nypl.simplified.books.controller;

import org.nypl.simplified.books.accounts.AccountType;
import org.nypl.simplified.books.book_database.BookID;
import org.nypl.simplified.opds.core.OPDSAcquisition;
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry;

/**
 * The books controller.
 */

public interface BooksControllerType {

  /**
   * Attempt to borrow the given book.
   *
   * @param id          The ID of the book
   * @param account     The account that will receive the book
   * @param acquisition The acquisition entry for the book
   * @param entry       The OPDS feed entry for the book
   */

  void bookBorrow(
      BookID id,
      AccountType account,
      OPDSAcquisition acquisition,
      OPDSAcquisitionFeedEntry entry);

  /**
   * Dismiss a failed book borrowing.
   *
   * @param id      The ID of the book
   * @param account The account that failed to receive the book
   */

  void bookBorrowFailedDismiss(
      BookID id,
      AccountType account);

  /**
   * Sync all books for the given account.
   *
   * @param account The account
   */

  void booksSync(
      AccountType account);

  /**
   * Revoke the given book.
   *
   * @param book_id The ID of the book
   * @param account The account
   */

  void bookRevoke(
      BookID book_id,
      AccountType account);
}
