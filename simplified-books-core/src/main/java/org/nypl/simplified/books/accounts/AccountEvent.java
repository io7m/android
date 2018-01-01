package org.nypl.simplified.books.accounts;

import com.google.auto.value.AutoValue;
import com.io7m.jfunctional.OptionType;
import com.io7m.jfunctional.PartialFunctionType;

import org.nypl.simplified.books.profiles.ProfileReadableType;

/**
 * The type of account events.
 */

public abstract class AccountEvent {

  /**
   * Match the type of event.
   *
   * @param <A>         The type of returned values
   * @param <E>         The type of raised exceptions
   * @param on_creation Called for {@code AccountCreationEvent} values
   * @param on_deletion Called for {@code AccountDeletionEvent} values
   * @param on_login    Called for {@code AccountLoginEvent} values
   * @param on_changed  Called for {@code AccountChanged} values
   * @return The value returned by the matcher
   * @throws E If the matcher raises {@code E}
   */

  public abstract <A, E extends Exception> A match(
      PartialFunctionType<AccountCreationEvent, A, E> on_creation,
      PartialFunctionType<AccountDeletionEvent, A, E> on_deletion,
      PartialFunctionType<AccountLoginEvent, A, E> on_login,
      PartialFunctionType<AccountChanged, A, E> on_changed)
      throws E;

  /**
   * Creating an account succeeded.
   */

  @AutoValue
  public abstract static class AccountChanged extends AccountEvent {

    @Override
    public final <A, E extends Exception> A match(
        final PartialFunctionType<AccountCreationEvent, A, E> on_creation,
        final PartialFunctionType<AccountDeletionEvent, A, E> on_deletion,
        final PartialFunctionType<AccountLoginEvent, A, E> on_login,
        final PartialFunctionType<AccountChanged, A, E> on_changed)
        throws E {
      return on_changed.call(this);
    }

    /**
     * @return The current account
     */

    public abstract AccountID account();

    /**
     * @return An event
     */

    public static AccountChanged of(final AccountID account) {
      return new AutoValue_AccountEvent_AccountChanged(account);
    }
  }

  /**
   * The type of account creation events.
   */

  public abstract static class AccountCreationEvent extends AccountEvent {

    @Override
    public final <A, E extends Exception> A match(
        final PartialFunctionType<AccountCreationEvent, A, E> on_creation,
        final PartialFunctionType<AccountDeletionEvent, A, E> on_deletion,
        final PartialFunctionType<AccountLoginEvent, A, E> on_login,
        final PartialFunctionType<AccountChanged, A, E> on_changed)
        throws E {
      return on_creation.call(this);
    }

    /**
     * Match the type of event.
     *
     * @param <A>        The type of returned values
     * @param <E>        The type of raised exceptions
     * @param on_success Called for {@code AccountCreationSucceeded} values
     * @param on_failure Called for {@code AccountCreationFailed} values
     * @return The value returned by the matcher
     * @throws E If the matcher raises {@code E}
     */

    public abstract <A, E extends Exception> A matchCreation(
        PartialFunctionType<AccountCreationSucceeded, A, E> on_success,
        PartialFunctionType<AccountCreationFailed, A, E> on_failure)
        throws E;


    /**
     * Creating an account succeeded.
     */

    @AutoValue
    public abstract static class AccountCreationSucceeded extends AccountCreationEvent {

      /**
       * @return The account provider
       */

      public abstract AccountProvider provider();

      @Override
      public final <A, E extends Exception> A matchCreation(
          final PartialFunctionType<AccountCreationSucceeded, A, E> on_success,
          final PartialFunctionType<AccountCreationFailed, A, E> on_failure)
          throws E {
        return on_success.call(this);
      }

      /**
       * @return An event
       */

      public static AccountCreationSucceeded of(final AccountProvider provider) {
        return new AutoValue_AccountEvent_AccountCreationEvent_AccountCreationSucceeded(provider);
      }
    }

    /**
     * Creating an account failed.
     */

