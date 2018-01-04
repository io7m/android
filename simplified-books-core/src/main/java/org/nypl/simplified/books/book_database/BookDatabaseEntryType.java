package org.nypl.simplified.books.book_database;

import org.nypl.drm.core.AdobeAdeptLoan;
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry;

import java.io.File;

/**
 * An entry in the book database.
 */

public interface BookDatabaseEntryType {

  /**
   * @return The most recent book value for the entry
   */

  Book book();

  /**
   * Copy the EPUB file at {@code file} into the database.
   *
   * @param file The source file
   * @throws BookDatabaseException On errors
   */

  void writeEPUB(File file)
      throws BookDatabaseException;

  /**
   * Copy the Adobe Adept loan information into the database.
   *
   * @param loan The loan information
   * @throws BookDatabaseException On errors
   */

  void writeAdobeLoan(AdobeAdeptLoan loan)
      throws BookDatabaseException;

  /**
   * Copy the OPDS entry into the database.
   *
   * @param opds_entry The OPDS entry
   * @throws BookDatabaseException On errors
   */

  void writeOPDSEntry(OPDSAcquisitionFeedEntry opds_entry)
      throws BookDatabaseException;

  /**
   * Delete the entry.
   * @throws BookDatabaseException On errors
   */

  void delete() throws BookDatabaseException;
}
