package org.nypl.simplified.books.accounts;

/**
 * A unique account identifier.
 */

public final class AccountID implements Comparable<AccountID> {
  private final int id;

  /**
   * Construct a account identifier.
   *
   * @param id A non-negative integer
   */

  public AccountID(int id) {
    if (id < 0) {
      throw new IllegalArgumentException("Account identifiers must be non-negative");
    }

    this.id = id;
  }

  /**
   * @return The raw integer value of the identifier
   */

  public int value()
  {
    return this.id;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    AccountID accountID = (AccountID) o;
    return id == accountID.id;
  }

  @Override
  public String toString() {
    return Integer.toString(id);
  }

  @Override
  public int hashCode() {
    return id;
  }

  @Override
  public int compareTo(AccountID other) {
    return Integer.compare(this.id, other.id);
  }
}
