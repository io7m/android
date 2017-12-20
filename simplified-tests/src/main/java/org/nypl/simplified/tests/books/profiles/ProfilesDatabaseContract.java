package org.nypl.simplified.tests.books.profiles;

import com.io7m.jfunctional.Option;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.core.StringContains;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.nypl.simplified.books.core.LogUtilities;
import org.nypl.simplified.books.profiles.ProfileDatabaseException;
import org.nypl.simplified.books.profiles.ProfileID;
import org.nypl.simplified.books.profiles.ProfileType;
import org.nypl.simplified.books.profiles.ProfilesDatabase;
import org.nypl.simplified.books.profiles.ProfilesDatabaseType;
import org.nypl.simplified.files.DirectoryUtilities;
import org.nypl.simplified.files.FileUtilities;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;

public abstract class ProfilesDatabaseContract {

  private static final Logger LOG = LogUtilities.getLog(ProfilesDatabaseContract.class);

  @Rule
  public ExpectedException expected = ExpectedException.none();

  /**
   * An exception matcher that checks to see if the given profile database exception has
   * at least one cause of the given type and with the given exception message.
   *
   * @param <T> The cause type
   */

  private static final class CausesContains<T extends Exception>
      extends BaseMatcher<ProfileDatabaseException> {
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
      if (item instanceof ProfileDatabaseException) {
        ProfileDatabaseException ex = (ProfileDatabaseException) item;
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
      description.appendText("must throw ProfileDatabaseException");
      description.appendText(" with at least one cause of type " + exception_type);
      description.appendText(" with a message containing '" + message + "'");
    }
  }

  @Test
  public final void testOpenExistingNotDirectory()
      throws Exception {
    final File f_tmp = DirectoryUtilities.directoryCreateTemporary();
    final File f_pro = new File(f_tmp, "profiles");
    FileUtilities.fileWriteUTF8(f_pro, "Hello!");

    expected.expect(ProfileDatabaseException.class);
    expected.expect(new CausesContains<>(IOException.class, "Not a directory"));
    ProfilesDatabase.open(f_pro);
  }

  @Test
  public final void testOpenExistingBadSubdirectory()
      throws Exception {
    final File f_tmp = DirectoryUtilities.directoryCreateTemporary();
    final File f_pro = new File(f_tmp, "profiles");

    f_pro.mkdirs();
    final File f_bad = new File(f_pro, "not-a-number");
    f_bad.mkdirs();

    expected.expect(ProfileDatabaseException.class);
    expected.expect(new CausesContains<>(
        IOException.class, "Could not parse directory name as profile ID"));
    ProfilesDatabase.open(f_pro);
  }

  @Test
  public final void testOpenExistingJSONMissing()
      throws Exception {
    final File f_tmp = DirectoryUtilities.directoryCreateTemporary();
    final File f_pro = new File(f_tmp, "profiles");

    f_pro.mkdirs();
    final File f_0 = new File(f_pro, "0");
    f_0.mkdirs();

    expected.expect(ProfileDatabaseException.class);
    expected.expect(new CausesContains<>(IOException.class, "Could not parse profile: "));
    ProfilesDatabase.open(f_pro);
  }

  @Test
  public final void testOpenExistingJSONUnparseable()
      throws Exception {
    final File f_tmp = DirectoryUtilities.directoryCreateTemporary();
    final File f_pro = new File(f_tmp, "profiles");

    f_pro.mkdirs();
    final File f_0 = new File(f_pro, "0");
    f_0.mkdirs();
    final File f_p = new File(f_0, "profile.json");
    FileUtilities.fileWriteUTF8(f_p, "} { this is not JSON { } { }");

    expected.expect(ProfileDatabaseException.class);
    expected.expect(new CausesContains<>(IOException.class, "Could not parse profile: "));
    ProfilesDatabase.open(f_pro);
  }

  @Test
  public final void testOpenExistingEmpty()
      throws Exception {
    final File f_tmp = DirectoryUtilities.directoryCreateTemporary();
    final File f_pro = new File(f_tmp, "profiles");

    ProfilesDatabaseType db = ProfilesDatabase.open(f_pro);
    Assert.assertEquals(0, db.profiles().size());
    Assert.assertEquals(f_pro, db.directory());
    Assert.assertTrue(db.currentProfile().isNone());
  }

