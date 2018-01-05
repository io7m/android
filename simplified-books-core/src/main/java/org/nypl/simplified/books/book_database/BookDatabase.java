package org.nypl.simplified.books.book_database;

import com.io7m.jfunctional.Option;
import com.io7m.jnull.NullCheck;
import com.io7m.jnull.Nullable;

import org.nypl.drm.core.AdobeAdeptLoan;
import org.nypl.simplified.assertions.Assertions;
import org.nypl.simplified.books.accounts.AccountID;
import org.nypl.simplified.books.core.LogUtilities;
import org.nypl.simplified.files.DirectoryUtilities;
import org.nypl.simplified.files.FileUtilities;
import org.nypl.simplified.json.core.JSONSerializerUtilities;
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry;
import org.nypl.simplified.opds.core.OPDSJSONParserType;
import org.nypl.simplified.opds.core.OPDSJSONSerializerType;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentSkipListMap;

import javax.annotation.concurrent.GuardedBy;

/**
 * The default implementation of the {@link BookDatabaseType} interface.
 */

public final class BookDatabase implements BookDatabaseType {

  private static final Logger LOG = LogUtilities.getLog(BookDatabase.class);

  private final AccountID owner;
  private final File directory;
  private final OPDSJSONSerializerType serializer;
  private final Object maps_lock;
  private final @GuardedBy("maps_lock") ConcurrentSkipListMap<BookID, Book> books;
  private final @GuardedBy("maps_lock") SortedMap<BookID, Book> books_read;
  private final @GuardedBy("maps_lock") ConcurrentSkipListMap<BookID, DatabaseEntry> entries;
  private final @GuardedBy("maps_lock") SortedMap<BookID, BookDatabaseEntryType> entries_read;

  private BookDatabase(
      final AccountID in_owner,
      final File in_directory,
      final ConcurrentSkipListMap<BookID, Book> in_books,
      final ConcurrentSkipListMap<BookID, DatabaseEntry> in_entries,
      final OPDSJSONSerializerType serializer)
  {
    this.owner =
        NullCheck.notNull(in_owner, "Owner");
    this.directory =
        NullCheck.notNull(in_directory, "Directory");
    this.books =
        NullCheck.notNull(in_books, "Books");
    this.entries =
        NullCheck.notNull(in_entries, "Entries");
    this.serializer =
        NullCheck.notNull(serializer, "Serializer");

    this.maps_lock = new Object();
    this.books_read = Collections.unmodifiableSortedMap(this.books);
    this.entries_read = Collections.unmodifiableSortedMap(this.entries);
  }

  public static BookDatabaseType open(
      final OPDSJSONParserType parser,
      final OPDSJSONSerializerType serializer,
      final AccountID owner,
      final File directory)
      throws BookDatabaseException {

    NullCheck.notNull(parser, "Parser");
    NullCheck.notNull(serializer, "Serializer");
    NullCheck.notNull(owner, "Owner");
    NullCheck.notNull(directory, "Directory");

    LOG.debug("opening book database: {}", directory);


    final ConcurrentSkipListMap<BookID, DatabaseEntry> entries = new ConcurrentSkipListMap<>();

    final List<Exception> errors = new ArrayList<>();
    openAllBooks(parser, serializer, owner, directory, entries, errors);

    if (!errors.isEmpty()) {
      throw new BookDatabaseException(
          "One or more errors occurred whilst trying to open a book database.", errors);
    }

    final ConcurrentSkipListMap<BookID, Book> books = new ConcurrentSkipListMap<>();
    for (final DatabaseEntry e : entries.values()) {
      books.put(e.book().id(), e.book());
    }

    return new BookDatabase(owner, directory, books, entries, serializer);
  }

  private static void openAllBooks(
      final OPDSJSONParserType parser,
      final OPDSJSONSerializerType serializer,
      final AccountID account,
      final File directory,
      final ConcurrentSkipListMap<BookID, DatabaseEntry> entries,
      final List<Exception> errors) {

    if (!directory.exists()) {
      directory.mkdirs();
    }

    if (!directory.isDirectory()) {
      errors.add(new IOException("Not a directory: " + directory));
    }

    final String[] book_dirs = directory.list();
    if (book_dirs != null) {
      for (final String book_id : book_dirs) {
        LOG.debug("opening book: {}/{}", directory, book_id);
        final File book_directory = new File(directory, book_id);
        final DatabaseEntry entry =
            openOneEntry(parser, serializer, account, book_directory, errors, book_id);
        if (entry == null) {
          continue;
        }
        entries.put(entry.book().id(), entry);
      }
    }
  }

