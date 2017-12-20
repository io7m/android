package org.nypl.simplified.books.profiles;

import com.google.auto.value.AutoValue;

/**
 * A description of a profile.
 */

@AutoValue
public abstract class ProfileDescription implements ProfilePreferencesReadableType {

  public static ProfileDescription create(String display_name) {
    return new AutoValue_ProfileDescription(display_name);
  }

  @Override
  public abstract String displayName();
}
