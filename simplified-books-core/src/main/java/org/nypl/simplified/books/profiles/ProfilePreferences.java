package org.nypl.simplified.books.profiles;

import com.google.auto.value.AutoValue;
import com.io7m.jfunctional.Option;
import com.io7m.jfunctional.OptionType;

import org.joda.time.LocalDate;

/**
 * A set of preferences for a profile.
 */

@AutoValue
public abstract class ProfilePreferences {

  ProfilePreferences() {

  }

  /**
   * @return The date of birth of the reader (if one has been explicitly specified)
   */

  public abstract OptionType<LocalDate> dateOfBirth();

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
     * @param date The date
     * @return The current builder
     * @see #dateOfBirth()
     */

    public abstract Builder setDateOfBirth(
        OptionType<LocalDate> date);

    /**
     * @param date The date
     * @return The current builder
     * @see #dateOfBirth()
     */

    public final Builder setDateOfBirth(
        final LocalDate date) {
      return setDateOfBirth(Option.some(date));
    }

    /**
     * @return A profile description based on the given parameters
     */

    public abstract ProfilePreferences build();
  }

  /**
   * @return A new builder
   */

  public static ProfilePreferences.Builder builder() {
    return new AutoValue_ProfilePreferences.Builder()
        .setDateOfBirth(Option.<LocalDate>none());
  }
}
