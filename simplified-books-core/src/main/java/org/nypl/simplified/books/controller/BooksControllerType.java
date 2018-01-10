package org.nypl.simplified.books.controller;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;

import org.nypl.simplified.books.accounts.AccountType;
import org.nypl.simplified.books.book_database.BookID;
import org.nypl.simplified.books.feeds.FeedWithoutGroups;
import org.nypl.simplified.books.profiles.ProfileReadableType;
import org.nypl.simplified.opds.core.OPDSAcquisition;
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry;

/**
 * The books controller.
 */

public interface BooksControllerType {

  /**
   * Attempt to borrow the given book.
   *
   * @param account     The account that will receive the book
   * @param id          The ID of the book
   * @param acquisition The acquisition entry for the book
   * @param entry       The OPDS feed entry for the book
   */

  void bookBorrow(
      AccountType account,
      BookID id,
      OPDSAcquisition acquisition,
      OPDSAcquisitionFeedEntry entry);

  /**
   * Dismiss a failed book borrowing.
   *
   * @param account The account that failed to receive the book
   * @param id      The ID of the book
   */

  void bookBorrowFailedDismiss(
      AccountType account,
      BookID id);

  /**
   * Cancel a book download.
   *
   * @param account The account that would be receiving the book
   * @param id      The ID of the book
   */

  void bookDownloadCancel(
      AccountType account,
      BookID id);

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
   * @param account The account
   * @param book_id The ID of the book
   */

  void bookRevoke(
      AccountType account,
      BookID book_id);

  /**
   * Delete the given book.
   *
   * @param account The account
   * @param book_id The ID of the book
   */

  void bookDelete(
      AccountType account,
      BookID book_id);

  /**
   * Dismiss a failed book revocation.
   *
   * @param account The account that failed to revoke the book
   * @param id      The ID of the book
   */

  void bookRevokeFailedDismiss(
      AccountType account,
      BookID id);
}
