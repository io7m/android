package org.nypl.simplified.app;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;

import com.io7m.jnull.NullCheck;
import com.io7m.junreachable.UnimplementedCodeException;

import org.nypl.simplified.app.catalog.MainCatalogActivity;
import org.nypl.simplified.books.core.LogUtilities;
import org.slf4j.Logger;

import static org.nypl.simplified.app.Simplified.WantActionBar.WANT_NO_ACTION_BAR;

/**
 *
 */

public final class LoginActivity extends Activity {

  /**
   * Construct login activity
   */

  public LoginActivity() {

  }

  private static final Logger LOG = LogUtilities.getLog(LoginActivity.class);

  @Override
  protected void onCreate(final Bundle state) {

    this.setTheme(Simplified.getCurrentTheme(WANT_NO_ACTION_BAR));
    super.onCreate(state);
    this.setContentView(R.layout.login_view);

    final Resources rr = NullCheck.notNull(this.getResources());
    final boolean clever_enabled = rr.getBoolean(R.bool.feature_auth_provider_clever);

    final ImageButton barcode = NullCheck.notNull(findViewById(R.id.login_with_barcode));
    barcode.setOnClickListener(view -> this.onLoginWithBarcode());

    final ImageButton clever = NullCheck.notNull(findViewById(R.id.login_with_clever));
    if (clever_enabled) {
      clever.setOnClickListener(view -> this.onLoginWithClever());
      clever.setVisibility(View.VISIBLE);
    } else {
      clever.setVisibility(View.GONE);
    }
  }

  private void openCatalog() {
    final Intent i = new Intent(this, MainCatalogActivity.class);
    i.putExtra("reload", true);
    this.startActivity(i);
    this.finish();
  }

  public void onLoginWithBarcode() {
    throw new UnimplementedCodeException();
  }

  public void onLoginWithClever() {
    final Intent i = new Intent(this, CleverLoginActivity.class);
    this.startActivityForResult(i, 1);
  }

  @Override
  protected void onActivityResult(
      final int request_code,
      final int result_code,
      final Intent data) {

    super.onActivityResult(request_code, result_code, data);
    if (result_code == 1) {
      this.openCatalog();
    }
  }
}
