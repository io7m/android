package org.nypl.simplified.books.profiles;

import com.google.auto.value.AutoValue;
import com.io7m.jfunctional.PartialFunctionType;

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

  @AutoValue
  public abstract static class ProfileEventCreated extends ProfileEvent {

    public abstract String displayName();

    public abstract ProfileID id();

    public final <A, E extends Exception> A matchEvent(
        final PartialFunctionType<ProfileEventCreated, A, E> on_created,
        final PartialFunctionType<ProfileEventCreationFailed, A, E> on_creation_failed)
        throws E {
      return on_created.call(this);
    }

    public static ProfileEventCreated of(
        final String display_name,
        final ProfileID id) {
      return new AutoValue_ProfileEvent_ProfileEventCreated(display_name, id);
    }
  }

  @AutoValue
  public abstract static class ProfileEventCreationFailed extends ProfileEvent {

    public enum ErrorCode {
      ERROR_DISPLAY_NAME_ALREADY_USED,
      ERROR_IO
    }

    public abstract String displayName();

    public abstract ErrorCode errorCode();

    public final <A, E extends Exception> A matchEvent(
        final PartialFunctionType<ProfileEventCreated, A, E> on_created,
        final PartialFunctionType<ProfileEventCreationFailed, A, E> on_creation_failed)
        throws E {
      return on_creation_failed.call(this);
    }

    public static ProfileEventCreationFailed of(
        final String display_name,
        final ErrorCode error) {
      return new AutoValue_ProfileEvent_ProfileEventCreationFailed(display_name, error);
    }
  }
}
