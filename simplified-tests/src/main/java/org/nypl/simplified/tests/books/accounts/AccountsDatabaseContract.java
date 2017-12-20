package org.nypl.simplified.tests.books.accounts;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.nypl.simplified.books.accounts.AccountType;
import org.nypl.simplified.books.core.LogUtilities;
import org.nypl.simplified.books.accounts.AccountsDatabaseException;
import org.nypl.simplified.books.accounts.AccountsDatabase;
import org.nypl.simplified.books.accounts.AccountsDatabaseType;
import org.nypl.simplified.books.profiles.ProfileID;
import org.nypl.simplified.files.DirectoryUtilities;
import org.nypl.simplified.files.FileUtilities;
import org.nypl.simplified.tests.books.profiles.FakeAccountProvider;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.net.URI;

public abstract class AccountsDatabaseContract {

  private static final Logger LOG = LogUtilities.getLog(AccountsDatabaseContract.class);

  @Rule
  public ExpectedException expected = ExpectedException.none();

  /**
   * An exception matcher that checks to see if the given accounts database exception has
   * at least one cause of the given type and with the given exception message.
   *
   * @param <T> The cause type
   */

  private static final class CausesContains<T extends Exception>
      extends BaseMatcher<AccountsDatabaseException> {
    private final Class<T> exception_type;
    private final String message;

    CausesContains(
        Class<T> exception_type,
        String message) {
      this.exception_type = exception_type;
      this.message = message;
    }

    @Override
    public boolean matches(Object item) {
      if (item instanceof AccountsDatabaseException) {
        AccountsDatabaseException ex = (AccountsDatabaseException) item;
        for (Exception c : ex.causes()) {
          LOG.error("Cause: ", c);
          if (exception_type.isAssignableFrom(c.getClass()) && c.getMessage().contains(message)) {
            return true;
          }
        }
      }
      return false;
    }

    @Override
    public void describeTo(Description description) {
      description.appendText("must throw AccountsDatabaseException");
      description.appendText(" with at least one cause of type " + exception_type);
      description.appendText(" with a message containing '" + message + "'");
    }
  }

  @Test
  public final void testOpenExistingNotDirectory()
      throws Exception {

    final File f_tmp = DirectoryUtilities.directoryCreateTemporary();
    final File f_pro = new File(f_tmp, "profiles");
    f_pro.mkdirs();
    final File f_p = new File(f_pro, "0");
    f_p.mkdirs();
    final File f_acc = new File(f_p, "accounts");

    FakeProfile owner = new FakeProfile(new ProfileID(0), f_pro, "Kermit");
    FileUtilities.fileWriteUTF8(f_acc, "Hello!");

    expected.expect(AccountsDatabaseException.class);
    expected.expect(new CausesContains<>(IOException.class, "Not a directory"));
    AccountsDatabase.open(owner, f_acc);
  }

  @Test
  public final void testOpenExistingBadSubdirectory()
      throws Exception {

    final File f_tmp = DirectoryUtilities.directoryCreateTemporary();
    final File f_pro = new File(f_tmp, "profiles");
    f_pro.mkdirs();
    final File f_p = new File(f_pro, "0");
    f_p.mkdirs();
    final File f_acc = new File(f_p, "accounts");
    f_acc.mkdirs();
    final File f_a = new File(f_acc, "xyz");
    f_a.mkdirs();

    FakeProfile owner = new FakeProfile(new ProfileID(0), f_pro, "Kermit");

    expected.expect(AccountsDatabaseException.class);
    expected.expect(new CausesContains<>(
        IOException.class, "Could not parse directory name as an account ID"));
    AccountsDatabase.open(owner, f_acc);
  }

  @Test
  public final void testOpenExistingJSONMissing()
      throws Exception {

    final File f_tmp = DirectoryUtilities.directoryCreateTemporary();
    final File f_pro = new File(f_tmp, "profiles");
    f_pro.mkdirs();
    final File f_p = new File(f_pro, "0");
    f_p.mkdirs();
    final File f_acc = new File(f_p, "accounts");
    f_acc.mkdirs();
    final File f_a = new File(f_acc, "0");
    f_a.mkdirs();

    FakeProfile owner = new FakeProfile(new ProfileID(0), f_pro, "Kermit");

    expected.expect(AccountsDatabaseException.class);
    expected.expect(new CausesContains<>(IOException.class, "Could not parse account: "));
    AccountsDatabase.open(owner, f_acc);
  }

