package org.nypl.simplified.app;

import android.app.Activity;
import android.app.FragmentManager;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;

import com.io7m.jfunctional.OptionType;
import com.io7m.jfunctional.Unit;
import com.io7m.jnull.NullCheck;
import com.io7m.junreachable.UnimplementedCodeException;

import org.nypl.simplified.app.catalog.MainCatalogActivity;
import org.nypl.simplified.app.utilities.UIThread;
import org.nypl.simplified.books.accounts.AccountAuthenticationCredentials;
import org.nypl.simplified.books.accounts.AccountBarcode;
import org.nypl.simplified.books.accounts.AccountPIN;
import org.nypl.simplified.books.core.BooksType;
import org.nypl.simplified.books.core.LogUtilities;
import org.nypl.simplified.books.profiles.ProfileNoneCurrentException;
import org.nypl.simplified.books.profiles.ProfileNonexistentAccountProviderException;
import org.slf4j.Logger;

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

    this.setTheme(Simplified.getCurrentTheme());
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
    this.overridePendingTransition(0, 0);
    this.finish();
  }

  public void onLoginWithBarcode() {
    final FragmentManager fm = this.getFragmentManager();

    UIThread.runOnUIThread(() -> {
      try {
        final AccountBarcode barcode = AccountBarcode.create("");
        final AccountPIN pin = AccountPIN.create("");
        final LoginDialog dialog =
            LoginDialog.newDialog(
                Simplified.getProfilesController(),
                "Login required",
                Simplified.getProfilesController().profileAccountProviderCurrent(),
                barcode,
                pin,
                this::openCatalog,
                () -> LOG.debug("cancelled login"),
                (m) -> Unit.unit());
        dialog.show(fm, "login-dialog");
      } catch (final ProfileNoneCurrentException | ProfileNonexistentAccountProviderException e) {
        throw new IllegalStateException(e);
      }
    });
  }

  public void onLoginWithClever() {
    final Intent i = new Intent(this, CleverLoginActivity.class);
    this.startActivityForResult(i, 1);
    this.overridePendingTransition(0, 0);
  }

  @Override
  protected void onActivityResult(
      final int request_code,
      final int result_code,
      final Intent data) {

    super.onActivityResult(request_code, result_code, data);
    if (result_code == 1) {
      this.openCatalog();
      final BooksType books = getBooks();
      books.fulfillExistingBooks();
    }
  }

  private static BooksType getBooks() {
    throw new UnimplementedCodeException();
  }
}
