package org.nypl.simplified.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.google.common.util.concurrent.ListenableFuture;
import com.io7m.jfunctional.ProcedureType;
import com.io7m.jfunctional.Unit;
import com.io7m.jnull.NullCheck;

import org.nypl.simplified.app.catalog.MainCatalogActivity;
import org.nypl.simplified.app.utilities.UIThread;
import org.nypl.simplified.books.controller.ProfilesControllerType;
import org.nypl.simplified.books.core.LogUtilities;
import org.nypl.simplified.books.profiles.ProfileEvent;
import org.nypl.simplified.books.profiles.ProfileReadableType;
import org.nypl.simplified.observable.ObservableSubscriptionType;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.nypl.simplified.books.profiles.ProfileEvent.ProfileEventCreationFailed.ErrorCode.ERROR_IO;

public final class ProfileSelectionActivity extends Activity {

  private static final Logger LOG = LogUtilities.getLog(ProfileSelectionActivity.class);

  private Button button;
  private ListView list;
  private ProfileArrayAdapter list_adapter;
  private ArrayList<ProfileReadableType> list_items;
  private ObservableSubscriptionType<ProfileEvent> profile_event_subscription;

  public ProfileSelectionActivity() {

  }

  @Override
  protected void onCreate(
      final @Nullable Bundle state) {

    this.setTheme(Simplified.getCurrentTheme());
    super.onCreate(state);

    this.setContentView(R.layout.profiles_selection);

    this.list_items = new ArrayList<>();
    this.reloadProfiles();

    this.list_adapter = new ProfileArrayAdapter(this.getApplicationContext(), this.list_items);
    this.list = NullCheck.notNull((ListView) this.findViewById(R.id.profileSelectionList));
    this.list.setAdapter(this.list_adapter);
    this.list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
      @Override
      public void onItemClick(
          final AdapterView<?> adapter_view,
          final View view,
          final int position,
          final long id) {
        onSelectedProfile(NullCheck.notNull(list_items.get(position)));
      }
    });

    this.button = NullCheck.notNull((Button) this.findViewById(R.id.profileSelectionCreate));
    this.button.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(final View view) {
        openCreationDialog();
      }
    });

    final ProfilesControllerType profiles = Simplified.getProfilesController();
    this.profile_event_subscription = profiles.profileEvents().subscribe(
        new ProcedureType<ProfileEvent>() {
          @Override
          public void call(final ProfileEvent event) {
            reloadProfiles();
          }
        });
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    this.profile_event_subscription.unsubscribe();
  }

  private void onSelectedProfile(
      final ProfileReadableType profile) {

    LOG.debug("selected profile: {} ({})", profile.id(), profile.displayName());

    final ProfilesControllerType profiles = Simplified.getProfilesController();

    final ListenableFuture<Unit> task = profiles.profileSelect(profile.id());

    task.addListener(new Runnable() {
      @Override
      public void run() {
        try {
          onProfileSelectionSucceeded(task.get());
        } catch (final InterruptedException | ExecutionException e) {
          LOG.error("profile selection failed: ", e);
          onProfileSelectionFailed(e);
        }
      }
    }, Simplified.getBackgroundTaskExecutor());
  }

  private void onProfileSelectionFailed(
      final Exception e) {

    LOG.error("onProfileSelectionFailed: {}", e);

    UIThread.runOnUIThread(new Runnable() {
      @Override
      public void run() {

        /*
         * XXX: What exactly can anyone do about this?
         */

        final AlertDialog.Builder alert_builder =
            new AlertDialog.Builder(ProfileSelectionActivity.this);
        alert_builder.setMessage("Unable to select profile!");
        alert_builder.setCancelable(true);

        final AlertDialog alert = alert_builder.create();
        alert.show();
      }
    });
  }

  private void onProfileSelectionSucceeded(
      final Unit ignored) {

    LOG.debug("onProfileSelectionSucceeded");

    UIThread.runOnUIThread(new Runnable() {
      @Override
      public void run() {
        openCatalog();
      }
    });
  }

  private void reloadProfiles() {
    LOG.debug("reloading profiles");

    UIThread.runOnUIThread(new Runnable() {
      @Override
      public void run() {
        final ProfilesControllerType profiles = Simplified.getProfilesController();
        list_items.clear();
        list_items.addAll(profiles.profiles().values());
        Collections.sort(list_items);
        list_adapter.notifyDataSetChanged();
      }
    });
  }

  private static final class ProfileArrayAdapter extends ArrayAdapter<ProfileReadableType> {

    private final List<ProfileReadableType> list_items;
    private final Context context;

    ProfileArrayAdapter(
        final Context in_context,
        final ArrayList<ProfileReadableType> objects) {
      super(in_context, R.layout.profiles_list_item, objects);
      this.context = NullCheck.notNull(in_context, "Context");
      this.list_items = NullCheck.notNull(objects, "Objects");
    }

    private static final class ViewHolder {
      private TextView text;
      private ImageView image;

      ViewHolder() {

      }
    }

    @Override
    public View getView(
        final int position,
        final View convert_view,
        final ViewGroup parent_group) {

      View row_view = convert_view;
      if (row_view == null) {
        final LayoutInflater inflater =
            (LayoutInflater) this.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        row_view = inflater.inflate(R.layout.profiles_list_item, null);

        final ViewHolder view_holder = new ViewHolder();
        view_holder.text = row_view.findViewById(R.id.profileItemDisplayName);
        view_holder.image = row_view.findViewById(R.id.profileItemIcon);
        row_view.setTag(view_holder);
      }

      final ViewHolder holder = (ViewHolder) row_view.getTag();
      final ProfileReadableType profile = this.list_items.get(position);
      holder.text.setText(profile.displayName());
      LOG.trace("getView: [{}] profile: {}", position, profile.displayName());
      return row_view;
    }
  }

  private void openCatalog() {
    final Intent i = new Intent(this, MainCatalogActivity.class);
    this.startActivity(i);
  }

  private void openCreationDialog() {
    final Intent i = new Intent(this, ProfileCreationActivity.class);
    this.startActivity(i);
  }
}
