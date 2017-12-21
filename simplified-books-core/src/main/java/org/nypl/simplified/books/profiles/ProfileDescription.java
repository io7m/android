package org.nypl.simplified.books.profiles;

import com.google.auto.value.AutoValue;

/**
 * A description of a profile.
 */

@AutoValue
public abstract class ProfileDescription implements ProfilePreferencesReadableType {

  ProfileDescription() {

  }

  /**
   * Create a new profile description.
   *
   * @param display_name The profile display name
   * @return A new profile description
   */

  public static ProfileDescription create(String display_name) {
    return new AutoValue_ProfileDescription(display_name);
  }

  @Override
  public abstract String displayName();
}
