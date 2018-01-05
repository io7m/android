package org.nypl.simplified.app.catalog;

import android.app.Activity;
import android.content.res.Resources;
import android.widget.TextView;

import com.io7m.jnull.NullCheck;
import com.io7m.junreachable.UnimplementedCodeException;

import org.nypl.simplified.app.R;
import org.nypl.simplified.app.reader.ReaderActivity;
import org.nypl.simplified.books.accounts.AccountType;
import org.nypl.simplified.books.book_database.BookID;
import org.nypl.simplified.books.controller.BooksControllerType;
import org.nypl.simplified.books.feeds.FeedEntryOPDS;

/**
 * A button that opens a given book for reading.
 */

public final class CatalogBookReadButton
    extends CatalogLeftPaddedButton implements CatalogBookButtonType {

  /**
   * The parent activity.
   */

  public CatalogBookReadButton(
      final Activity in_activity,
      final AccountType in_account,
      final BookID in_book_id,
      final FeedEntryOPDS in_entry) {

    super(in_activity);

    final Resources resources =
        NullCheck.notNull(in_activity.getResources(), "Resources");

    this.setBackgroundResource(R.drawable.simplified_button);

    final TextView text_view = this.getTextView();
    text_view.setTextSize(12.0f);
    text_view.setText(NullCheck.notNull(resources.getString(R.string.catalog_book_read)));
    text_view.setContentDescription(NullCheck.notNull(resources.getString(R.string.catalog_accessibility_book_read)));

    this.setOnClickListener(
        view -> ReaderActivity.startActivity(in_activity, in_account, in_book_id));
  }
}