    @AutoValue
    public abstract static class AccountCreationFailed extends AccountCreationEvent {

      /**
       * The error codes that can be raised
       */

      public enum ErrorCode {

        /**
         * A profile configuration problem occurred (such as the user not having
         * selected a profile).
         */

        ERROR_PROFILE_CONFIGURATION,

        /**
         * There was a problem with the accounts database.
         */

        ERROR_ACCOUNT_DATABASE_PROBLEM
      }

      /**
       * @return The error code
       */

      public abstract ErrorCode errorCode();

      /**
       * @return The exception raised during logging in, if any
       */

      public abstract OptionType<Exception> exception();

      @Override
      public final <A, E extends Exception> A matchCreation(
          final PartialFunctionType<AccountCreationSucceeded, A, E> on_success,
          final PartialFunctionType<AccountCreationFailed, A, E> on_failure)
          throws E {
        return on_failure.call(this);
      }

      /**
       * @param code      The error code
       * @param exception The exception raised, if any
       * @return An event
       */

      public static AccountCreationFailed of(
          final ErrorCode code,
          final OptionType<Exception> exception) {
        return new AutoValue_AccountEvent_AccountCreationEvent_AccountCreationFailed(code, exception);
      }
    }
  }


  /**
   * The type of account deletion events.
   */

  public abstract static class AccountDeletionEvent extends AccountEvent {

    @Override
    public final <A, E extends Exception> A match(
        final PartialFunctionType<AccountCreationEvent, A, E> on_creation,
        final PartialFunctionType<AccountDeletionEvent, A, E> on_deletion,
        final PartialFunctionType<AccountLoginEvent, A, E> on_login,
        final PartialFunctionType<AccountChanged, A, E> on_changed)
        throws E {
      return on_deletion.call(this);
    }

    /**
     * Match the type of event.
     *
     * @param <A>        The type of returned values
     * @param <E>        The type of raised exceptions
     * @param on_success Called for {@code AccountDeletionSucceeded} values
     * @param on_failure Called for {@code AccountDeletionFailed} values
     * @return The value returned by the matcher
     * @throws E If the matcher raises {@code E}
     */

    public abstract <A, E extends Exception> A matchDeletion(
        PartialFunctionType<AccountDeletionSucceeded, A, E> on_success,
        PartialFunctionType<AccountDeletionFailed, A, E> on_failure)
        throws E;


    /**
     * Creating an account succeeded.
     */

    @AutoValue
    public abstract static class AccountDeletionSucceeded extends AccountDeletionEvent {

      /**
       * @return The account provider
       */

      public abstract AccountProvider provider();

      @Override
      public final <A, E extends Exception> A matchDeletion(
          final PartialFunctionType<AccountDeletionSucceeded, A, E> on_success,
          final PartialFunctionType<AccountDeletionFailed, A, E> on_failure)
          throws E {
        return on_success.call(this);
      }

      /**
       * @return An event
       */

      public static AccountDeletionSucceeded of(final AccountProvider provider) {
        return new AutoValue_AccountEvent_AccountDeletionEvent_AccountDeletionSucceeded(provider);
      }
    }

    /**
     * Creating an account failed.
     */

    @AutoValue
    public abstract static class AccountDeletionFailed extends AccountDeletionEvent {

      /**
       * The error codes that can be raised
       */

      public enum ErrorCode {

        /**
         * A profile configuration problem occurred (such as the user not having
         * selected a profile).
         */

        ERROR_PROFILE_CONFIGURATION,

        /**
         * The user attempted to delete the only remaining account.
         */

        ERROR_ACCOUNT_ONLY_ONE_REMAINING,

        /**
         * There was a problem with the accounts database.
         */

        ERROR_ACCOUNT_DATABASE_PROBLEM
      }

      /**
       * @return The error code
       */

      public abstract ErrorCode errorCode();

      /**
       * @return The exception raised during logging in, if any
       */

      public abstract OptionType<Exception> exception();

