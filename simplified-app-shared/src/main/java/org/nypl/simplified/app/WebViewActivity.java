package org.nypl.simplified.app;

import android.app.ActionBar;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.MenuItem;
import android.webkit.WebSettings;
import android.webkit.WebView;

import com.io7m.jnull.NullCheck;
import com.io7m.jnull.Nullable;

import org.nypl.simplified.books.core.LogUtilities;
import org.slf4j.Logger;

/**
 * A mindlessly simple activity that displays a given URI in a full-screen web
 * view.
 */

public final class WebViewActivity extends NavigationDrawerActivity {
  /**
   * The name used to pass URIs to the activity.
   */

  public static final String URI_KEY =
      "org.nypl.simplified.app.WebViewActivity.uri";

  /**
   * The name used to pass titles to the activity.
   */

  public static final String TITLE_KEY =
      "org.nypl.simplified.app.WebViewActivity.title";

  private static final Logger LOG;

  static {
    LOG = LogUtilities.getLog(WebViewActivity.class);
  }

  private WebView web_view;
  private String title;

  /**
   * Construct an activity.
   */

  public WebViewActivity() {

  }

  /**
   * Configure the given argument bundle for use in instantiating a {@link
   * WebViewActivity}.
   *
   * @param b     The argument bundle
   * @param title The title that will be displayed
   * @param uri   The URI that will be loaded
   */

  public static void setActivityArguments(
      final Bundle b,
      final String uri,
      final String title) {

    NullCheck.notNull(b);
    NullCheck.notNull(uri);
    NullCheck.notNull(title);

    b.putString(WebViewActivity.URI_KEY, uri);
    b.putString(WebViewActivity.TITLE_KEY, title);
  }

  @Override
  public boolean onOptionsItemSelected(
      final @Nullable MenuItem item_mn) {
    final MenuItem item = NullCheck.notNull(item_mn);
    switch (item.getItemId()) {

      case android.R.id.home: {
        onBackPressed();
        return true;
      }

      default: {
        return super.onOptionsItemSelected(item);
      }
    }
  }

  @Override
  protected boolean navigationDrawerShouldShowIndicator() {
    return false;
  }

  @Override
  protected String navigationDrawerGetActivityTitle(final Resources resources) {
    return this.title;
  }

  @Override
  protected void onCreate(final Bundle state) {
    super.onCreate(state);

    this.setContentView(R.layout.webview);

    final Intent i = NullCheck.notNull(this.getIntent());
    final String uri =
        NullCheck.notNull(i.getStringExtra(WebViewActivity.URI_KEY));
    this.title =
        NullCheck.notNull(i.getStringExtra(WebViewActivity.TITLE_KEY));

    WebViewActivity.LOG.debug("uri: {}", uri);
    WebViewActivity.LOG.debug("title: {}", title);

    this.web_view =
        NullCheck.notNull(this.findViewById(R.id.web_view));

    this.web_view.setVerticalScrollBarEnabled(false);

    final ActionBar bar = this.getActionBar();
    bar.setTitle(title);
    if (android.os.Build.VERSION.SDK_INT < 21) {
      bar.setDisplayHomeAsUpEnabled(false);
      bar.setHomeButtonEnabled(true);
      bar.setIcon(R.drawable.ic_arrow_back_white_24dp);
    } else {
      bar.setHomeAsUpIndicator(R.drawable.ic_arrow_back_white_24dp);
      bar.setDisplayHomeAsUpEnabled(true);
      bar.setHomeButtonEnabled(false);
    }

    final WebSettings settings = this.web_view.getSettings();
    settings.setAllowFileAccess(true);
    settings.setAllowContentAccess(true);
    settings.setSupportMultipleWindows(false);
    settings.setAllowUniversalAccessFromFileURLs(false);
    settings.setJavaScriptEnabled(false);

    this.web_view.loadUrl(uri);
  }
}
