package org.nypl.simplified.books.profiles;

import com.google.auto.value.AutoValue;

public abstract class ProfileEvent {

  /**
   * The type of event matchers.
   *
   * @param <A> The type of returned values
   * @param <E> The type of raised exceptions
   */

  public interface MatcherType<A, E extends Exception> {

    /**
     * Match an event.
     *
     * @param e The event
     * @return A value of {@code A}
     * @throws E If required
     */

    A onProfileEventCreated(
        ProfileEventCreated e)
        throws E;

    /**
     * Match an event.
     *
     * @param e The event
     * @return A value of {@code A}
     * @throws E If required
     */

    A onProfileEventCreationFailed(
        ProfileEventCreationFailed e)
        throws E;
  }

  /**
   * Match the type of event.
   *
   * @param m   The matcher
   * @param <A> The type of returned values
   * @param <E> The type of raised exceptions
   * @return The value returned by the matcher
   * @throws E If the matcher raises {@code E}
   */

  public abstract <A, E extends Exception> A matchEvent(
      MatcherType<A, E> m)
      throws E;

  @AutoValue
  public abstract static class ProfileEventCreated extends ProfileEvent {

    public abstract String displayName();

    public abstract ProfileID id();

    @Override
    public final <A, E extends Exception> A matchEvent(
        final MatcherType<A, E> m) throws E {
      return m.onProfileEventCreated(this);
    }

    public static ProfileEventCreated of(
        final String display_name,
        final ProfileID id)
    {
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

    @Override
    public final <A, E extends Exception> A matchEvent(
        final MatcherType<A, E> m) throws E {
      return m.onProfileEventCreationFailed(this);
    }

    public static ProfileEventCreationFailed of(
        final String display_name,
        final ErrorCode error)
    {
      return new AutoValue_ProfileEvent_ProfileEventCreationFailed(display_name, error);
    }
  }
}
