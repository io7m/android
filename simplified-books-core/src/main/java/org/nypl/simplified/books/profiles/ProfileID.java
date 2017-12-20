package org.nypl.simplified.books.profiles;

/**
 * A unique profile identifier.
 */

public final class ProfileID implements Comparable<ProfileID> {
  private final int id;

  /**
   * Construct a profile identifier.
   *
   * @param id A non-negative integer
   */

  public ProfileID(int id) {
    if (id < 0) {
      throw new IllegalArgumentException("Profile identifiers must be non-negative");
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

    ProfileID profileID = (ProfileID) o;
    return id == profileID.id;
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
  public int compareTo(ProfileID other) {
    return Integer.compare(this.id, other.id);
  }
}
