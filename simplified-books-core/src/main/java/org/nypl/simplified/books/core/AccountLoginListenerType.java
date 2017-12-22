package org.nypl.simplified.books.core;

import com.io7m.jfunctional.OptionType;

import org.nypl.simplified.books.accounts.AccountAuthenticationCredentials;

/**
 * The type of listeners for account login operations.
 */

public interface AccountLoginListenerType extends AccountSyncListenerType {

  /**
   * Logging in failed due to rejected credentials.
   */

  void onAccountLoginFailureCredentialsIncorrect();

  /**
   * Logging in failed due to a server error.
   *
   * @param code The HTTP status code
   */

  void onAccountLoginFailureServerError(int code);

  /**
   * Logging in failed due to a local error.
   *
   * @param error   The exception raised, if any
   * @param message The error message
   */

  void onAccountLoginFailureLocalError(
      final OptionType<Throwable> error,
      final String message);

  /**
   * Logging in succeeded.
   *
   * @param credentials The current account credentials
   */

  void onAccountLoginSuccess(
      AccountAuthenticationCredentials credentials);

  /**
   * Logging in failed due to a failure with device activation.
   *
   * @param message The error message
   */

  void onAccountLoginFailureDeviceActivationError(String message);

}
