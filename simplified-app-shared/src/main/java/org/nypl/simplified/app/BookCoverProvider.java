package org.nypl.simplified.app;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.widget.ImageView;

import com.io7m.jfunctional.FunctionType;
import com.io7m.jfunctional.None;
import com.io7m.jfunctional.Option;
import com.io7m.jfunctional.OptionType;
import com.io7m.jfunctional.OptionVisitorType;
import com.io7m.jfunctional.Some;
import com.io7m.jnull.NullCheck;
import com.io7m.jnull.Nullable;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;

import org.nypl.simplified.app.catalog.CatalogBookCoverGeneratorRequestHandler;
import org.nypl.simplified.app.catalog.CatalogBookCoverGeneratorType;
import org.nypl.simplified.app.utilities.UIThread;
import org.nypl.simplified.books.book_registry.BookRegistryReadableType;
import org.nypl.simplified.books.book_registry.BookWithStatus;
import org.nypl.simplified.books.core.BookDatabaseEntrySnapshot;
import org.nypl.simplified.books.book_database.BookID;
import org.nypl.simplified.books.core.FeedEntryOPDS;
import org.nypl.simplified.books.core.LogUtilities;
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry;
import org.slf4j.Logger;

import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * The default implementation of the {@link BookCoverProviderType} interface.
 */

public final class BookCoverProvider implements BookCoverProviderType {

  private static final String COVER_TAG;
  private static final Logger LOG;
  private static final String THUMBNAIL_TAG;

  static {
    LOG = LogUtilities.getLog(BookCoverProvider.class);
    THUMBNAIL_TAG = "thumbnail";
    COVER_TAG = "cover";
  }

  private final BookRegistryReadableType books_registry;
  private final CatalogBookCoverGeneratorType cover_gen;
  private final Picasso picasso;

  private BookCoverProvider(
      final Picasso in_picasso,
      final BookRegistryReadableType in_book_registry,
      final CatalogBookCoverGeneratorType in_cover_generator) {

    this.picasso =
        NullCheck.notNull(in_picasso, "Picasso");
    this.books_registry =
        NullCheck.notNull(in_book_registry, "Book Registry");
    this.cover_gen =
        NullCheck.notNull(in_cover_generator, "Cover generator");
  }

  private static URI generateCoverURI(
      final FeedEntryOPDS e,
      final CatalogBookCoverGeneratorType cg) {

    final OPDSAcquisitionFeedEntry eo = e.getFeedEntry();
    final String title = eo.getTitle();
    final String author;
    final List<String> authors = eo.getAuthors();
    if (authors.isEmpty()) {
      author = "";
    } else {
      author = NullCheck.notNull(authors.get(0));
    }
    return cg.generateURIForTitleAuthor(title, author);
  }

  private static void load(
      final FeedEntryOPDS e,
      final ImageView i,
      final int w,
      final int h,
      final @Nullable Callback c,
      final Picasso p,
      final CatalogBookCoverGeneratorType cg,
      final String tag,
      final OptionType<URI> uri_opt) {

    final URI uri_generated = BookCoverProvider.generateCoverURI(e, cg);
    if (uri_opt.isSome()) {
      final URI uri_specified = ((Some<URI>) uri_opt).get();

      LOG.debug("{}: {}: loading specified uri {}", tag, e.getBookID(), uri_specified);

      final RequestCreator r = p.load(uri_specified.toString());
      r.tag(tag);
      r.resize(w, h);
      r.into(
          i, new Callback() {
            @Override
            public void onError() {
              LOG.debug("{}: {}: failed to load uri {}, falling back to generation",
                  tag, e.getBookID(), uri_specified);

              final RequestCreator fallback_r = p.load(uri_generated.toString());
              fallback_r.tag(tag);
              fallback_r.resize(w, h);
              fallback_r.into(i, c);
            }

            @Override
            public void onSuccess() {
              if (c != null) {
                c.onSuccess();
              }
            }
          });
    } else {
      LOG.debug("{}: {}: loading generated uri {}", tag, e.getBookID(), uri_generated);

      final RequestCreator r = p.load(uri_generated.toString());
      r.tag(tag);
      r.resize(w, h);
      r.into(i, c);
    }
  }

  /**
   * Create a new cover provider.
   *
   * @param in_c             The application context
   * @param in_book_registry The book registry
   * @param in_generator     A cover generator
   * @param in_exec          An executor
   * @return A new cover provider
   */

  public static BookCoverProviderType newCoverProvider(
      final Context in_c,
      final BookRegistryReadableType in_book_registry,
      final CatalogBookCoverGeneratorType in_generator,
      final ExecutorService in_exec) {

    NullCheck.notNull(in_c, "Context");
    NullCheck.notNull(in_book_registry, "Book provider");
    NullCheck.notNull(in_generator, "Generator");
    NullCheck.notNull(in_exec, "Executor");

    final Resources rr = in_c.getResources();
    final Picasso.Builder pb = new Picasso.Builder(in_c);
    pb.defaultBitmapConfig(Bitmap.Config.RGB_565);
    pb.indicatorsEnabled(rr.getBoolean(R.bool.debug_picasso_cache_indicators));
    pb.loggingEnabled(rr.getBoolean(R.bool.debug_picasso_logging));
    pb.addRequestHandler(new CatalogBookCoverGeneratorRequestHandler(in_generator));
    pb.executor(in_exec);

    final Picasso p = NullCheck.notNull(pb.build());
    return new BookCoverProvider(p, in_book_registry, in_generator);
  }

