package org.nypl.simplified.books.controller;

import com.io7m.jfunctional.None;
import com.io7m.jfunctional.Option;
import com.io7m.jfunctional.OptionType;
import com.io7m.jfunctional.OptionVisitorType;
import com.io7m.jfunctional.Some;
import com.io7m.jfunctional.Unit;
import com.io7m.jnull.NullCheck;
import com.io7m.junreachable.UnimplementedCodeException;

import org.nypl.drm.core.AdobeAdeptACSMException;
import org.nypl.drm.core.AdobeAdeptLoan;
import org.nypl.simplified.books.accounts.AccountAuthenticatedHTTP;
import org.nypl.simplified.books.accounts.AccountType;
import org.nypl.simplified.books.book_database.Book;
import org.nypl.simplified.books.book_database.BookDatabaseEntryType;
import org.nypl.simplified.books.book_database.BookDatabaseException;
import org.nypl.simplified.books.book_database.BookDatabaseType;
import org.nypl.simplified.books.book_database.BookID;
import org.nypl.simplified.books.book_registry.BookRegistryType;
import org.nypl.simplified.books.book_registry.BookStatusEvent;
import org.nypl.simplified.books.book_registry.BookWithStatus;
import org.nypl.simplified.books.core.BookStatus;
import org.nypl.simplified.books.core.BookStatusDownloadFailed;
import org.nypl.simplified.books.core.BookStatusDownloadInProgress;
import org.nypl.simplified.books.core.BookStatusRequestingLoan;
import org.nypl.simplified.books.core.LogUtilities;
import org.nypl.simplified.downloader.core.DownloadListenerType;
import org.nypl.simplified.downloader.core.DownloadType;
import org.nypl.simplified.downloader.core.DownloaderType;
import org.nypl.simplified.http.core.HTTPAuthType;
import org.nypl.simplified.opds.core.OPDSAcquisition;
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A book borrowing task.
 */

final class BookBorrowTask implements Callable<Unit> {

  private static final String ACSM_CONTENT_TYPE = "application/vnd.adobe.adept+xml";

  private static final Logger LOG = LoggerFactory.getLogger(BookBorrowTask.class);

  private final BookRegistryType book_registry;
  private final BookID book_id;
  private final AccountType account;
  private final OPDSAcquisition acquisition;
  private final OPDSAcquisitionFeedEntry entry;
  private final Book.Builder book_builder;
  private final DownloaderType downloader;
  private final ConcurrentHashMap<BookID, DownloadType> downloads;
  private long download_running_total;
  private BookDatabaseEntryType database_entry;

  BookBorrowTask(
      final DownloaderType downloader,
      final ConcurrentHashMap<BookID, DownloadType> downloads,
      final BookRegistryType book_registry,
      final BookID id,
      final AccountType account,
      final OPDSAcquisition acquisition,
      final OPDSAcquisitionFeedEntry entry) {

    this.downloader =
        NullCheck.notNull(downloader, "Downloader");
    this.downloads =
        NullCheck.notNull(downloads, "Downloads");
    this.book_registry =
        NullCheck.notNull(book_registry, "Book registry");
    this.book_id =
        NullCheck.notNull(id, "ID");
    this.account =
        NullCheck.notNull(account, "Account");
    this.acquisition =
        NullCheck.notNull(acquisition, "Acquisition");
    this.entry =
        NullCheck.notNull(entry, "Entry");

    this.book_builder = Book.builder(this.book_id, this.account.id(), this.entry);
  }

  @Override
  public Unit call() throws Exception {
    execute();
    return Unit.unit();
  }