  @Test
  public final void testOpenCreateProfiles()
      throws Exception {
    final File f_tmp = DirectoryUtilities.directoryCreateTemporary();
    final File f_pro = new File(f_tmp, "profiles");

    ProfilesDatabaseType db = ProfilesDatabase.open(f_pro);

    FakeAccountProvider acc = new FakeAccountProvider("urn:fake");
    ProfileType p0 = db.createProfile(acc, "Kermit");
    ProfileType p1 = db.createProfile(acc, "Gonzo");
    ProfileType p2 = db.createProfile(acc, "Beaker");

    Assert.assertEquals("Kermit", p0.displayName());
    Assert.assertEquals("Gonzo", p1.displayName());
    Assert.assertEquals("Beaker", p2.displayName());

    Assert.assertNotEquals(p0.id(), p1.id());
    Assert.assertNotEquals(p0.id(), p2.id());
    Assert.assertNotEquals(p1.id(), p2.id());

    Assert.assertTrue(
        "Kermit profile exists",
        p0.directory().isDirectory());

    Assert.assertTrue(
        "Kermit profile file exists",
        new File(p0.directory(), "profile.json").isFile());

    Assert.assertTrue(
        "Gonzo profile exists",
        p1.directory().isDirectory());

    Assert.assertTrue(
        "Gonzo profile file exists",
        new File(p1.directory(), "profile.json").isFile());

    Assert.assertTrue(
        "Beaker profile exists",
        p1.directory().isDirectory());

    Assert.assertTrue(
        "Beaker profile file exists",
        new File(p2.directory(), "profile.json").isFile());

    Assert.assertFalse(p0.isCurrent());
    Assert.assertFalse(p1.isCurrent());
    Assert.assertFalse(p2.isCurrent());
    Assert.assertTrue(db.currentProfile().isNone());
  }

  @Test
  public final void testOpenCreateReopen()
      throws Exception {
    final File f_tmp = DirectoryUtilities.directoryCreateTemporary();
    final File f_pro = new File(f_tmp, "profiles");

    ProfilesDatabaseType db0 = ProfilesDatabase.open(f_pro);
    FakeAccountProvider acc = new FakeAccountProvider("urn:fake");
    ProfileType p0 = db0.createProfile(acc, "Kermit");
    ProfileType p1 = db0.createProfile(acc, "Gonzo");
    ProfileType p2 = db0.createProfile(acc, "Beaker");

    ProfilesDatabaseType db1 = ProfilesDatabase.open(f_pro);
    ProfileType pr0 = db1.profiles().get(p0.id());
    ProfileType pr1 = db1.profiles().get(p1.id());
    ProfileType pr2 = db1.profiles().get(p2.id());

    Assert.assertEquals(p0.directory(), pr0.directory());
    Assert.assertEquals(p1.directory(), pr1.directory());
    Assert.assertEquals(p2.directory(), pr2.directory());

    Assert.assertEquals(p0.displayName(), pr0.displayName());
    Assert.assertEquals(p1.displayName(), pr1.displayName());
    Assert.assertEquals(p2.displayName(), pr2.displayName());

    Assert.assertEquals(p0.id(), pr0.id());
    Assert.assertEquals(p1.id(), pr1.id());
    Assert.assertEquals(p2.id(), pr2.id());
  }

  @Test
  public final void testCreateProfileDuplicate()
      throws Exception {
    final File f_tmp = DirectoryUtilities.directoryCreateTemporary();
    final File f_pro = new File(f_tmp, "profiles");

    ProfilesDatabaseType db = ProfilesDatabase.open(f_pro);

    FakeAccountProvider acc = new FakeAccountProvider("urn:fake");
    ProfileType p0 = db.createProfile(acc, "Kermit");

    expected.expect(ProfileDatabaseException.class);
    expected.expectMessage(StringContains.containsString("Display name is already used"));
    db.createProfile(acc, "Kermit");
  }

  @Test
  public final void testSetCurrent()
      throws Exception {
    final File f_tmp = DirectoryUtilities.directoryCreateTemporary();
    final File f_pro = new File(f_tmp, "profiles");

    ProfilesDatabaseType db0 = ProfilesDatabase.open(f_pro);
    FakeAccountProvider acc = new FakeAccountProvider("urn:fake");
    ProfileType p0 = db0.createProfile(acc, "Kermit");

    db0.setProfileCurrent(p0.id());

    Assert.assertTrue(p0.isCurrent());
    Assert.assertEquals(Option.some(p0.id()), db0.currentProfile());
  }

  @Test
  public final void testSetCurrentNonexistent()
      throws Exception {
    final File f_tmp = DirectoryUtilities.directoryCreateTemporary();
    final File f_pro = new File(f_tmp, "profiles");

    ProfilesDatabaseType db0 = ProfilesDatabase.open(f_pro);
    FakeAccountProvider acc = new FakeAccountProvider("urn:fake");
    ProfileType p0 = db0.createProfile(acc, "Kermit");

    expected.expect(ProfileDatabaseException.class);
    expected.expectMessage(StringContains.containsString("Profile does not exist"));
    db0.setProfileCurrent(new ProfileID(23));
  }

}