  private OptionType<URI> getCoverURI(final FeedEntryOPDS e) {
    final OPDSAcquisitionFeedEntry eo = e.getFeedEntry();
    final BookID id = e.getBookID();
    final OptionType<BookWithStatus> book_opt = Option.of(books_registry.books().get(id));
    return book_opt.accept(new OptionVisitorType<BookWithStatus, OptionType<URI>>() {
      @Override
      public OptionType<URI> none(final None<BookWithStatus> none) {
        return eo.getCover();
      }

      @Override
      public OptionType<URI> some(final Some<BookWithStatus> some_book) {
        return some_book.get().book().cover().accept(
            new OptionVisitorType<File, OptionType<URI>>() {
              @Override
              public OptionType<URI> none(final None<File> none) {
                return eo.getCover();
              }

              @Override
              public OptionType<URI> some(final Some<File> some_file) {
                return Option.some(some_file.get().toURI());
              }
            });
      }
    });
  }

  private OptionType<URI> getThumbnailURI(final FeedEntryOPDS e) {
    final OPDSAcquisitionFeedEntry eo = e.getFeedEntry();
    final BookID id = e.getBookID();
    final OptionType<BookWithStatus> book_opt = Option.of(books_registry.books().get(id));
    return book_opt.accept(new OptionVisitorType<BookWithStatus, OptionType<URI>>() {
      @Override
      public OptionType<URI> none(final None<BookWithStatus> none) {
        return eo.getThumbnail();
      }

      @Override
      public OptionType<URI> some(final Some<BookWithStatus> some_book) {
        return some_book.get().book().cover().accept(
            new OptionVisitorType<File, OptionType<URI>>() {
              @Override
              public OptionType<URI> none(final None<File> none) {
                return eo.getThumbnail();
              }

              @Override
              public OptionType<URI> some(final Some<File> some_file) {
                return Option.some(some_file.get().toURI());
              }
            });
      }
    });
  }

  @Override
  public void loadCoverInto(
      final FeedEntryOPDS e,
      final ImageView i,
      final int w,
      final int h) {
    NullCheck.notNull(e);
    NullCheck.notNull(i);
    this.loadCoverIntoActual(e, i, w, h, null);
  }

  private void loadCoverIntoActual(
      final FeedEntryOPDS e,
      final ImageView i,
      final int w,
      final int h,
      final @Nullable Callback c) {
    final OPDSAcquisitionFeedEntry eo = e.getFeedEntry();

    LOG.debug("{}: loadCoverInto {}", e.getBookID(), eo.getID());

    UIThread.checkIsUIThread();

    final OptionType<URI> uri_opt = this.getCoverURI(e);
    BookCoverProvider.load(e, i, w, h, c, this.picasso, this.cover_gen, COVER_TAG, uri_opt);
  }

  @Override
  public void loadCoverIntoWithCallback(
      final FeedEntryOPDS e,
      final ImageView i,
      final int w,
      final int h,
      final Callback c) {
    NullCheck.notNull(e);
    NullCheck.notNull(i);
    NullCheck.notNull(c);
    this.loadCoverIntoActual(e, i, w, h, c);
  }

  @Override
  public void loadingThumbailsPause() {
    this.picasso.pauseTag(BookCoverProvider.THUMBNAIL_TAG);
  }

  @Override
  public void loadingThumbnailsContinue() {
    this.picasso.resumeTag(BookCoverProvider.THUMBNAIL_TAG);
  }

  @Override
  public void loadThumbnailInto(
      final FeedEntryOPDS e,
      final ImageView i,
      final int w,
      final int h) {
    NullCheck.notNull(e);
    NullCheck.notNull(i);
    this.loadThumbnailIntoActual(e, i, w, h, null);
  }

  private void loadThumbnailIntoActual(
      final FeedEntryOPDS e,
      final ImageView i,
      final int w,
      final int h,
      final @Nullable Callback c) {
    final OPDSAcquisitionFeedEntry eo = e.getFeedEntry();

    LOG.debug("{}: loadThumbnailInto {}", e.getBookID(), eo.getID());

    UIThread.checkIsUIThread();

    final OptionType<URI> uri_opt = this.getThumbnailURI(e);
    BookCoverProvider.load(e, i, w, h, c, this.picasso, this.cover_gen, THUMBNAIL_TAG, uri_opt);
  }

  @Override
  public void loadThumbnailIntoWithCallback(
      final FeedEntryOPDS e,
      final ImageView i,
      final int w,
      final int h,
      final Callback c) {
    NullCheck.notNull(e);
    NullCheck.notNull(i);
    NullCheck.notNull(c);
    this.loadThumbnailIntoActual(e, i, w, h, c);
  }
}
