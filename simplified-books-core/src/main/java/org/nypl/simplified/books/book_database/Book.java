package org.nypl.simplified.books.book_database;

import com.google.auto.value.AutoValue;
import com.io7m.jfunctional.Option;
import com.io7m.jfunctional.OptionType;

import org.nypl.drm.core.AdobeAdeptLoan;
import org.nypl.simplified.books.accounts.AccountID;
import org.nypl.simplified.opds.core.OPDSAcquisitionFeedEntry;

import java.io.File;

@AutoValue
public abstract class Book {

  Book() {

  }

  public abstract BookID id();

  public abstract AccountID account();

  public abstract OptionType<File> cover();

  public abstract OptionType<File> file();

  public abstract OPDSAcquisitionFeedEntry entry();

  public abstract OptionType<AdobeAdeptLoan> adobeLoan();

  public abstract Builder toBuilder();

  public static Builder builder(
      final BookID book_id,
      final AccountID account_id,
      final OPDSAcquisitionFeedEntry entry) {

    return new AutoValue_Book.Builder()
        .setAdobeLoan(Option.<AdobeAdeptLoan>none())
        .setCover(Option.<File>none())
        .setFile(Option.<File>none())
        .setEntry(entry)
        .setId(book_id)
        .setAccount(account_id);
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setId(
        BookID id);

    public abstract Builder setAccount(
        AccountID id);

    public abstract Builder setCover(
        OptionType<File> cover_option);

    public final Builder setCover(
        final File cover) {
      return setCover(Option.some(cover));
    }

    public abstract Builder setFile(
        OptionType<File> file);

    public final Builder setFile(
        final File file) {
      return setFile(Option.some(file));
    }

    public abstract Builder setEntry(
        OPDSAcquisitionFeedEntry entry);

    public abstract Builder setAdobeLoan(
        OptionType<AdobeAdeptLoan> loan);

    public final Builder setAdobeLoan(
        final AdobeAdeptLoan loan) {
      return setAdobeLoan(Option.some(loan));
    }

    public abstract Book build();

  }
}