  private void execute() {

    try {
      LOG.debug("[{}]: starting borrow", this.book_id.brief());
      LOG.debug("[{}]: creating feed entry", this.book_id.brief());

      this.book_registry.update(
          BookWithStatus.create(this.book_builder.build(),
              new BookStatusRequestingLoan(this.book_id)));

      final BookDatabaseType database = this.account.bookDatabase();
      this.database_entry = database.create(this.book_id, this.entry);

      final OPDSAcquisition.Type type = this.acquisition.getType();
      switch (type) {
        case ACQUISITION_BORROW: {
          throw new UnimplementedCodeException();
        }
        case ACQUISITION_GENERIC: {
          throw new UnimplementedCodeException();
        }
        case ACQUISITION_OPEN_ACCESS: {
          LOG.debug("[{}]: acquisition type is {}, performing fulfillment", this.book_id.brief(), type);
          this.downloadAddToCurrent(this.runAcquisitionFulfill(this.entry));
          return;
        }
        case ACQUISITION_BUY:
        case ACQUISITION_SAMPLE:
        case ACQUISITION_SUBSCRIBE: {
          LOG.debug("[{}]: acquisition type is {}, cannot continue!", this.book_id.brief(), type);
          throw new UnsupportedOperationException();
        }
      }
    } catch (final Exception e) {
      LOG.error("[{}]: error: ", this.book_id.brief(), e);
      this.downloadFailed(Option.some(e));
    }
  }

  private DownloadType runAcquisitionFulfill(
      final OPDSAcquisitionFeedEntry entry)
      throws NoUsableAcquisitionException {

    LOG.debug("[{}]: fulfilling book", this.book_id.brief());

    for (final OPDSAcquisition acquisition : entry.getAcquisitions()) {
      switch (acquisition.getType()) {
        case ACQUISITION_GENERIC:
        case ACQUISITION_OPEN_ACCESS: {
          return this.runAcquisitionFulfillDoDownload(acquisition);
        }
        case ACQUISITION_BORROW:
        case ACQUISITION_BUY:
        case ACQUISITION_SAMPLE:
        case ACQUISITION_SUBSCRIBE: {
          break;
        }
      }
    }

    throw new NoUsableAcquisitionException();
  }

  private DownloadType runAcquisitionFulfillDoDownload(final OPDSAcquisition acquisition) {

    /*
     * Downloading may require authentication.
     */

    final OptionType<HTTPAuthType> auth =
        this.account.credentials().map(AccountAuthenticatedHTTP::createAuthenticatedHTTP);

    LOG.debug("[{}]: starting download", this.book_id.brief());

    /*
     * Point the downloader at the acquisition link. The result will either
     * be an EPUB or an ACSM file. ACSM files have to be "fulfilled" after
     * downloading by passing them to the Adobe DRM connector.
     */

    return this.downloader.download(acquisition.getURI(), auth, new FulfillmentListener(this));
  }

  private void downloadFailed(final OptionType<Throwable> exception) {
    LogUtilities.errorWithOptionalException(LOG, "download failed", exception);

    this.book_registry.update(
        BookWithStatus.create(this.book_builder.build(),
            new BookStatusDownloadFailed(this.book_id, exception, Option.none())));
  }

  private static final class NoUsableAcquisitionException extends Exception {

  }

  private static final class FetchingACSMFailed extends Exception {
    FetchingACSMFailed(final OptionType<Throwable> exception) {
      super(exception.accept(new OptionVisitorType<Throwable, Throwable>() {
        @Override
        public Throwable none(final None<Throwable> none) {
          return null;
        }

        @Override
        public Throwable some(final Some<Throwable> some) {
          return some.get();
        }
      }));
    }
  }

  private static final class FetchingBookFailed extends Exception {
    FetchingBookFailed(final OptionType<Throwable> exception) {
      super(exception.accept(new OptionVisitorType<Throwable, Throwable>() {
        @Override
        public Throwable none(final None<Throwable> none) {
          return null;
        }

        @Override
        public Throwable some(final Some<Throwable> some) {
          return some.get();
        }
      }));
    }
  }

  private void downloadDataReceived(
      final long running_total,
      final long expected_total) {

    /*
     * Because "data received" updates happen at such a huge rate, we want
     * to ensure that updates to the book status are rate limited to avoid
     * overwhelming the UI. Book updates are only published at the start of
     * downloads, or if a large enough chunk of data has now been received
     * to justify a UI update.
     */

    final boolean at_start = running_total == 0L;
    final double divider = (double) expected_total / 10.0;
    final boolean long_enough =
        (double) running_total > (double) this.download_running_total + divider;

    if (long_enough || at_start) {
      this.book_registry.update(
          BookWithStatus.create(
              this.book_builder.build(),
              new BookStatusDownloadInProgress(
                  this.book_id, running_total, expected_total, Option.none())));
      this.download_running_total = running_total;
    }
  }

