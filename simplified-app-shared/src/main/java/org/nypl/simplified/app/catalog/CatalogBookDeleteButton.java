package org.nypl.simplified.app.catalog;

import android.app.Activity;
import android.app.FragmentManager;
import android.content.res.Resources;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.io7m.jnull.NullCheck;
import com.io7m.jnull.Nullable;
import com.io7m.junreachable.UnimplementedCodeException;

import org.nypl.simplified.app.R;
import org.nypl.simplified.books.accounts.AccountType;
import org.nypl.simplified.books.book_database.BookID;
import org.nypl.simplified.books.controller.BooksControllerType;

/**
 * A button for deleting books.
 */

public final class CatalogBookDeleteButton extends Button implements CatalogBookButtonType {

  /**
   * Construct a button.
   */

  public CatalogBookDeleteButton(
      final Activity in_activity,
      final BooksControllerType in_books_controller,
      final AccountType in_account,
      final BookID in_book_id) {

    super(in_activity);

    final Resources resources = NullCheck.notNull(in_activity.getResources());

    this.setText(NullCheck.notNull(resources.getString(R.string.catalog_book_delete)));
    this.setContentDescription(NullCheck.notNull(resources.getString(R.string.catalog_accessibility_book_delete)));
    this.setTextSize(12.0f);

    this.setOnClickListener(view -> {
      final CatalogBookDeleteDialog d = CatalogBookDeleteDialog.newDialog();
      d.setOnConfirmListener(() -> in_books_controller.bookDelete(in_account, in_book_id));
      final FragmentManager fm = in_activity.getFragmentManager();
      d.show(fm, "delete-confirm");
    });
  }
}
