package org.nypl.simplified.app.catalog;

import android.app.Activity;
import android.content.res.Resources;
import android.view.ContextThemeWrapper;
import android.widget.Button;
import android.widget.TextView;

import com.io7m.jnull.NullCheck;

import org.nypl.simplified.app.R;
import org.nypl.simplified.app.reader.ReaderActivity;
import org.nypl.simplified.books.accounts.AccountType;
import org.nypl.simplified.books.book_database.BookID;

/**
 * A button that opens a given book for reading.
 */

public final class CatalogBookReadButton extends Button implements CatalogBookButtonType {

  /**
   * The parent activity.
   */

  public CatalogBookReadButton(
      final Activity in_activity,
      final AccountType in_account,
      final BookID in_book_id) {

    super(in_activity);

    final Resources resources =
        NullCheck.notNull(in_activity.getResources(), "Resources");

    this.setTextSize(12.0f);
    this.setText(NullCheck.notNull(resources.getString(R.string.catalog_book_read)));
    this.setContentDescription(NullCheck.notNull(resources.getString(R.string.catalog_accessibility_book_read)));

    this.setOnClickListener(
        view -> ReaderActivity.startActivity(in_activity, in_account, in_book_id));
  }
}
