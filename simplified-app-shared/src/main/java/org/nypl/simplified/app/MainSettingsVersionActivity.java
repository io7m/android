package org.nypl.simplified.app;

import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.widget.TextView;

import com.io7m.jnull.NullCheck;
import com.io7m.jnull.Nullable;

import org.nypl.simplified.books.core.LogUtilities;
import org.slf4j.Logger;

/**
 * The activity displaying the application version information.
 */

public final class MainSettingsVersionActivity extends NavigationDrawerActivity {

  private static final Logger LOG = LogUtilities.getLog(MainSettingsVersionActivity.class);

  private TextView text_build;
  private TextView text_version;

  /**
   * Construct an activity.
   */

  public MainSettingsVersionActivity() {

  }

  @Override
  protected String navigationDrawerGetActivityTitle(final Resources resources) {
    return resources.getString(R.string.settings);
  }

  @Override
  protected boolean navigationDrawerShouldShowIndicator() {
    return true;
  }

  @Override
  protected void onCreate(final @Nullable Bundle state) {
    super.onCreate(state);

    this.setContentView(R.layout.settings_version);

    this.text_build =
        NullCheck.notNull(this.findViewById(R.id.settings_version_revision),
            "this.findViewById(R.id.settings_version_revision)");

    this.text_version =
        NullCheck.notNull(this.findViewById(R.id.settings_version_version),
            "this.findViewById(R.id.settings_version_version)");

    this.text_build.setText(BuildRevision.revision(this.getAssets()));

    try {
      this.text_version.setText(
          this.getPackageManager()
              .getPackageInfo(getPackageName(), 0)
              .versionName);
    } catch (final PackageManager.NameNotFoundException e) {
      LOG.error("Unable to fetch version: ", e);
    }
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
  }
}
