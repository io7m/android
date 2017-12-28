package org.nypl.simplified.books.profiles;

import com.google.auto.value.AutoValue;
import com.io7m.jfunctional.Option;
import com.io7m.jfunctional.OptionType;

/**
 * A description of a profile.
 */

@AutoValue
public abstract class ProfileDescription {

  ProfileDescription() {

  }

  /**
   * @return The display name for the profile
   */

  public abstract String displayName();

  /**
   * @return The preferences for the profile
   */

  public abstract ProfilePreferences preferences();

  /**
   * @return The current value as a mutable builder
   */

  public abstract Builder toBuilder();

  /**
   * A mutable builder for the type.
   */

  @AutoValue.Builder
  public abstract static class Builder {

    Builder() {

    }

    /**
     * @see #displayName()
     * @param display_name The display name
     * @return The current builder
     */

    public abstract Builder setDisplayName(
        String display_name);

    /**
     * @see #preferences()
     * @param preferences The profile preferences
     * @return The current builder
     */

    public abstract Builder setPreferences(
        ProfilePreferences preferences);

    /**
     * @return A profile description based on the given parameters
     */

    public abstract ProfileDescription build();
  }

  /**
   * @param display_name The display name for the profile
   * @return A mutable builder to construct descriptions
   */

  public static ProfileDescription.Builder builder(
      final String display_name,
      final ProfilePreferences preferences)
  {
    return new AutoValue_ProfileDescription.Builder()
        .setDisplayName(display_name)
        .setPreferences(preferences);
  }
}
