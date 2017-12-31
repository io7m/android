package org.nypl.simplified.tests.books.book_database;

import org.junit.Assert;
import org.junit.Test;
import org.nypl.simplified.books.accounts.AccountID;
import org.nypl.simplified.books.book_database.BookDatabase;
import org.nypl.simplified.books.book_database.BookDatabaseType;
import org.nypl.simplified.files.DirectoryUtilities;
import org.nypl.simplified.opds.core.OPDSJSONParser;
import org.nypl.simplified.opds.core.OPDSJSONParserType;
import org.nypl.simplified.opds.core.OPDSJSONSerializer;
import org.nypl.simplified.opds.core.OPDSJSONSerializerType;

import java.io.File;

public abstract class BookDatabaseContract {

  @Test
  public final void openEmpty()
      throws Exception {

    final OPDSJSONParserType parser = OPDSJSONParser.newParser();
    final OPDSJSONSerializerType serializer = OPDSJSONSerializer.newSerializer();

    final File directory =
        DirectoryUtilities.directoryCreateTemporary();
    final BookDatabaseType db =
        BookDatabase.open(parser, serializer, AccountID.create(1), directory);

    Assert.assertEquals(0L, db.books().size());
  }
}
