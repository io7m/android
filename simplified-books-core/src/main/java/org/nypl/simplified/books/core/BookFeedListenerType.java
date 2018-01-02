package org.nypl.simplified.books.core;

import org.nypl.simplified.books.feeds.FeedWithoutGroups;

/**
 * The type of listeners for generating book feeds.
 */

public interface BookFeedListenerType
{
  /**
   * Generating the feed failed.
   *
   * @param x The exception raised
   */

  void onBookFeedFailure(
    Throwable x);

  /**
   * Generating the feed succeeded.
   *
   * @param f The feed
   */

  void onBookFeedSuccess(
    FeedWithoutGroups f);
}