  private static final class FulfillmentListener implements DownloadListenerType {

    private final BookBorrowTask task;

    FulfillmentListener(final BookBorrowTask task) {
      this.task = NullCheck.notNull(task, "Task");
    }

    @Override
    public void onDownloadStarted(
        final DownloadType d,
        final long expected_total) {
      this.task.downloadDataReceived(0L, expected_total);
    }

    @Override
    public void onDownloadDataReceived(
        final DownloadType d,
        final long running_total,
        final long expected_total) {
      this.task.downloadDataReceived(running_total, expected_total);
    }

    @Override
    public void onDownloadCancelled(final DownloadType d) {
      this.task.downloadCancelled();
    }

    @Override
    public void onDownloadFailed(
        final DownloadType d,
        final int status,
        final long running_total,
        final OptionType<Throwable> exception) {

      /*
       * If the content type indicates that the file was an ACSM file,
       * explicitly indicate that it was fetching an ACSM that failed.
       * This allows the UI to assign blame!
       */

      final Throwable ex;
      final String acsm_type = ACSM_CONTENT_TYPE;
      if (acsm_type.equals(d.getContentType())) {
        ex = new FetchingACSMFailed(exception);
      } else {
        ex = new FetchingBookFailed(exception);
      }

      this.task.downloadFailed(Option.some(ex));
    }

    @Override
    public void onDownloadCompleted(
        final DownloadType d,
        final File file) throws IOException {
      this.task.downloadCompleted(d, file);
    }
  }

  private void downloadCompleted(
      final DownloadType download,
      final File file) {

    try {
      LOG.debug("[{}]: download {} completed for {}", this.book_id.brief(), download, file);
      this.downloadRemoveFromCurrent();

      /*
       * If the downloaded file is an ACSM fulfillment token, then the
       * book must be downloaded using the Adobe DRM interface.
       */

      final String content_type = download.getContentType();
      LOG.debug("[{}]: content type is {}", this.book_id.brief(), content_type);

      if (ACSM_CONTENT_TYPE.equals(content_type)) {
        this.runFulfillACSM(file);
      } else {

        /*
         * Otherwise, assume it's an EPUB and keep it.
         */

        final OptionType<AdobeAdeptLoan> none = Option.none();
        this.saveEPUBAndRights(file, none);
      }
    } catch (final AdobeAdeptACSMException e) {
      LOG.error("onDownloadCompleted: acsm exception: ", e);
      this.downloadFailed(Option.some(e));
    } catch (final BookDatabaseException e) {
      LOG.error("onDownloadCompleted: book database exception: ", e);
      this.downloadFailed(Option.some(e));
    }
  }

  private void downloadAddToCurrent(final DownloadType download) {
    LOG.debug("[{}]: adding download {}", this.book_id.brief(), download);
    this.downloads.put(this.book_id, download);
  }

  private void downloadRemoveFromCurrent() {
    LOG.debug("[{}]: removing download", this.book_id.brief());
    this.downloads.remove(this.book_id);
  }

  private void runFulfillACSM(final File file) throws AdobeAdeptACSMException {
    throw new UnimplementedCodeException();
  }

  private void saveEPUBAndRights(
      final File file,
      final OptionType<AdobeAdeptLoan> loan_opt)
      throws BookDatabaseException {

    this.database_entry.writeEPUB(file);
    loan_opt.mapPartial_(loan -> this.database_entry.writeAdobeLoan(loan));
  }

  private void downloadCancelled() {
    try {
      final Book book = this.database_entry.book();
      this.book_registry.update(BookWithStatus.create(book, BookStatus.fromBook(book)));
    } finally {
      this.downloadRemoveFromCurrent();
    }
  }
}
