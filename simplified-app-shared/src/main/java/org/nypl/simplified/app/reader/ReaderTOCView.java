package org.nypl.simplified.app.reader;

import android.content.Context;
import android.content.res.Resources;
import android.database.DataSetObserver;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.io7m.jnull.NullCheck;
import com.io7m.jnull.Nullable;
import com.io7m.junreachable.UnimplementedCodeException;

import org.nypl.simplified.app.R;
import org.nypl.simplified.app.ScreenSizeInformationType;
import org.nypl.simplified.app.Simplified;
import org.nypl.simplified.app.reader.ReaderTOC.TOCElement;
import org.nypl.simplified.app.utilities.UIThread;
import org.nypl.simplified.books.accounts.AccountType;
import org.nypl.simplified.books.controller.ProfilesControllerType;
import org.nypl.simplified.books.core.LogUtilities;
import org.nypl.simplified.books.profiles.ProfileNoneCurrentException;
import org.nypl.simplified.books.reader.ReaderColorScheme;
import org.nypl.simplified.books.reader.ReaderPreferences;
import org.slf4j.Logger;

import java.util.List;

/**
 * A re-usable view of a table of contents.
 */

public final class ReaderTOCView implements ListAdapter {

  private static final Logger LOG = LogUtilities.getLog(ReaderTOCView.class);

  private final ArrayAdapter<TOCElement> adapter;
  private final LayoutInflater inflater;
  private final ReaderTOCViewSelectionListenerType listener;
  private final ViewGroup view_layout;
  private final ViewGroup view_root;
  private final TextView view_title;
  private final ProfilesControllerType profiles;
  private final AccountType account;

  /**
   * Construct a TOC view.
   */

  public ReaderTOCView(
      final ProfilesControllerType in_profiles,
      final AccountType in_account,
      final LayoutInflater in_inflater,
      final Context in_context,
      final ReaderTOC in_toc,
      final ReaderTOCViewSelectionListenerType in_listener) {

    this.profiles =
        NullCheck.notNull(in_profiles, "Profiles");
    this.account =
        NullCheck.notNull(in_account, "Account");
    this.inflater =
        NullCheck.notNull(in_inflater, "Inflator");
    this.listener =
        NullCheck.notNull(in_listener, "Listener");

    NullCheck.notNull(in_context, "Context");
    NullCheck.notNull(in_profiles, "Profiles");
    NullCheck.notNull(in_toc, "TOC");

    final ViewGroup in_layout =
        NullCheck.notNull((ViewGroup) in_inflater.inflate(R.layout.reader_toc, null));
    final ListView in_list_view =
        NullCheck.notNull(in_layout.findViewById(R.id.reader_toc_list));
    final TextView in_title =
        NullCheck.notNull(in_layout.findViewById(R.id.reader_toc_title));
    final ViewGroup in_root =
        NullCheck.notNull((ViewGroup) in_list_view.getRootView());

    this.view_layout = in_layout;
    this.view_root = in_root;
    this.view_title = in_title;

    final List<TOCElement> es = in_toc.getElements();
    this.adapter = new ArrayAdapter<>(in_context, 0, es);

    in_list_view.setAdapter(this);

    try {
      this.applyColorScheme(
          profiles.profileCurrent()
              .preferences()
              .readerPreferences()
              .colorScheme());
    } catch (final ProfileNoneCurrentException e) {
      throw new IllegalStateException(e);
    }
  }

  private void applyColorScheme(final ReaderColorScheme cs) {
    UIThread.checkIsUIThread();

    final ViewGroup in_root = NullCheck.notNull(this.view_root);
    in_root.setBackgroundColor(ReaderColorSchemes.background(cs));
  }

  @Override
  public boolean areAllItemsEnabled() {
    return NullCheck.notNull(this.adapter).areAllItemsEnabled();
  }

  @Override
  public int getCount() {
    return NullCheck.notNull(this.adapter).getCount();
  }

  @Override
  public TOCElement getItem(final int position) {
    return NullCheck.notNull(NullCheck.notNull(this.adapter).getItem(position));
  }

  @Override
  public long getItemId(final int position) {
    return NullCheck.notNull(this.adapter).getItemId(position);
  }

  @Override
  public int getItemViewType(final int position) {
    return NullCheck.notNull(this.adapter).getItemViewType(position);
  }

  /**
   * @return The view group containing the main layout
   */

  public ViewGroup getLayoutView() {
    return this.view_layout;
  }

  @Override
  public View getView(
      final int position,
      final @Nullable View reuse,
      final @Nullable ViewGroup parent) {

    final ViewGroup item_view;
    if (reuse != null) {
      item_view = (ViewGroup) reuse;
    } else {
      item_view = (ViewGroup) this.inflater.inflate(
          R.layout.reader_toc_element, parent, false);
    }

    /*
     * Populate the text view and set the left margin based on the desired
     * indentation level.
     */

    final TextView text_view =
        NullCheck.notNull(item_view.findViewById(R.id.reader_toc_element_text));
    final TOCElement e =
        NullCheck.notNull(this.adapter).getItem(position);

    text_view.setText(e.getTitle());

    final ReaderColorScheme color_scheme;
    try {
      color_scheme = profiles.profileCurrent()
          .preferences()
          .readerPreferences()
          .colorScheme();
    } catch (final ProfileNoneCurrentException ex) {
      throw new IllegalStateException(ex);
    }

    final RelativeLayout.LayoutParams p =
        new RelativeLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT);

    final ScreenSizeInformationType screen = Simplified.getScreenSizeInformation();
    p.setMargins((int) screen.screenDPToPixels(e.getIndent() * 16), 0, 0, 0);
    text_view.setLayoutParams(p);
    text_view.setTextColor(ReaderColorSchemes.foreground(color_scheme));
    item_view.setOnClickListener(v -> this.listener.onTOCItemSelected(e));
    return item_view;
  }

  @Override
  public int getViewTypeCount() {
    return NullCheck.notNull(this.adapter).getViewTypeCount();
  }

  @Override
  public boolean hasStableIds() {
    return NullCheck.notNull(this.adapter).hasStableIds();
  }

  /**
   * Hide the back button!
   */

  @Override
  public boolean isEmpty() {
    return NullCheck.notNull(this.adapter).isEmpty();
  }

  @Override
  public boolean isEnabled(final int position) {
    return NullCheck.notNull(this.adapter).isEnabled(position);
  }

  @Override
  public void registerDataSetObserver(final @Nullable DataSetObserver observer) {
    NullCheck.notNull(this.adapter).registerDataSetObserver(observer);
  }

  @Override
  public void unregisterDataSetObserver(final @Nullable DataSetObserver observer) {
    NullCheck.notNull(this.adapter).unregisterDataSetObserver(observer);
  }

  void onProfilePreferencesChanged(final ReaderPreferences prefs) {
    UIThread.checkIsUIThread();
    this.applyColorScheme(prefs.colorScheme());
  }
}
