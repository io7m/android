package org.nypl.simplified.app.catalog;

import android.app.Activity;
import android.app.FragmentManager;
import android.content.res.Resources;
import android.widget.TextView;

import com.io7m.jnull.NullCheck;

import org.nypl.simplified.app.R;
import org.nypl.simplified.books.accounts.AccountType;
import org.nypl.simplified.books.book_database.BookID;
import org.nypl.simplified.books.controller.BooksControllerType;

/**
 * A button for revoking loans or holds.
 */

public final class CatalogBookRevokeButton
    extends CatalogLeftPaddedButton implements CatalogBookButtonType {

  /**
   * Construct a button.
   */

  public CatalogBookRevokeButton(
      final Activity in_activity,
      final BooksControllerType in_books,
      final AccountType in_account,
      final BookID in_book_id,
      final CatalogBookRevokeType in_revoke_type) {
    super(in_activity);

    NullCheck.notNull(in_book_id);
    NullCheck.notNull(in_revoke_type);

    final Resources resources = NullCheck.notNull(in_activity.getResources());
    final TextView text_view = this.getTextView();

    text_view.setTextSize(12.0f);
    this.setBackgroundResource(R.drawable.simplified_button);

    switch (in_revoke_type) {
      case REVOKE_LOAN: {
        text_view.setText(
            NullCheck.notNull(resources.getString(R.string.catalog_book_revoke_loan)));
        text_view.setContentDescription(
            NullCheck.notNull(resources.getString(R.string.catalog_accessibility_book_revoke_loan)));
        break;
      }
      case REVOKE_HOLD: {
        text_view.setText(
            NullCheck.notNull(resources.getString(R.string.catalog_book_revoke_hold)));
        text_view.setContentDescription(
            NullCheck.notNull(resources.getString(R.string.catalog_accessibility_book_revoke_hold)));
        break;
      }
    }

    this.setOnClickListener(view -> {
      final CatalogBookRevokeDialog d =
          CatalogBookRevokeDialog.newDialog(
              in_revoke_type, () -> in_books.bookRevoke(in_account, in_book_id));

      final FragmentManager fm = in_activity.getFragmentManager();
      d.show(fm, "revoke-confirm");
    });
  }
}
