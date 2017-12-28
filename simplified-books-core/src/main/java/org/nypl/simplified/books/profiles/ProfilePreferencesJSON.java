package org.nypl.simplified.books.profiles;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.io7m.jfunctional.OptionType;
import com.io7m.jfunctional.PartialFunctionType;
import com.io7m.jfunctional.ProcedureType;
import com.io7m.jnull.NullCheck;
import com.io7m.junreachable.UnreachableCodeException;

import org.nypl.simplified.files.FileUtilities;
import org.nypl.simplified.json.core.JSONParseException;
import org.nypl.simplified.json.core.JSONParserUtilities;
import org.nypl.simplified.json.core.JSONSerializerUtilities;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

/**
 * Functions to serialize and deserialize profile preferences to/from JSON.
 */

public final class ProfilePreferencesJSON {

  private ProfilePreferencesJSON() {
    throw new UnreachableCodeException();
  }

  /**
   * Deserialize profile preferences from the given file.
   *
   * @param jom  A JSON object mapper
   * @param file A file
   * @return A parsed description
   * @throws IOException On I/O and/or parse errors
   */

  public static ProfilePreferences deserializeFromFile(
      final ObjectMapper jom,
      final File file)
      throws IOException {
    NullCheck.notNull(jom, "Object mapper");
    NullCheck.notNull(file, "File");
    return deserializeFromText(jom, FileUtilities.fileReadUTF8(file));
  }

  /**
   * Deserialize profile preferences from the given text.
   *
   * @param jom  A JSON object mapper
   * @param text A JSON string
   * @return A parsed description
   * @throws IOException On I/O and/or parse errors
   */

  public static ProfilePreferences deserializeFromText(
      final ObjectMapper jom,
      final String text) throws IOException {
    NullCheck.notNull(jom, "Object mapper");
    NullCheck.notNull(text, "Text");
    return deserializeFromJSON(jom, jom.readTree(text));
  }

  /**
   * Deserialize profile preferences from the given JSON node.
   *
   * @param jom  A JSON object mapper
   * @param node A JSON node
   * @return A parsed description
   * @throws JSONParseException On parse errors
   */

  public static ProfilePreferences deserializeFromJSON(
      final ObjectMapper jom,
      final JsonNode node)
      throws JSONParseException {
    NullCheck.notNull(jom, "Object mapper");
    NullCheck.notNull(node, "JSON");

    final ObjectNode obj =
        JSONParserUtilities.checkObject(null, node);

    final OptionType<ProfilePreferences.Age> age =
        JSONParserUtilities.getStringOptional(obj, "age")
        .mapPartial(new PartialFunctionType<String, ProfilePreferences.Age, JSONParseException>() {
          @Override
          public ProfilePreferences.Age call(
              final String x)
              throws JSONParseException {
            try {
              return ProfilePreferences.Age.valueOf(x);
            } catch (final IllegalArgumentException e) {
              throw new JSONParseException(e);
            }
          }
        });

    return ProfilePreferences.builder()
        .setAge(age)
        .build();
  }

  /**
   * Serialize profile preferences to JSON.
   *
   * @param jom A JSON object mapper
   * @return A serialized object
   */

  public static ObjectNode serializeToJSON(
      final ObjectMapper jom,
      final ProfilePreferences description) {
    NullCheck.notNull(jom, "Object mapper");
    NullCheck.notNull(description, "Description");

    final ObjectNode jo = jom.createObjectNode();

    description.age().map_(new ProcedureType<ProfilePreferences.Age>() {
      @Override
      public void call(final ProfilePreferences.Age x) {
        jo.put("age", x.name());
      }
    });

    return jo;
  }

  /**
   * Serialize profile preferences to a JSON string.
   *
   * @param jom A JSON object mapper
   * @return A JSON string
   * @throws IOException On serialization errors
   */

  public static String serializeToString(
      final ObjectMapper jom,
      final ProfilePreferences description)
      throws IOException {
    final ObjectNode jo = serializeToJSON(jom, description);
    final ByteArrayOutputStream bao = new ByteArrayOutputStream(1024);
    JSONSerializerUtilities.serialize(jo, bao);
    return bao.toString("UTF-8");
  }
}
