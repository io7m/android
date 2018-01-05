package org.nypl.simplified.books.controller;

import com.io7m.jnull.NullCheck;

import org.nypl.simplified.books.accounts.AccountType;
import org.nypl.simplified.books.book_database.Book;
import org.nypl.simplified.books.book_database.BookDatabaseType;
import org.nypl.simplified.books.book_registry.BookRegistryType;
import org.nypl.simplified.books.book_registry.BookStatus;
import org.nypl.simplified.books.book_registry.BookWithStatus;
import org.nypl.simplified.books.core.LogUtilities;
import org.nypl.simplified.books.profiles.ProfileType;
import org.slf4j.Logger;

final class ProfileDataLoadTask implements Runnable {

  private static final Logger LOG = LogUtilities.getLog(ProfileDataLoadTask.class);

  private final ProfileType profile;
  private final BookRegistryType book_registry;

  ProfileDataLoadTask(
      final ProfileType profile,
      final BookRegistryType book_registry) {

    this.profile =
        NullCheck.notNull(profile, "Profile");
    this.book_registry =
        NullCheck.notNull(book_registry, "Book registry");
  }

  @Override
  public void run() {
    LOG.debug("load: profile {}", this.profile.displayName());

    for (final AccountType account : this.profile.accounts().values()) {
      LOG.debug("load: profile {} / account {}", this.profile.displayName(), account.id().id());
      final BookDatabaseType books = account.bookDatabase();
      for (final Book book : books.books().values()) {
        this.book_registry.update(BookWithStatus.create(book, BookStatus.fromBook(book)));
      }
    }
  }
}
