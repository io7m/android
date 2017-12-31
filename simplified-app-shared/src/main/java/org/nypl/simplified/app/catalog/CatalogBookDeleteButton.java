package org.nypl.simplified.app.catalog;

import android.app.Activity;
import android.app.FragmentManager;
import android.content.res.Resources;
import android.view.View;
import android.widget.TextView;

import com.io7m.jnull.NullCheck;
import com.io7m.jnull.Nullable;
import com.io7m.junreachable.UnimplementedCodeException;

import org.nypl.simplified.app.R;
import org.nypl.simplified.books.book_database.BookID;

/**
 * A button for deleting books.
 */

public final class CatalogBookDeleteButton extends CatalogLeftPaddedButton
    implements CatalogBookButtonType {
  /**
   * Construct a button.
   *
   * @param in_activity The host activity
   * @param in_book_id  The book ID
   */

  public CatalogBookDeleteButton(
      final Activity in_activity,
      final BookID in_book_id) {
    super(in_activity);

    final Resources resources = NullCheck.notNull(in_activity.getResources());

    final TextView text_view = this.getTextView();
    text_view.setText(NullCheck.notNull(resources.getString(R.string.catalog_book_delete)));
    text_view.setContentDescription(NullCheck.notNull(resources.getString(R.string.catalog_accessibility_book_delete)));
    text_view.setTextSize(12.0f);
    text_view.setTextColor(getBrandingColor());

    this.setBackgroundResource(R.drawable.simplified_button);

    this.setOnClickListener(
        new OnClickListener() {
          @Override
          public void onClick(
              final @Nullable View v) {
            final CatalogBookDeleteDialog d = CatalogBookDeleteDialog.newDialog();
            d.setOnConfirmListener(
                new Runnable() {
                  @Override
                  public void run() {
                    throw new UnimplementedCodeException();
                  }
                });
            final FragmentManager fm = in_activity.getFragmentManager();
            d.show(fm, "delete-confirm");
          }
        });
  }

  private static int getBrandingColor() {
    throw new UnimplementedCodeException();
  }
}
