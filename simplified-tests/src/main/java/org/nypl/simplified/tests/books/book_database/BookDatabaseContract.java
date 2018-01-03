package org.nypl.simplified.tests.books.book_database;

import com.io7m.jfunctional.Option;

import org.junit.Assert;
import org.junit.Test;
import org.nypl.simplified.books.accounts.AccountID;
import org.nypl.simplified.books.book_database.BookDatabase;
import org.nypl.simplified.books.book_database.BookDatabaseType;
import org.nypl.simplified.books.book_database.BookID;
import org.nypl.simplified.files.DirectoryUtilities;
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry;
import org.nypl.simplified.opds.core.OPDSAvailabilityOpenAccess;
import org.nypl.simplified.opds.core.OPDSJSONParser;
import org.nypl.simplified.opds.core.OPDSJSONParserType;
import org.nypl.simplified.opds.core.OPDSJSONSerializer;
import org.nypl.simplified.opds.core.OPDSJSONSerializerType;

import java.io.File;
import java.util.Calendar;

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

  @Test
  public final void openCreateReopen()
      throws Exception {

    final OPDSJSONParserType parser = OPDSJSONParser.newParser();
    final OPDSJSONSerializerType serializer = OPDSJSONSerializer.newSerializer();

    final File directory =
        DirectoryUtilities.directoryCreateTemporary();
    final BookDatabaseType db0 =
        BookDatabase.open(parser, serializer, AccountID.create(1), directory);

    final OPDSAcquisitionFeedEntry entry0 =
        OPDSAcquisitionFeedEntry.newBuilder(
            "a",
            "Title",
            Calendar.getInstance(),
            OPDSAvailabilityOpenAccess.get(Option.none()))
            .build();

    final OPDSAcquisitionFeedEntry entry1 =
        OPDSAcquisitionFeedEntry.newBuilder(
            "b",
            "Title",
            Calendar.getInstance(),
            OPDSAvailabilityOpenAccess.get(Option.none()))
            .build();

    final OPDSAcquisitionFeedEntry entry2 =
        OPDSAcquisitionFeedEntry.newBuilder(
            "c",
            "Title",
            Calendar.getInstance(),
            OPDSAvailabilityOpenAccess.get(Option.none()))
            .build();

    final BookID id0 = BookID.create("a");
    db0.create(id0, entry0);
    final BookID id1 = BookID.create("b");
    db0.create(id1, entry1);
    final BookID id2 = BookID.create("c");
    db0.create(id2, entry2);

    final BookDatabaseType db1 =
        BookDatabase.open(parser, serializer, AccountID.create(1), directory);

    Assert.assertEquals(3, db1.books().size());
    Assert.assertTrue(db1.books().containsKey(id0));
    Assert.assertTrue(db1.books().containsKey(id1));
    Assert.assertTrue(db1.books().containsKey(id2));
    Assert.assertEquals(db1.books().get(id0).entry().getID(), entry0.getID());
    Assert.assertEquals(db1.books().get(id1).entry().getID(), entry1.getID());
    Assert.assertEquals(db1.books().get(id2).entry().getID(), entry2.getID());
  }
}
