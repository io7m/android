package org.nypl.simplified.books.accounts;

/**
 * <p>A description of the details of authentication.</p>
 */

public interface AccountProviderAuthenticationDescriptionType {

  /**
   * @return The required length of passcodes, or {@code 0} if no specific length is required
   */

  int passCodeLength();

  /**
    *@return {@code true} iff passcodes may contain letters
   */

  boolean passCodeMayContainLetters();
}
