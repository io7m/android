package org.nypl.simplified.app.catalog;

import android.app.Activity;
import android.app.FragmentManager;
import android.widget.Button;

import com.io7m.jnull.NullCheck;

import org.nypl.simplified.app.R;
import org.nypl.simplified.assertions.Assertions;
import org.nypl.simplified.books.feeds.FeedFacetType;

import java.util.ArrayList;

/**
 * A  button that shows a list of facets.
 */

public final class CatalogFacetButton extends Button {

  /**
   * Construct a new button.
   *
   * @param in_activity   The host activity
   * @param in_group_name The facet group name
   * @param in_group      The facet group
   * @param in_listener   A listener that receives selections
   */

  public CatalogFacetButton(
      final Activity in_activity,
      final String in_group_name,
      final ArrayList<FeedFacetType> in_group,
      final CatalogFacetSelectionListenerType in_listener) {
    super(in_activity);

    NullCheck.notNull(in_group);
    NullCheck.notNull(in_group_name);
    NullCheck.notNull(in_listener);

    Assertions.checkPrecondition(
        in_group.isEmpty() == false, "Facet group is not empty");

    FeedFacetType active_maybe = NullCheck.notNull(in_group.get(0));
    for (final FeedFacetType f : in_group) {
      if (f.facetIsActive()) {
        active_maybe = NullCheck.notNull(f);
        break;
      }
    }

    final FeedFacetType active = NullCheck.notNull(active_maybe);
    this.setTextSize(12.0f);
    this.setBackgroundResource(R.drawable.simplified_button);

    this.setText(active.facetGetTitle());
    this.setOnClickListener(view -> {
      final FragmentManager fm = in_activity.getFragmentManager();
      final CatalogFacetDialog dialog = CatalogFacetDialog.newDialog(in_group_name, in_group);
      dialog.setFacetSelectionListener(facet -> {
        dialog.dismiss();
        in_listener.onFacetSelected(facet);
      });
      dialog.show(fm, "facet-dialog");
    });
  }
}
