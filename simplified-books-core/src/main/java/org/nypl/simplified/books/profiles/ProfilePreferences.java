package org.nypl.simplified.books.profiles;

import com.google.auto.value.AutoValue;
import com.io7m.jfunctional.Option;
import com.io7m.jfunctional.OptionType;

/**
 * A set of preferences for a profile.
 */

@AutoValue
public abstract class ProfilePreferences {

  ProfilePreferences() {

  }

  /**
   * @return The age of the reader (if one has been explicitly specified)
   */

  public abstract OptionType<Age> age();

  /**
   * The age of the reader.
   */

  enum Age {

    /**
     * The reader is 13 or over.
     */

    AGE_OVER_OR_EXACTLY_13,

    /**
     * The reader is under 13.
     */

    AGE_UNDER_13
  }

  /**
   * A mutable builder for the type.
   */

  @AutoValue.Builder
  public abstract static class Builder {

    Builder() {

    }

    /**
     * @param age The age
     * @return The current builder
     * @see #age()
     */

    public abstract Builder setAge(
        OptionType<Age> age);

    /**
     * @param age The age
     * @return The current builder
     * @see #age()
     */

    public final Builder setAge(
        final Age age)
    {
      return setAge(Option.some(age));
    }

    /**
     * @return A profile description based on the given parameters
     */

    public abstract ProfilePreferences build();
  }

  public static ProfilePreferences.Builder builder()
  {
    return new AutoValue_ProfilePreferences.Builder()
        .setAge(Option.<Age>none());
  }
}