  private static @Nullable DatabaseEntry openOneEntry(
      final OPDSJSONParserType parser,
      final OPDSJSONSerializerType serializer,
      final AccountID account_id,
      final File directory,
      final List<Exception> errors,
      final String name) {

    try {
      if (!directory.isDirectory()) {
        return null;
      }

      final BookID book_id = BookID.create(name);

      final File file_meta = new File(directory, "meta.json");
      final OPDSAcquisitionFeedEntry entry;
      try (FileInputStream is = new FileInputStream(file_meta)) {
        entry = parser.parseAcquisitionFeedEntryFromStream(is);
      }

      final Book.Builder book_builder = Book.builder(book_id, account_id, entry);

      final File file_book = new File(directory, "book.epub");
      if (file_book.isFile()) {
        book_builder.setFile(file_book);
      }

      final File file_cover = new File(directory, "cover.jpg");
      if (file_cover.isFile()) {
        book_builder.setCover(file_cover);
      }

      return new DatabaseEntry(directory, serializer, book_builder.build());
    } catch (final IOException e) {
      errors.add(e);
      return null;
    }
  }

  @Override
  public AccountID owner() {
    return this.owner;
  }

  @Override
  public SortedMap<BookID, Book> books() {
    synchronized (this.maps_lock) {
      return this.books_read;
    }
  }

  @Override
  public void delete() throws BookDatabaseException {
    try {
      DirectoryUtilities.directoryDelete(this.directory);
    } catch (final IOException e) {
      throw new BookDatabaseException(
          "Could not delete book database", Collections.singletonList(e));
    } finally {
      synchronized (this.maps_lock) {
        this.books.clear();
        this.entries.clear();
      }
    }
  }

  @Override
  public BookDatabaseEntryType createOrUpdate(
      final BookID id,
      final OPDSAcquisitionFeedEntry feed_entry) throws BookDatabaseException {

    NullCheck.notNull(id, "ID");
    NullCheck.notNull(feed_entry, "Entry");

    synchronized (this.maps_lock) {
      try {
        final File book_dir = new File(this.directory, id.value());
        DirectoryUtilities.directoryCreate(book_dir);

        final File file_meta = new File(book_dir, "meta.json");
        final File file_meta_tmp = new File(book_dir, "meta.json.tmp");

        FileUtilities.fileWriteUTF8Atomically(
            file_meta,
            file_meta_tmp,
            JSONSerializerUtilities.serializeToString(
                this.serializer.serializeFeedEntry(feed_entry)));

        final Book.Builder book_builder = Book.builder(id, this.owner, feed_entry);
        final DatabaseEntry entry =
            new DatabaseEntry(book_dir, this.serializer, book_builder.build());
        this.books.put(id, entry.book());
        this.entries.put(id, entry);
        return entry;
      } catch (final IOException e) {
        throw new BookDatabaseException(e.getMessage(), Collections.singletonList(e));
      }
    }
  }

  @Override
  public BookDatabaseEntryType entry(final BookID id) throws BookDatabaseException {

    NullCheck.notNull(id, "ID");

    synchronized (this.maps_lock) {
      final DatabaseEntry entry = this.entries.get(id);
      if (entry == null) {
        throw new BookDatabaseException(
            "Nonexistent book entry: " + id.value(), Collections.emptyList());
      }
      return entry;
    }
  }

  private static final class DatabaseEntry implements BookDatabaseEntryType {

    private final File book_dir;
    private final Object book_lock;
    private final OPDSJSONSerializerType serializer;
    private @GuardedBy("book_lock") boolean deleted;
    private @GuardedBy("book_lock") Book book;

    DatabaseEntry(
        final File book_dir,
        OPDSJSONSerializerType serializer, final Book book) {

      this.book_dir =
          NullCheck.notNull(book_dir, "Book directory");
      this.serializer =
          NullCheck.notNull(serializer, "Serializer");
      this.book =
          NullCheck.notNull(book, "book");

      this.book_lock = new Object();
      this.deleted = false;
    }

    @Override
    public Book book() {
      synchronized (this.book_lock) {
        Assertions.checkPrecondition(!this.deleted, "Entry must not have been deleted");
        return this.book;
      }
    }

