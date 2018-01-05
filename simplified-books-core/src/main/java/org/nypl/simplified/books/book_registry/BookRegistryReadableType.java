package org.nypl.simplified.books.book_registry;

import com.io7m.jfunctional.OptionType;

import org.nypl.simplified.books.book_database.BookID;
import org.nypl.simplified.observable.ObservableReadableType;

import java.util.SortedMap;

public interface BookRegistryReadableType {

  SortedMap<BookID, BookWithStatus> books();

  ObservableReadableType<BookStatusEvent> bookEvents();

  OptionType<BookStatusType> bookStatus(BookID id);
}
