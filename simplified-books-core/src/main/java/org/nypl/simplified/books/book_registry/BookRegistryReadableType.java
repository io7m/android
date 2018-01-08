package org.nypl.simplified.books.book_registry;

import com.io7m.jfunctional.OptionType;

import org.nypl.simplified.books.book_database.BookID;
import org.nypl.simplified.observable.ObservableReadableType;

import java.util.SortedMap;

/**
 * The type of readable book registries.
 */

public interface BookRegistryReadableType {

  /**
   * @return A read-only map of the known books
   */

  SortedMap<BookID, BookWithStatus> books();

  /**
   * @return An observable that publishes book status events
   */

  ObservableReadableType<BookStatusEvent> bookEvents();

  /**
   * @param id The book ID
   * @return The status for the given book, if any.
   */

  OptionType<BookStatusType> bookStatus(BookID id);

  /**
   * @param id The book ID
   * @return The registered book, if any
   */

  OptionType<BookWithStatus> book(BookID id);
}