    @Override
    public void writeEPUB(final File file_source) throws BookDatabaseException {
      NullCheck.notNull(file_source, "File");

      synchronized (this.book_lock) {
        Assertions.checkPrecondition(!this.deleted, "Entry must not have been deleted");

        final File file_target =
            new File(this.book_dir, "book.epub");
        final File file_target_tmp =
            new File(this.book_dir, "book.epub.tmp");

        try {
          DirectoryUtilities.directoryCreate(this.book_dir);
          FileUtilities.fileCopy(file_source, file_target_tmp);
          FileUtilities.fileRename(file_target_tmp, file_target);
          this.book =
              this.book.toBuilder()
                  .setFile(file_target)
                  .build();
        } catch (final IOException e) {
          throw new BookDatabaseException(e.getMessage(), Collections.singletonList(e));
        } finally {
          try {
            FileUtilities.fileDelete(file_target_tmp);
          } catch (final IOException ignored) {
            LOG.error("could not delete temporary file: {}: ", file_target_tmp, ignored);
          }
        }
      }
    }

    @Override
    public void writeAdobeLoan(final AdobeAdeptLoan loan) throws BookDatabaseException {
      NullCheck.notNull(loan, "Loan");

      synchronized (this.book_lock) {
        Assertions.checkPrecondition(!this.deleted, "Entry must not have been deleted");

        final File file_rights_target =
            new File(this.book_dir, "rights_adobe.xml");
        final File file_rights_target_tmp =
            new File(this.book_dir, "rights_adobe.xml.tmp");
        final File file_meta_target =
            new File(this.book_dir, "meta_adobe.json");
        final File file_meta_target_tmp =
            new File(this.book_dir, "meta_adobe.json.tmp");

        try {
          DirectoryUtilities.directoryCreate(this.book_dir);

          FileUtilities.fileWriteBytesAtomically(
              file_rights_target,
              file_rights_target_tmp,
              loan.getSerialized().array());

          FileUtilities.fileWriteUTF8Atomically(
              file_meta_target,
              file_meta_target_tmp,
              BookAdeptLoanJSON.serializeToString(loan));

          this.book =
              this.book.toBuilder()
                  .setAdobeLoan(loan)
                  .build();
        } catch (final IOException e) {
          throw new BookDatabaseException(e.getMessage(), Collections.singletonList(e));
        } finally {
          try {
            FileUtilities.fileDelete(file_rights_target_tmp);
          } catch (final IOException ignored) {
            LOG.error("could not delete temporary file: {}: ", file_rights_target_tmp, ignored);
          }
          try {
            FileUtilities.fileDelete(file_meta_target_tmp);
          } catch (final IOException ignored) {
            LOG.error("could not delete temporary file: {}: ", file_meta_target_tmp, ignored);
          }
        }
      }
    }

    @Override
    public void writeOPDSEntry(final OPDSAcquisitionFeedEntry opds_entry) throws BookDatabaseException {
      NullCheck.notNull(opds_entry, "OPDS entry");

      synchronized (this.book_lock) {
        Assertions.checkPrecondition(!this.deleted, "Entry must not have been deleted");

        final File file_meta =
            new File(this.book_dir, "meta.json");
        final File file_meta_tmp =
            new File(this.book_dir, "meta.json.tmp");

        try {
          DirectoryUtilities.directoryCreate(this.book_dir);

          FileUtilities.fileWriteUTF8Atomically(
              file_meta,
              file_meta_tmp,
              JSONSerializerUtilities.serializeToString(
                  this.serializer.serializeFeedEntry(opds_entry)));

          this.book =
              this.book.toBuilder()
                  .setEntry(opds_entry)
                  .build();
        } catch (final IOException e) {
          throw new BookDatabaseException(e.getMessage(), Collections.singletonList(e));
        } finally {
          try {
            FileUtilities.fileDelete(file_meta_tmp);
          } catch (final IOException ignored) {
            LOG.error("could not delete temporary file: {}: ", file_meta_tmp, ignored);
          }
        }
      }
    }

    @Override
    public void delete() throws BookDatabaseException {
      synchronized (this.book_lock) {
        Assertions.checkPrecondition(!this.deleted, "Entry must not have been deleted");

        try {
          DirectoryUtilities.directoryDelete(this.book_dir);
        } catch (final IOException e) {
          throw new BookDatabaseException(e.getMessage(), Collections.singletonList(e));
        }
      }
    }

    @Override
    public void deleteEPUB() throws BookDatabaseException {
      synchronized (this.book_lock) {
        Assertions.checkPrecondition(!this.deleted, "Entry must not have been deleted");

        final File file_target =
            new File(this.book_dir, "book.epub");

        try {
          DirectoryUtilities.directoryCreate(this.book_dir);
          FileUtilities.fileDelete(file_target);
          this.book =
              this.book.toBuilder()
                  .setFile(Option.none())
                  .build();
        } catch (final IOException e) {
          throw new BookDatabaseException(e.getMessage(), Collections.singletonList(e));
        }
      }
    }
  }
}
