package org.nypl.simplified.tests.books.accounts;

import com.io7m.jnull.NullCheck;
import com.io7m.junreachable.UnimplementedCodeException;

import org.nypl.simplified.books.accounts.AccountID;
import org.nypl.simplified.books.accounts.AccountType;
import org.nypl.simplified.books.profiles.ProfileID;
import org.nypl.simplified.books.profiles.ProfilePreferences;
import org.nypl.simplified.books.profiles.ProfileReadableType;
import org.nypl.simplified.books.profiles.ProfileType;

import java.io.File;
import java.io.IOException;
import java.util.SortedMap;

public final class FakeProfile implements ProfileType {

  private final ProfileID id;
  private final File directory;
  private final String display_name;
  private ProfilePreferences prefs;

  public FakeProfile(
      final ProfileID id,
      final File directory,
      final String display_name) {
    this.id = NullCheck.notNull(id, "id");
    this.directory = NullCheck.notNull(directory, "directory");
    this.display_name = NullCheck.notNull(display_name, "display name");
    this.prefs = ProfilePreferences.builder().build();
  }

  @Override
  public ProfileID id() {
    return this.id;
  }

  @Override
  public boolean isAnonymous() {
    return id.id() == 0;
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
  public AccountType accountCurrent() {
    throw new UnimplementedCodeException();
  }

  @Override
  public SortedMap<AccountID, AccountType> accounts() {
    throw new UnimplementedCodeException();
  }

  @Override
  public ProfilePreferences preferences() {
    return prefs;
  }

  @Override
  public void preferencesUpdate(
      final ProfilePreferences preferences)
      throws IOException {
    this.prefs = NullCheck.notNull(preferences, "Preferences");
  }

  @Override
  public int compareTo(final ProfileReadableType other) {
    return displayName().compareTo(NullCheck.notNull(other, "Other").displayName());
  }
}
