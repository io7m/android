package org.nypl.simplified.books.controller;

import com.io7m.jfunctional.Unit;
import com.io7m.jnull.NullCheck;

import org.nypl.simplified.books.book_database.BookDatabaseType;
import org.nypl.simplified.books.book_database.BookID;
import org.nypl.simplified.books.book_registry.BookRegistryType;
import org.nypl.simplified.downloader.core.DownloadType;
import org.nypl.simplified.downloader.core.DownloaderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

final class BookDownloadCancelTask implements Callable<Unit> {

  private static final Logger LOG = LoggerFactory.getLogger(BookDeleteTask.class);

  private final DownloaderType downloader;
  private final ConcurrentHashMap<BookID, DownloadType> downloads;
  private final BookDatabaseType book_database;
  private final BookRegistryType book_registry;
  private final BookID id;

  BookDownloadCancelTask(
      final DownloaderType downloader,
      final ConcurrentHashMap<BookID, DownloadType> downloads,
      final BookDatabaseType book_database,
      final BookRegistryType book_registry,
      final BookID id) {

    this.downloader = NullCheck.notNull(downloader, "downloader");
    this.downloads = NullCheck.notNull(downloads, "downloads");
    this.book_database = NullCheck.notNull(book_database, "book_database");
    this.book_registry = NullCheck.notNull(book_registry, "book_registry");
    this.id = NullCheck.notNull(id, "id");
  }

  @Override
  public Unit call() throws Exception {
    LOG.debug("[{}] download cancel", this.id.brief());

    final DownloadType d = this.downloads.get(this.id);
    if (d != null) {
      LOG.debug("[{}] cancelling download {}", d);
      d.cancel();
      this.downloads.remove(this.id);
    }

    return Unit.unit();
  }
}
