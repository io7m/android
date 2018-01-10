package org.nypl.simplified.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.io7m.jnull.NullCheck;
import com.io7m.junreachable.UnimplementedCodeException;

import org.nypl.simplified.app.catalog.MainCatalogActivity;
import org.nypl.simplified.books.accounts.AccountProviderCollection;
import org.nypl.simplified.books.core.LogUtilities;
import org.slf4j.Logger;

import static org.nypl.simplified.app.Simplified.WantActionBar.WANT_NO_ACTION_BAR;

/**
 * An activity that requires the user to either pick a specific account provider, or allows them
 * to read the SimplyE collection.
 */

public class MainWelcomeActivity extends Activity {
  private static final Logger LOG;

  static {
    LOG = LogUtilities.getLog(MainWelcomeActivity.class);
  }

  /**
   * Construct an activity.
   */

  public MainWelcomeActivity() {

  }

  @Override
  protected void onCreate(
      final Bundle state) {
    LOG.debug("onCreate");

    this.setTheme(Simplified.getCurrentTheme(WANT_NO_ACTION_BAR));
    super.onCreate(state);
    this.setContentView(R.layout.welcome);

    final Button library_button =
        NullCheck.notNull((Button) findViewById(R.id.welcome_library));
    final Button instant_button =
        NullCheck.notNull((Button) findViewById(R.id.welcome_instant));

    final AccountProviderCollection account_providers =
        Simplified.getAccountProviders();

    library_button.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(final View view) {
        MainWelcomeActivity.this.openAccountsList();
      }
    });

    instant_button.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(final View view) {

      }
    });
  }

  private void openAccountsList() {
    throw new UnimplementedCodeException();
  }

  private void openCatalog() {
    final Intent i = new Intent(this, MainCatalogActivity.class);
    i.putExtra("reload", true);
    this.startActivity(i);
    this.overridePendingTransition(0, 0);
    this.finish();
  }
}
