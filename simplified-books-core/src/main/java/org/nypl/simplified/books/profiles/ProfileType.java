package org.nypl.simplified.books.profiles;

import java.io.IOException;

/**
 * <p>The readable and writable interface exposed by profiles.</p>
 * <p>A profile aggregates a display name, a set of accounts, a set of preferences, and a current
 * account. Profiles are assigned monotonically increasing identifiers by the application, but the
 * identifiers themselves carry no meaning. It is an error to depend on the values of identifiers
 * for any kind of program logic. Exactly one account may be current at any given time. It is the
 * responsibility of the application to pick an account provider to be used as the default to
 * derive accounts for newly created profiles.</p>
 * <p>Values of type {@code ProfileType} are required to be safe to read and write from multiple
 * threads concurrently.</p>
 */

public interface ProfileType extends ProfileReadableType {

  /**
   * Set the profile's preferences to the given value.
   *
   * @param preferences The new preferences
   */

  void preferencesUpdate(
      ProfilePreferences preferences) throws IOException;
}