  @Test
  public final void testOpenExistingJSONUnparseable()
      throws Exception {

    final File f_tmp = DirectoryUtilities.directoryCreateTemporary();
    final File f_pro = new File(f_tmp, "profiles");
    f_pro.mkdirs();
    final File f_p = new File(f_pro, "0");
    f_p.mkdirs();
    final File f_acc = new File(f_p, "accounts");
    f_acc.mkdirs();
    final File f_a = new File(f_acc, "0");
    f_a.mkdirs();

    FakeProfile owner = new FakeProfile(new ProfileID(0), f_pro, "Kermit");

    final File f_f = new File(f_a, "account.json");
    FileUtilities.fileWriteUTF8(f_f, "} { this is not JSON { } { }");

    expected.expect(AccountsDatabaseException.class);
    expected.expect(new CausesContains<>(IOException.class, "Could not parse account: "));
    AccountsDatabase.open(owner, f_acc);
  }

  @Test
  public final void testOpenExistingEmpty()
      throws Exception {

    final File f_tmp = DirectoryUtilities.directoryCreateTemporary();
    final File f_pro = new File(f_tmp, "profiles");
    f_pro.mkdirs();
    final File f_p = new File(f_pro, "0");
    f_p.mkdirs();
    final File f_acc = new File(f_p, "accounts");

    FakeProfile owner = new FakeProfile(new ProfileID(0), f_pro, "Kermit");

    AccountsDatabaseType db = AccountsDatabase.open(owner, f_acc);
    Assert.assertEquals(0, db.accounts().size());
    Assert.assertEquals(f_acc, db.directory());
    Assert.assertSame(owner, db.owner());
  }

  @Test
  public final void testCreateAccount()
      throws Exception {

    final File f_tmp = DirectoryUtilities.directoryCreateTemporary();
    final File f_pro = new File(f_tmp, "profiles");
    f_pro.mkdirs();
    final File f_p = new File(f_pro, "0");
    f_p.mkdirs();
    final File f_acc = new File(f_p, "accounts");

    FakeProfile owner = new FakeProfile(new ProfileID(0), f_pro, "Kermit");

    AccountsDatabaseType db =
        AccountsDatabase.open(owner, f_acc);

    FakeAccountProvider provider0 =
        new FakeAccountProvider("http://example.com/accounts0/");
    FakeAccountProvider provider1 =
        new FakeAccountProvider("http://example.com/accounts1/");

    AccountType acc0 = db.createAccount(provider0);
    AccountType acc1 = db.createAccount(provider1);

    Assert.assertTrue("Account 0 directory exists", acc0.directory().isDirectory());
    Assert.assertTrue("Account 1 directory exists", acc1.directory().isDirectory());

    Assert.assertTrue(
        "Account 0 file exists",
        new File(acc0.directory(), "account.json").isFile());
    Assert.assertTrue(
        "Account 1 file exists",
        new File(acc1.directory(), "account.json").isFile());

    Assert.assertEquals(URI.create("http://example.com/accounts0/"), acc0.provider());
    Assert.assertEquals(URI.create("http://example.com/accounts1/"), acc1.provider());

    Assert.assertNotEquals(acc0.id(), acc1.id());
    Assert.assertNotEquals(acc0.directory(), acc1.directory());
  }

  @Test
  public final void testCreateReopen()
      throws Exception {

    final File f_tmp = DirectoryUtilities.directoryCreateTemporary();
    final File f_pro = new File(f_tmp, "profiles");
    f_pro.mkdirs();
    final File f_p = new File(f_pro, "0");
    f_p.mkdirs();
    final File f_acc = new File(f_p, "accounts");

    FakeProfile owner = new FakeProfile(new ProfileID(0), f_pro, "Kermit");

    AccountsDatabaseType db0 = AccountsDatabase.open(owner, f_acc);

    FakeAccountProvider provider0 =
        new FakeAccountProvider("http://example.com/accounts0/");
    FakeAccountProvider provider1 =
        new FakeAccountProvider("http://example.com/accounts1/");

    AccountType acc0 = db0.createAccount(provider0);
    AccountType acc1 = db0.createAccount(provider1);

    AccountsDatabaseType db1 = AccountsDatabase.open(owner, f_acc);

    AccountType acr0 = db1.accounts().get(acc0.id());
    AccountType acr1 = db1.accounts().get(acc1.id());

    Assert.assertEquals(acc0.id(), acr0.id());
    Assert.assertEquals(acc1.id(), acr1.id());
    Assert.assertEquals(acc0.directory(), acr0.directory());
    Assert.assertEquals(acc1.directory(), acr1.directory());
    Assert.assertEquals(acc0.provider(), acr0.provider());
    Assert.assertEquals(acc1.provider(), acr1.provider());
  }
}
