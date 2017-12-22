package org.nypl.simplified.books.core;

import com.io7m.jfunctional.Option;
import com.io7m.jfunctional.OptionType;
import com.io7m.jfunctional.Some;
import com.io7m.jfunctional.Unit;
import com.io7m.jnull.NullCheck;

import org.nypl.simplified.books.accounts.AccountAuthenticatedHTTP;
import org.nypl.simplified.books.accounts.AccountAuthenticationCredentials;
import org.nypl.simplified.http.core.HTTPAuthType;
import org.nypl.simplified.http.core.HTTPResultError;
import org.nypl.simplified.http.core.HTTPResultException;
import org.nypl.simplified.http.core.HTTPResultMatcherType;
import org.nypl.simplified.http.core.HTTPResultOKType;
import org.nypl.simplified.http.core.HTTPResultType;
import org.nypl.simplified.http.core.HTTPType;
import org.nypl.simplified.opds.core.OPDSAcquisition;
import org.nypl.simplified.opds.core.OPDSAcquisitionFeed;
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry;
import org.nypl.simplified.opds.core.OPDSFeedParserType;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

final class BooksControllerFulFillTask implements Runnable {

  private static final Logger LOG;

  static {
    LOG = LogUtilities.getLog(BooksControllerFulFillTask.class);
  }

  private final OPDSFeedParserType feed_parser;
  private final HTTPType http;
  private final AtomicBoolean running;
  private final BooksControllerType books_controller;
  private final AccountsDatabaseType accounts_database;
  private final URI loans_uri;
  private final OptionType<BookID> book_id;

  BooksControllerFulFillTask(
      final BooksControllerType in_books,
      final AccountsDatabaseType in_accounts_database,
      final HTTPType in_http,
      final OPDSFeedParserType in_feed_parser,
      final AtomicBoolean in_running,
      final URI in_loans_uri,
      final OptionType<BookID> in_book_id) {
    this.books_controller = NullCheck.notNull(in_books);
    this.accounts_database = NullCheck.notNull(in_accounts_database);
    this.http = NullCheck.notNull(in_http);
    this.feed_parser = NullCheck.notNull(in_feed_parser);
    this.running = NullCheck.notNull(in_running);
    this.loans_uri = NullCheck.notNull(in_loans_uri);
    this.book_id = NullCheck.notNull(in_book_id);
  }

  private static OptionType<OPDSAcquisition> getPreferredAcquisition(
      final BookID book_id,
      final List<OPDSAcquisition> acquisitions) {
    NullCheck.notNull(acquisitions);

    if (acquisitions.isEmpty()) {
      LOG.debug("[{}]: no acquisitions, so no best acquisition!", book_id);
      return Option.none();
    }

    OPDSAcquisition best = NullCheck.notNull(acquisitions.get(0));
    for (final OPDSAcquisition current : acquisitions) {
      final OPDSAcquisition nn_current = NullCheck.notNull(current);
      if (BooksControllerFulFillTask.priority(nn_current)
          > BooksControllerFulFillTask.priority(best)) {
        best = nn_current;
      }
    }

    LOG.debug("[{}]: best acquisition of {} was {}", book_id, acquisitions, best);
    return Option.some(best);
  }

  private static int priority(
      final OPDSAcquisition a) {
    switch (a.getType()) {
      case ACQUISITION_BORROW:
        return 6;
      case ACQUISITION_OPEN_ACCESS:
        return 5;
      case ACQUISITION_GENERIC:
        return 4;
      case ACQUISITION_SAMPLE:
        return 3;
      case ACQUISITION_BUY:
        return 2;
      case ACQUISITION_SUBSCRIBE:
        return 1;
    }

    return 0;
  }

  @Override
  public void run() {
    if (this.running.compareAndSet(false, true)) {
      try {
        LOG.debug("running");
        this.sync();

      } catch (final Throwable x) {
        LOG.debug("failed");

      } finally {
        this.running.set(false);
        LOG.debug("completed");
      }
    } else {
      LOG.debug("sync already in progress, exiting");
    }
  }

  private void sync()
      throws Exception {
    final OptionType<AccountAuthenticationCredentials> credentials_opt =
        this.accounts_database.accountGetCredentials();

    if (credentials_opt.isSome()) {

      final AccountAuthenticationCredentials credentials =
          ((Some<AccountAuthenticationCredentials>) credentials_opt).get();
      final HTTPAuthType auth =
          AccountAuthenticatedHTTP.createAuthenticatedHTTP(credentials);

      final HTTPResultType<InputStream> r =
          this.http.get(Option.some(auth), this.loans_uri, 0L);

      r.matchResult(
          new HTTPResultMatcherType<InputStream, Unit, Exception>() {
            @Override
            public Unit onHTTPError(
                final HTTPResultError<InputStream> e)
                throws Exception {
              final String m =
                  String.format("%s: %d: %s", BooksControllerFulFillTask.this.loans_uri, e.getStatus(), e.getMessage());

              switch (e.getStatus()) {
                case HttpURLConnection.HTTP_UNAUTHORIZED: {
                  BooksControllerFulFillTask.this.accounts_database.accountRemoveCredentials();
                  return Unit.unit();
                }
                default: {
                  throw new IOException(m);
                }
              }
            }

            @Override
            public Unit onHTTPException(
                final HTTPResultException<InputStream> e)
                throws Exception {
              throw e.getError();
            }

            @Override
            public Unit onHTTPOK(
                final HTTPResultOKType<InputStream> e)
                throws Exception {
              try {
                BooksControllerFulFillTask.this.fulFillEntries(e);
                return Unit.unit();
              } finally {
                e.close();
              }
            }
          });
    }
  }

  private void fulFillEntries(
      final HTTPResultOKType<InputStream> r_feed)
      throws Exception {
    final BooksStatusCacheType books_status =
        this.books_controller.bookGetStatusCache();

    final OPDSAcquisitionFeed feed =
        this.feed_parser.parse(this.loans_uri, r_feed.getValue());

    /*
     * Handle each book in the received feed.
     */

    final List<OPDSAcquisitionFeedEntry> entries = feed.getFeedEntries();
    for (final OPDSAcquisitionFeedEntry e : entries) {
      final OPDSAcquisitionFeedEntry e_nn = NullCheck.notNull(e);
      final BookID in_book_id = BookID.newIDFromEntry(e_nn);

      if (this.book_id.isNone() || this.book_id.equals(Option.some(in_book_id))) {
        try {
          final OptionType<BookStatusType> stat =
              books_status.booksStatusGet(in_book_id);

          if (stat.isSome() && ((Some<BookStatusType>) stat).get() instanceof BookStatusDownloaded) {
            final OptionType<OPDSAcquisition> a_opt =
                BooksControllerFulFillTask.getPreferredAcquisition(
                    in_book_id, e_nn.getAcquisitions());
            if (a_opt.isSome()) {
              final OPDSAcquisition a = ((Some<OPDSAcquisition>) a_opt).get();
              this.books_controller.bookBorrow(in_book_id, a, e_nn, true);
            }

          }

        } catch (final Throwable x) {
          LOG.error("[{}]: unable to save entry: {}: ", in_book_id.getShortID(), x);
        }
      }
    }
  }

}
