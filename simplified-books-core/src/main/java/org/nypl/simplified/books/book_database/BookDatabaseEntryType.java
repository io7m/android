package org.nypl.simplified.books.book_database;

import org.nypl.drm.core.AdobeAdeptLoan;

import java.io.File;

public interface BookDatabaseEntryType {

  Book book();

  void writeEPUB(File file) throws BookDatabaseException;

  void writeAdobeLoan(AdobeAdeptLoan loan) throws BookDatabaseException;
}
