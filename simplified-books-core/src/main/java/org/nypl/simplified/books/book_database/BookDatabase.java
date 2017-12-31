package org.nypl.simplified.books.book_database;

import com.io7m.jnull.NullCheck;
import com.io7m.jnull.Nullable;

import org.nypl.simplified.books.accounts.AccountID;
import org.nypl.simplified.books.accounts.AccountType;
import org.nypl.simplified.books.core.LogUtilities;
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

/**
 * The default implementation of the {@link BookDatabaseType} interface.
 */

public final class BookDatabase implements BookDatabaseType {

  private static final Logger LOG = LogUtilities.getLog(BookDatabase.class);

  private final AccountID owner;
  private final File directory;
  private final SortedMap<BookID, Book> books_read;
  private final ConcurrentSkipListMap<BookID, Book> books;

  private BookDatabase(
      final AccountID in_owner,
      final File in_directory,
      final ConcurrentSkipListMap<BookID, Book> in_books)
  {
    this.owner = NullCheck.notNull(in_owner, "Owner");
    this.directory = NullCheck.notNull(in_directory, "Directory");
    this.books = NullCheck.notNull(in_books, "Books");
    this.books_read = Collections.unmodifiableSortedMap(this.books);
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

    final ConcurrentSkipListMap<BookID, Book> books = new ConcurrentSkipListMap<>();

    final List<Exception> errors = new ArrayList<>();
    openAllBooks(parser, owner, directory, books, errors);

    if (!errors.isEmpty()) {
      throw new BookDatabaseException(
          "One or more errors occurred whilst trying to open a book database.", errors);
    }

    return new BookDatabase(owner, directory, books);
  }

  private static void openAllBooks(
      final OPDSJSONParserType parser,
      final AccountID account,
      final File directory,
      final SortedMap<BookID, Book> books,
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
        final Book book = openOneBook(parser, account, book_directory, errors, book_id);
        if (book == null) {
          continue;
        }
        books.put(book.id(), book);
      }
    }
  }

  private static @Nullable Book openOneBook(
      final OPDSJSONParserType parser,
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

      return book_builder.build();
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
    return this.books_read;
  }
}
