package org.nypl.simplified.app.catalog;

import org.nypl.simplified.app.R;
import org.nypl.simplified.app.SimplifiedPart;
import org.nypl.simplified.books.core.BooksFeedSelection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An activity that displays the holds for the current user.
 */

public final class MainHoldsActivity extends MainLocalFeedActivity
{
  private static final Logger LOG = LoggerFactory.getLogger(MainHoldsActivity.class);

  @Override
  protected Logger log() {
    return LOG;
  }

  /**
   * Construct a new activity.
   */

  public MainHoldsActivity()
  {

  }

  @Override protected BooksFeedSelection getLocalFeedTypeSelection()
  {
    return BooksFeedSelection.BOOKS_FEED_HOLDS;
  }

  @Override protected SimplifiedPart navigationDrawerGetPart()
  {
    return SimplifiedPart.PART_HOLDS;
  }

  @Override protected boolean navigationDrawerShouldShowIndicator()
  {
    return true;
  }

  @Override protected String catalogFeedGetEmptyText()
  {
    return this.getResources().getString(R.string.holds_empty);
  }
}
