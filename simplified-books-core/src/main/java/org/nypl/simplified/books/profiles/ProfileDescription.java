package org.nypl.simplified.books.profiles;

import com.io7m.jnull.NullCheck;

/**
 * A description of a profile.
 */

public final class ProfileDescription implements ProfilePreferencesReadableType {

  private final String display_name;

  private ProfileDescription(
      String in_display_name) {
    this.display_name = NullCheck.notNull(in_display_name, "Display name");
  }

  public static ProfileDescription create(
      String in_display_name)
  {
    return new ProfileDescription(in_display_name);
  }

  @Override
  public String displayName() {
    return this.display_name;
  }
}