      @Override
      public final <A, E extends Exception> A matchDeletion(
          final PartialFunctionType<AccountDeletionSucceeded, A, E> on_success,
          final PartialFunctionType<AccountDeletionFailed, A, E> on_failure)
          throws E {
        return on_failure.call(this);
      }

      /**
       * @param code      The error code
       * @param exception The exception raised, if any
       * @return An event
       */

      public static AccountDeletionFailed of(
          final ErrorCode code,
          final OptionType<Exception> exception) {
        return new AutoValue_AccountEvent_AccountDeletionEvent_AccountDeletionFailed(code, exception);
      }
    }
  }

  /**
   * The type of account login events.
   */

  public abstract static class AccountLoginEvent extends AccountEvent {

    @Override
    public final <A, E extends Exception> A match(
        final PartialFunctionType<AccountCreationEvent, A, E> on_creation,
        final PartialFunctionType<AccountDeletionEvent, A, E> on_deletion,
        final PartialFunctionType<AccountLoginEvent, A, E> on_login,
        final PartialFunctionType<AccountChanged, A, E> on_changed)
        throws E {
      return on_login.call(this);
    }

    /**
     * Match the type of event.
     *
     * @param <A>        The type of returned values
     * @param <E>        The type of raised exceptions
     * @param on_success Called for {@code AccountLoginSucceeded} values
     * @param on_failure Called for {@code AccountLoginFailed} values
     * @return The value returned by the matcher
     * @throws E If the matcher raises {@code E}
     */

    public abstract <A, E extends Exception> A matchLogin(
        PartialFunctionType<AccountLoginSucceeded, A, E> on_success,
        PartialFunctionType<AccountLoginFailed, A, E> on_failure)
        throws E;

    /**
     * Logging in succeeded.
     */

    @AutoValue
    public abstract static class AccountLoginSucceeded extends AccountLoginEvent {

      /**
       * @return The accepted credentials
       */

      public abstract AccountAuthenticationCredentials credentials();

      @Override
      public final <A, E extends Exception> A matchLogin(
          final PartialFunctionType<AccountLoginSucceeded, A, E> on_success,
          final PartialFunctionType<AccountLoginFailed, A, E> on_failure)
          throws E {
        return on_success.call(this);
      }

      /**
       * @return An event
       */

      public static AccountLoginSucceeded of(
          final AccountAuthenticationCredentials credentials) {
        return new AutoValue_AccountEvent_AccountLoginEvent_AccountLoginSucceeded(credentials);
      }
    }

    /**
     * Logging in failed.
     */

    @AutoValue
    public abstract static class AccountLoginFailed extends AccountLoginEvent {

      /**
       * The error codes that can be raised
       */

      public enum ErrorCode {

        /**
         * A profile or account configuration problem occurred (such as the user not having
         * selected a profile).
         */

        ERROR_PROFILE_CONFIGURATION,

        /**
         * A network problem occurred whilst trying to contact a remote server.
         */

        ERROR_NETWORK_EXCEPTION,

        /**
         * The provided credentials were rejected by the server.
         */

        ERROR_CREDENTIALS_INCORRECT,

        /**
         * The server responded with an error.
         */

        ERROR_SERVER_ERROR
      }

      /**
       * @return The error code
       */

      public abstract AccountLoginFailed.ErrorCode errorCode();

      /**
       * @return The exception raised during logging in, if any
       */

      public abstract OptionType<Exception> exception();

      @Override
      public final <A, E extends Exception> A matchLogin(
          final PartialFunctionType<AccountLoginSucceeded, A, E> on_success,
          final PartialFunctionType<AccountLoginFailed, A, E> on_failure)
          throws E {
        return on_failure.call(this);
      }

      /**
       * @param code      The error code
       * @param exception The exception raised, if any
       * @return An event
       */

      public static AccountLoginFailed of(
          final AccountLoginFailed.ErrorCode code,
          final OptionType<Exception> exception) {
        return new AutoValue_AccountEvent_AccountLoginEvent_AccountLoginFailed(code, exception);
      }
    }
  }
}
