package org.nypl.simplified.app.catalog;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Color;
import android.widget.TextView;

import com.io7m.jnull.NullCheck;

import org.nypl.simplified.app.R;
import org.nypl.simplified.books.accounts.AccountProvider;
import org.nypl.simplified.books.book_database.BookID;
import org.nypl.simplified.books.controller.BooksControllerType;
import org.nypl.simplified.books.controller.ProfilesControllerType;
import org.nypl.simplified.books.feeds.FeedEntryOPDS;
import org.nypl.simplified.opds.core.OPDSAcquisition;
import org.nypl.simplified.opds.core.OPDSAvailabilityHoldable;
import org.nypl.simplified.opds.core.OPDSAvailabilityType;

/**
 * An acquisition button.
 */

public final class CatalogAcquisitionButton
    extends CatalogLeftPaddedButton implements CatalogBookButtonType {

  /**
   * Construct an acquisition button.
   *
   * @param in_activity         The host activity
   * @param in_account_provider The current account provider
   * @param in_books            The books controller
   * @param in_profiles         The profiles controller
   * @param in_book_id          The ID of the book to be acquired
   * @param in_acquisition      The acquisition
   * @param in_entry            The associated feed entry
   */

  public CatalogAcquisitionButton(
      final Activity in_activity,
      final AccountProvider in_account_provider,
      final BooksControllerType in_books,
      final ProfilesControllerType in_profiles,
      final BookID in_book_id,
      final OPDSAcquisition in_acquisition,
      final FeedEntryOPDS in_entry) {

    super(NullCheck.notNull(in_activity, "Activity"));
    final Resources resources = NullCheck.notNull(in_activity.getResources());

    final OPDSAvailabilityType availability = in_entry.getFeedEntry().getAvailability();
    final TextView text_view = this.getTextView();
    text_view.setTextSize(12.0f);
    text_view.setTextColor(Color.parseColor(in_account_provider.mainColor()));

    this.setBackgroundResource(R.drawable.simplified_button);
    switch (in_acquisition.getType()) {
      case ACQUISITION_OPEN_ACCESS:
        text_view.setText(
            NullCheck.notNull(resources.getString(R.string.catalog_book_download)));
        text_view.setContentDescription(
            NullCheck.notNull(resources.getString(R.string.catalog_accessibility_book_download)));
        break;
      case ACQUISITION_BORROW: {
        if (availability instanceof OPDSAvailabilityHoldable) {
          text_view.setText(
              NullCheck.notNull(resources.getString(R.string.catalog_book_reserve)));
          text_view.setContentDescription(
              NullCheck.notNull(resources.getString(R.string.catalog_accessibility_book_reserve)));
        } else {
          text_view.setText(
              NullCheck.notNull(resources.getString(R.string.catalog_book_borrow)));
          text_view.setContentDescription(
              NullCheck.notNull(resources.getString(R.string.catalog_accessibility_book_borrow)));
        }
        break;
      }
      case ACQUISITION_BUY:
      case ACQUISITION_GENERIC:
      case ACQUISITION_SAMPLE:
      case ACQUISITION_SUBSCRIBE: {
        text_view.setText(
            NullCheck.notNull(resources.getString(R.string.catalog_book_download)));
        text_view.setContentDescription(
            NullCheck.notNull(resources.getString(R.string.catalog_accessibility_book_download)));
        break;
      }
    }

    this.setOnClickListener(
        new CatalogAcquisitionButtonController(
            in_activity, in_profiles, in_books, in_book_id, in_acquisition, in_entry));
  }
}
