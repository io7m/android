package org.nypl.simplified.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.io7m.jnull.NullCheck;

import org.nypl.simplified.app.catalog.MainCatalogActivity;
import org.nypl.simplified.books.controller.ProfilesControllerType;
import org.nypl.simplified.books.core.LogUtilities;
import org.slf4j.Logger;

import java.util.Timer;
import java.util.TimerTask;

import static org.nypl.simplified.app.Simplified.WantActionBar.WANT_NO_ACTION_BAR;

/**
 * A splash screen activity that shows a logo and then either moves to the catalog or the
 * profile selection screen.
 */

public class MainSplashActivity extends SimplifiedActivity {

  private static final Logger LOG = LogUtilities.getLog(MainSplashActivity.class);

  private View root;
  private View image;

  /**
   * Construct an activity.
   */

  public MainSplashActivity() {

  }

  @Override
  protected void onCreate(final Bundle state) {

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

    this.root =
        NullCheck.notNull(this.findViewById(R.id.splash_root), "R.id.splash_root");
    this.image =
        NullCheck.notNull(this.findViewById(R.id.splash_image), "R.id.splash_image");

    this.root.setOnClickListener(view -> {
      timer.cancel();
      this.finishSplash();
    });

    this.image.setOnClickListener(view -> {
      timer.cancel();
      this.finishSplash();
    });
  }

  @Override
  protected void onRestart() {
    super.onRestart();
    this.finishSplash();
  }

  private void finishSplash() {
    LOG.debug("splash screen completed");
    this.openProfilesOrCatalog();
  }

  private void openProfilesOrCatalog() {
    final ProfilesControllerType profiles = Simplified.getProfilesController();
    switch (profiles.profileAnonymousEnabled()) {
      case ANONYMOUS_PROFILE_ENABLED: {
        this.openCatalog();
        break;
      }
      case ANONYMOUS_PROFILE_DISABLED: {
        this.openProfiles();
        break;
      }
    }
  }

  private void openProfiles() {
    final Intent intent = new Intent(this, ProfileSelectionActivity.class);
    this.startActivity(intent);
    this.finish();
  }

  private void openCatalog() {
    final Intent intent = new Intent(this, MainCatalogActivity.class);
    this.startActivity(intent);
    this.finish();
  }
}
