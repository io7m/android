package org.nypl.simplified.tests.books.accounts;

import com.io7m.jnull.NullCheck;
import com.io7m.junreachable.UnimplementedCodeException;

import org.nypl.simplified.books.accounts.AccountID;
import org.nypl.simplified.books.accounts.AccountType;
import org.nypl.simplified.books.profiles.ProfileID;
import org.nypl.simplified.books.profiles.ProfileType;

import java.io.File;
import java.util.SortedMap;

public final class FakeProfile implements ProfileType {

  private final ProfileID id;
  private final File directory;
  private final String display_name;

  public FakeProfile(
      ProfileID id,
      File directory,
      String display_name) {
    this.id = NullCheck.notNull(id, "id");
    this.directory = NullCheck.notNull(directory, "directory");
    this.display_name = NullCheck.notNull(display_name, "display name");
  }

  @Override
  public ProfileID id() {
    return this.id;
  }

  @Override
  public File directory() {
    return this.directory;
  }

  @Override
  public String displayName() {
    return this.display_name;
  }

  @Override
  public boolean isCurrent() {
    return false;
  }

  @Override
  public AccountID accountCurrent() {
    throw new UnimplementedCodeException();
  }

  @Override
  public SortedMap<AccountID, AccountType> accounts() {
    throw new UnimplementedCodeException();
  }
}
