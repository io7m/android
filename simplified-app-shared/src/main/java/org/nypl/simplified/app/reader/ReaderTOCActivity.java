package org.nypl.simplified.app.reader;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;

import com.io7m.jnull.NullCheck;
import com.io7m.jnull.Nullable;

import org.nypl.simplified.app.Simplified;
import org.nypl.simplified.app.reader.ReaderTOC.TOCElement;
import org.nypl.simplified.app.utilities.UIThread;
import org.nypl.simplified.books.accounts.AccountID;
import org.nypl.simplified.books.accounts.AccountType;
import org.nypl.simplified.books.accounts.AccountsDatabaseNonexistentException;
import org.nypl.simplified.books.controller.ProfilesControllerType;
import org.nypl.simplified.books.core.LogUtilities;
import org.nypl.simplified.books.profiles.ProfileEvent;
import org.nypl.simplified.books.profiles.ProfileNoneCurrentException;
import org.nypl.simplified.books.profiles.ProfilePreferencesChanged;
import org.nypl.simplified.books.reader.ReaderPreferences;
import org.nypl.simplified.observable.ObservableSubscriptionType;
import org.slf4j.Logger;

/**
 * Activity for displaying the table of contents on devices with small screens.
 */

public final class ReaderTOCActivity
    extends Activity implements ReaderTOCViewSelectionListenerType {

  /**
   * The name of the argument containing the TOC.
   */

  public static final String TOC_ID;

  /**
   * The name of the argument containing the selected TOC item.
   */

  public static final String TOC_SELECTED_ID;

  /**
   * The activity request code (for retrieving the result of executing the
   * activity).
   */

  public static final int TOC_SELECTION_REQUEST_CODE;

  private static final Logger LOG;

  private static final String ACCOUNT_ID;

  static {
    LOG = LogUtilities.getLog(ReaderTOCActivity.class);
    TOC_SELECTION_REQUEST_CODE = 23;
    TOC_ID = "org.nypl.simplified.app.reader.ReaderTOCActivity.toc";
    ACCOUNT_ID = "org.nypl.simplified.app.reader.ReaderTOCActivity.account";
    TOC_SELECTED_ID = "org.nypl.simplified.app.reader.ReaderTOCActivity.toc_selected";
  }

  private ReaderTOCView view;
  private ObservableSubscriptionType<ProfileEvent> profile_event_subscription;

  /**
   * Construct an activity.
   */

  public ReaderTOCActivity() {

  }

  /**
   * Start a TOC activity. The user will be prompted to select a TOC item, and
   * the results of that selection will be reported using the request code
   * {@link #TOC_SELECTION_REQUEST_CODE}.
   *
   * @param from The parent activity
   * @param toc  The table of contents
   */

  public static void startActivityForResult(
      final Activity from,
      final AccountType account,
      final ReaderTOC toc) {

    NullCheck.notNull(from);
    NullCheck.notNull(account, "Account");
    NullCheck.notNull(toc);

    final Intent i = new Intent(Intent.ACTION_PICK);
    i.setClass(from, ReaderTOCActivity.class);
    i.putExtra(TOC_ID, toc);
    i.putExtra(ACCOUNT_ID, account.id());
    from.startActivityForResult(i, TOC_SELECTION_REQUEST_CODE);
  }

  @Override
  public void finish() {
    super.finish();
    this.overridePendingTransition(0, 0);
  }

  @Override
  protected void onCreate(final @Nullable Bundle state) {
    this.setTheme(Simplified.getCurrentTheme());
    super.onCreate(state);

    LOG.debug("onCreate");

    final Intent input =
        NullCheck.notNull(this.getIntent());
    final Bundle args =
        NullCheck.notNull(input.getExtras());
    final ReaderTOC in_toc =
        NullCheck.notNull((ReaderTOC) args.getSerializable(TOC_ID));
    final AccountID in_account_id =
        NullCheck.notNull((AccountID) args.getSerializable(ACCOUNT_ID));

    try {
      final ProfilesControllerType profiles =
          Simplified.getProfilesController();
      final AccountType account =
          profiles.profileCurrent().account(in_account_id);

      final LayoutInflater inflater =
          NullCheck.notNull(this.getLayoutInflater());
      this.view =
          new ReaderTOCView(profiles, account, inflater, this, in_toc, this);

      this.setContentView(this.view.getLayoutView());

      this.profile_event_subscription =
          profiles.profileEvents().subscribe(this::onProfileEvent);
    } catch (final AccountsDatabaseNonexistentException | ProfileNoneCurrentException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();

    this.profile_event_subscription.unsubscribe();
  }

  private void onProfileEvent(final ProfileEvent event) {
    if (event instanceof ProfilePreferencesChanged) {
      try {
        final ReaderPreferences prefs =
            Simplified.getProfilesController()
                .profileCurrent()
                .preferences()
                .readerPreferences();

        UIThread.runOnUIThread(() -> this.view.onProfilePreferencesChanged(prefs));
      } catch (final ProfileNoneCurrentException e) {
        throw new IllegalStateException(e);
      }
    }
  }

  @Override
  public void onTOCBackSelected() {
    this.finish();
  }

  @Override
  public void onTOCItemSelected(final TOCElement e) {
    final Intent intent = new Intent();
    intent.putExtra(TOC_SELECTED_ID, e);
    this.setResult(Activity.RESULT_OK, intent);
    this.finish();
  }
}
