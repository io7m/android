package org.nypl.simplified.books.accounts;

import com.google.auto.value.AutoValue;

/**
 * A unique account identifier.
 */

@AutoValue
public abstract class AccountID implements Comparable<AccountID> {

  AccountID() {

  }

  /**
   * Construct a account identifier.
   *
   * @param id A non-negative integer
   */

  public static AccountID create(int id)
  {
    if (id < 0) {
      throw new IllegalArgumentException("Account identifiers must be non-negative");
    }
    return new AutoValue_AccountID(id);
  }

  /**
   * @return The raw identifier value
   */

  public abstract int id();

  @Override
  public int compareTo(AccountID other) {
    return Integer.compare(this.id(), other.id());
  }
}
