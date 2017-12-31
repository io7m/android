package org.nypl.simplified.books.book_registry;

import com.google.auto.value.AutoValue;

import org.nypl.simplified.books.book_database.Book;
import org.nypl.simplified.books.book_database.BookID;
import org.nypl.simplified.books.core.BookStatusType;

@AutoValue
public abstract class BookEvent {

  BookEvent() {

  }

  public enum Type {
    BOOK_CHANGED,
    BOOK_REMOVED
  }

  public abstract BookID book();

  public abstract Type type();

  public static BookEvent create(
      final BookID book,
      final Type type)
  {
    return new AutoValue_BookEvent(book, type);
  }
}
