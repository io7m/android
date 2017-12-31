package org.nypl.simplified.app.reader;

import com.android.volley.RequestQueue;
import com.io7m.jfunctional.OptionType;

import org.nypl.simplified.books.accounts.AccountAuthenticationCredentials;
import org.nypl.simplified.books.book_database.BookID;
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry;

/**
 * The interface exposed by bookmarks databases.
 */

public interface ReaderBookmarksType
{
  /**
   * Retrieve the current bookmark for the given book, if any.
   *
   * @param id The ID of the book
   * @param entry feed entry
   * @return A bookmark, if any
   */

  OptionType<ReaderBookLocation> getBookmark(
    BookID id,
    OPDSAcquisitionFeedEntry entry);

  /**
   * Set the bookmark for the given book.
   * @param id       The ID of the book
   * @param bookmark The bookmark
   * @param entry feed entry
   * @param credentials  account credentials
   * @param queue volley queue
   */

  void setBookmark(
    BookID id,
    ReaderBookLocation bookmark,
    OPDSAcquisitionFeedEntry entry,
    AccountAuthenticationCredentials credentials,
    RequestQueue queue);

  /**
   * @param id       The ID of the book
   * @param bookmark The bookmark
   */

  void setBookmark(
    BookID id,
    ReaderBookLocation bookmark);

}
