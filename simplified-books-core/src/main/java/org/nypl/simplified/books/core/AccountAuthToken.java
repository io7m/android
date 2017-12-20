package org.nypl.simplified.books.core;

import com.io7m.jnull.NullCheck;
import com.io7m.jnull.Nullable;
import org.nypl.simplified.files.FileUtilities;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;

/**
 * The type of account barcodes.
 *
 * Account barcodes are expected to be 5-14 digit numbers, but the type does not
 * (currently) enforce this fact.
 */

public final class AccountAuthToken implements Serializable
{
  private static final long serialVersionUID = 1L;
  private final String value;

  /**
   * Construct a barcode.
   *
   * @param in_value The raw barcode value
   */

  public AccountAuthToken(
    final String in_value)
  {
    this.value = NullCheck.notNull(in_value);
  }

  @Override public boolean equals(
    final @Nullable Object obj)
  {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (this.getClass() != obj.getClass()) {
      return false;
    }
    final AccountAuthToken other = (AccountAuthToken) obj;
    return this.value.equals(other.value);
  }

  @Override public int hashCode()
  {
    return this.value.hashCode();
  }

  @Override public String toString()
  {
    return this.value;
  }

}
