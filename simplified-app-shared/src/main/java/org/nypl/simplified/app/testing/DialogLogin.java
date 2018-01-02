package org.nypl.simplified.app.testing;

import android.app.Activity;
import android.app.FragmentManager;
import android.os.Bundle;

import com.io7m.jnull.Nullable;

import org.nypl.simplified.app.LoginDialog;
import org.nypl.simplified.app.Simplified;
import org.nypl.simplified.books.accounts.AccountBarcode;
import org.nypl.simplified.books.accounts.AccountPIN;
import org.nypl.simplified.books.profiles.ProfileNoneCurrentException;
import org.nypl.simplified.books.profiles.ProfileNonexistentAccountProviderException;

/**
 * A dialog activity requesting login details.
 */

public final class DialogLogin extends Activity {

  /**
   * Construct an activity.
   */

  public DialogLogin() {

  }

  @Override
  protected void onCreate(final @Nullable Bundle state) {
    super.onCreate(state);

    try {
      final LoginDialog d = LoginDialog.newDialog(
          Simplified.getProfilesController(),
          "Something here",
          Simplified.getProfilesController().profileAccountProviderCurrent(),
          AccountBarcode.create(""),
          AccountPIN.create(""),
          () -> {},
          () -> {},
          x -> {});

      final FragmentManager fm = this.getFragmentManager();
      d.show(fm, "dialog");
    } catch (final ProfileNoneCurrentException | ProfileNonexistentAccountProviderException e) {
      throw new IllegalStateException(e);
    }
  }
}
