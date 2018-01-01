package org.nypl.simplified.books.profiles;

import com.google.auto.value.AutoValue;
import com.io7m.jfunctional.PartialFunctionType;

/**
 * The type of profile events.
 */

public abstract class ProfileEvent {

  /**
   * Match the type of event.
   *
   * @param <A>                The type of returned values
   * @param <E>                The type of raised exceptions
   * @param on_created         Called for {@code ProfileEventCreated} values
   * @param on_creation_failed Called for {@code ProfileEventCreationFailed} values
   * @return The value returned by the matcher
   * @throws E If the matcher raises {@code E}
   */

  public abstract <A, E extends Exception> A matchEvent(
      PartialFunctionType<ProfileEventCreated, A, E> on_created,
      PartialFunctionType<ProfileEventCreationFailed, A, E> on_creation_failed)
      throws E;

  /**
   * A profile was created.
   */

  @AutoValue
  public abstract static class ProfileEventCreated extends ProfileEvent {

    /**
     * @return The profile display name
     */

    public abstract String displayName();

    /**
     * @return The new profile ID
     */

    public abstract ProfileID id();

    @Override
    public final <A, E extends Exception> A matchEvent(
        final PartialFunctionType<ProfileEventCreated, A, E> on_created,
        final PartialFunctionType<ProfileEventCreationFailed, A, E> on_creation_failed)
        throws E {
      return on_created.call(this);
    }

    /**
     * Create an event.
     *
     * @param display_name The profile display name
     * @param id           The profile ID
     * @return An event
     */

    public static ProfileEventCreated of(
        final String display_name,
        final ProfileID id) {
      return new AutoValue_ProfileEvent_ProfileEventCreated(display_name, id);
    }
  }

  /**
   * The creation of a profile failed.
   */

  @AutoValue
  public abstract static class ProfileEventCreationFailed extends ProfileEvent {

    /**
     * The error code.
     */

    public enum ErrorCode {
      ERROR_DISPLAY_NAME_ALREADY_USED,
      ERROR_IO
    }

    /**
     * @return The profile display name
     */

    public abstract String displayName();

    /**
     * @return The error code
     */

    public abstract ErrorCode errorCode();

    @Override
    public final <A, E extends Exception> A matchEvent(
        final PartialFunctionType<ProfileEventCreated, A, E> on_created,
        final PartialFunctionType<ProfileEventCreationFailed, A, E> on_creation_failed)
        throws E {
      return on_creation_failed.call(this);
    }

    /**
     * Create an event.
     *
     * @param display_name The profile display name
     * @param error        The error code
     * @return An event
     */

    public static ProfileEventCreationFailed of(
        final String display_name,
        final ErrorCode error) {
      return new AutoValue_ProfileEvent_ProfileEventCreationFailed(display_name, error);
    }
  }
}
