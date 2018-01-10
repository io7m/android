package org.nypl.simplified.app;

import android.content.Intent;
import android.os.Bundle;

import com.io7m.jfunctional.OptionType;
import com.io7m.jfunctional.Some;

import org.nypl.simplified.app.catalog.MainCatalogActivity;
import org.nypl.simplified.books.core.DocumentStoreType;
import org.nypl.simplified.books.core.EULAType;
import org.nypl.simplified.books.core.LogUtilities;
import org.slf4j.Logger;

import java.util.Timer;
import java.util.TimerTask;

import static org.nypl.simplified.app.Simplified.WantActionBar.WANT_NO_ACTION_BAR;

/**
 * A splash screen activity that either shows a license agreement, or simply
 * starts up another activity without displaying anything if the user has
 * already agreed to the license.
 */

public class MainSplashActivity extends SimplifiedActivity {
  private static final Logger LOG;

  static {
    LOG = LogUtilities.getLog(MainSplashActivity.class);
  }

  /**
   * Construct an activity.
   */

  public MainSplashActivity() {

  }

  @Override
  protected void onCreate(
      final Bundle state) {

    this.setTheme(Simplified.getCurrentTheme(WANT_NO_ACTION_BAR));
    super.onCreate(state);
    this.setContentView(R.layout.splash);

    final Timer timer = new Timer();
    timer.schedule(
        new TimerTask() {
          @Override
          public void run() {
            MainSplashActivity.this.finishSplash();
          }
        }, 2000L);
  }

  @Override
  protected void onRestart() {
    super.onRestart();
    this.finishSplash();
  }

  private void finishSplash() {
    final DocumentStoreType docs = Simplified.getDocumentStore();
    final OptionType<EULAType> eula_opt = docs.getEULA();

    if (eula_opt.isSome()) {
      final Some<EULAType> some_eula = (Some<EULAType>) eula_opt;
      final EULAType eula = some_eula.get();
      if (eula.eulaHasAgreed()) {
        LOG.debug("EULA: agreed");
        this.openCatalog();
      } else {
        LOG.debug("EULA: not agreed");
        this.openEULA();
      }
    } else {
      LOG.debug("EULA: unavailable");
      this.openWelcome();
    }
  }

  private void openEULA() {
    final Intent i = new Intent(this, MainEULAActivity.class);
    this.startActivity(i);
  }

  private void openWelcome() {
    final Intent i = new Intent(this, MainWelcomeActivity.class);
    this.startActivity(i);
    this.finish();
  }

  private void openCatalog() {
    final Intent i = new Intent(this, MainCatalogActivity.class);
    this.startActivity(i);
    this.finish();
  }
}
