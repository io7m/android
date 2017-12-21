package org.nypl.simplified.books.accounts;

import com.google.auto.value.AutoValue;
import com.io7m.jnull.NullCheck;
import com.io7m.jnull.Nullable;

import java.io.Serializable;

/**
 * The type of account PINs.
 *
 * Account PINs are expected to be 4 digit numbers, but the type does not
 * (currently) enforce this fact.
 */

@AutoValue
public abstract class AccountPIN
{
  AccountPIN() {

  }

  /**
   * Construct a PIN.
   *
   * @param in_value The raw PIN value
   */

  public static AccountPIN create(String in_value)
  {
    return new AutoValue_AccountPIN(in_value);
  }

  /**
   * @return The actual PIN value
   */

  public abstract String value();

  @Override
  public String toString() {
    return value();
  }
}
