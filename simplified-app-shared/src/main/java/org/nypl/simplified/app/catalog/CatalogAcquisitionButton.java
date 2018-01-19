package org.nypl.simplified.app.catalog;

import android.app.Activity;
import android.content.res.Resources;
import android.view.ContextThemeWrapper;
import android.widget.Button;
import android.widget.TextView;

import com.io7m.jnull.NullCheck;

import org.nypl.simplified.app.R;
import org.nypl.simplified.books.accounts.AccountType;
import org.nypl.simplified.books.book_database.BookID;
import org.nypl.simplified.books.book_registry.BookRegistryReadableType;
import org.nypl.simplified.books.controller.BooksControllerType;
import org.nypl.simplified.books.controller.ProfilesControllerType;
import org.nypl.simplified.books.feeds.FeedEntryOPDS;
import org.nypl.simplified.opds.core.OPDSAcquisition;
import org.nypl.simplified.opds.core.OPDSAvailabilityHoldable;
import org.nypl.simplified.opds.core.OPDSAvailabilityType;

/**
 * An acquisition button.
 */

public final class CatalogAcquisitionButton extends Button implements CatalogBookButtonType {

  /**
   * Construct an acquisition button.
   */

  public CatalogAcquisitionButton(
      final Activity in_activity,
      final BooksControllerType in_books,
      final ProfilesControllerType in_profiles,
      final BookRegistryReadableType in_book_registry,
      final BookID in_book_id,
      final OPDSAcquisition in_acquisition,
      final FeedEntryOPDS in_entry) {

    super(in_activity);
    final Resources resources = NullCheck.notNull(in_activity.getResources());

    final OPDSAvailabilityType availability = in_entry.getFeedEntry().getAvailability();
    this.setTextSize(12.0f);

    switch (in_acquisition.getType()) {
      case ACQUISITION_OPEN_ACCESS:
        this.setText(NullCheck.notNull(resources.getString(R.string.catalog_book_download)));
        this.setContentDescription(NullCheck.notNull(resources.getString(R.string.catalog_accessibility_book_download)));
        break;
      case ACQUISITION_BORROW: {
        if (availability instanceof OPDSAvailabilityHoldable) {
          this.setText(NullCheck.notNull(resources.getString(R.string.catalog_book_reserve)));
          this.setContentDescription(NullCheck.notNull(resources.getString(R.string.catalog_accessibility_book_reserve)));
        } else {
          this.setText(NullCheck.notNull(resources.getString(R.string.catalog_book_borrow)));
          this.setContentDescription(NullCheck.notNull(resources.getString(R.string.catalog_accessibility_book_borrow)));
        }
        break;
      }
      case ACQUISITION_BUY:
      case ACQUISITION_GENERIC:
      case ACQUISITION_SAMPLE:
      case ACQUISITION_SUBSCRIBE: {
        this.setText(NullCheck.notNull(resources.getString(R.string.catalog_book_download)));
        this.setContentDescription(NullCheck.notNull(resources.getString(R.string.catalog_accessibility_book_download)));
        break;
      }
    }

    this.setOnClickListener(
        new CatalogAcquisitionButtonController(
            in_activity,
            in_profiles,
            in_books,
            in_book_registry,
            in_book_id,
            in_acquisition,
            in_entry));
  }
}
