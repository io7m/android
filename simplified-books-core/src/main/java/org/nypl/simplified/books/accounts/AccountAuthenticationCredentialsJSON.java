package org.nypl.simplified.books.accounts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.io7m.jfunctional.FunctionType;
import com.io7m.jfunctional.PartialFunctionType;
import com.io7m.jfunctional.ProcedureType;
import com.io7m.jnull.NullCheck;
import com.io7m.junreachable.UnreachableCodeException;

import org.nypl.drm.core.AdobeDeviceID;
import org.nypl.drm.core.AdobeUserID;
import org.nypl.drm.core.AdobeVendorID;
import org.nypl.simplified.http.core.HTTPOAuthToken;
import org.nypl.simplified.json.core.JSONParseException;
import org.nypl.simplified.json.core.JSONParserUtilities;
import org.nypl.simplified.json.core.JSONSerializerUtilities;
import org.nypl.simplified.opds.core.DRMLicensor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Functions for serializing/deserializing account credentials.
 */

public final class AccountAuthenticationCredentialsJSON {
  private AccountAuthenticationCredentialsJSON() {
    throw new UnreachableCodeException();
  }

  /**
   * Serialize the given credentials to a JSON object, and serialize that to a
   * UTF-8 string.
   *
   * @param credentials The credentials.
   * @return A string of JSON
   * @throws IOException On I/O or serialization errors
   */

  public static String serializeToText(
      final AccountAuthenticationCredentials credentials)
      throws IOException {
    NullCheck.notNull(credentials, "Credentials");

    final ObjectNode jo = AccountAuthenticationCredentialsJSON.serializeToJSON(credentials);
    final ByteArrayOutputStream bao = new ByteArrayOutputStream(1024);
    JSONSerializerUtilities.serialize(jo, bao);
    return bao.toString("UTF-8");
  }

  /**
   * Serialize the given credentials to a JSON object.
   *
   * @param credentials The credentials.
   * @return A JSON object
   */

  public static ObjectNode serializeToJSON(
      final AccountAuthenticationCredentials credentials) {
    NullCheck.notNull(credentials, "Credentials");

    final ObjectMapper jom = new ObjectMapper();
    final ObjectNode jo = jom.createObjectNode();
    jo.put("username", credentials.barcode().value());
    jo.put("password", credentials.pin().value());

    credentials.oAuthToken().map_(
        new ProcedureType<HTTPOAuthToken>() {
          @Override
          public void call(final HTTPOAuthToken x) {
            jo.put("oauth_token", x.value());
          }
        });

    credentials.adobeCredentials().map_(
        new ProcedureType<AccountAuthenticationAdobeCredentials>() {
          @Override
          public void call(AccountAuthenticationAdobeCredentials x) {
            final ObjectNode adobe_jo = jom.createObjectNode();
            adobe_jo.put("user_id", x.userID().getValue());
            adobe_jo.put("device_id", x.deviceID().getValue());
            adobe_jo.put("vendor_id", x.vendorID().getValue());
            x.deviceToken().map_(new ProcedureType<AccountAdobeDeviceToken>() {
              @Override
              public void call(AccountAdobeDeviceToken x) {
                adobe_jo.put("device_token", x.value());
              }
            });
            jo.set("adobe_credentials", adobe_jo);
          }
        });

    final ArrayNode jo_drm = jom.createArrayNode();
    for (final DRMLicensor d : credentials.drmLicensors()) {
      final ObjectNode d_node = jom.createObjectNode();
      d_node.put("vendor", d.getVendor());
      d_node.put("token", d.getClientToken());
      d.getDeviceManager().map_(new ProcedureType<String>() {
        @Override
        public void call(String x) {
          d_node.put("device_manager_url", x);
        }
      });
      jo_drm.add(d_node);
    }
    jo.set("drm_licensors", jo_drm);

    credentials.authenticationProvider().map_(new ProcedureType<AccountAuthenticationProvider>() {
      @Override
      public void call(AccountAuthenticationProvider x) {
        jo.put("auth_provider", x.value());
      }
    });

    credentials.patron().map_(new ProcedureType<AccountPatron>() {
      @Override
      public void call(AccountPatron x) {
        jo.put("patron", x.value());
      }
    });

    return jo;
  }

  /**
   * Deserialize the given text, which is assumed to be a JSON object
   * representing account credentials.
   *
   * @param text The credentials text.
   * @return Account credentials
   * @throws IOException On I/O or serialization errors
   */

  public static AccountAuthenticationCredentials deserializeFromText(final String text)
      throws IOException {
    NullCheck.notNull(text);
    final ObjectMapper jom = new ObjectMapper();
    return AccountAuthenticationCredentialsJSON.deserializeFromJSON(jom.readTree(text));
  }

  /**
   * Deserialize the given JSON node, which is assumed to be a JSON object
   * representing account credentials.
   *
   * @param node Credentials as a JSON node.
   * @return Account credentials
   * @throws JSONParseException On parse errors
   */

  public static AccountAuthenticationCredentials deserializeFromJSON(
      final JsonNode node)
      throws JSONParseException {
    NullCheck.notNull(node, "Node");

    final ObjectNode obj = JSONParserUtilities.checkObject(null, node);

    final AccountBarcode user =
        AccountBarcode.create(JSONParserUtilities.getString(obj, "username"));
    final AccountPIN pass =
        AccountPIN.create(JSONParserUtilities.getString(obj, "password"));
    final AccountAuthenticationCredentials.Builder builder =
        AccountAuthenticationCredentials.builder(pass, user);

    builder.setPatron(
        JSONParserUtilities.getStringOptional(obj, "patron")
            .map(new FunctionType<String, AccountPatron>() {
              @Override
              public AccountPatron call(String x) {
                return AccountPatron.create(x);
              }
            }));

    builder.setAuthenticationProvider(
        JSONParserUtilities.getStringOptional(obj, "auth_provider")
            .map(new FunctionType<String, AccountAuthenticationProvider>() {
              @Override
              public AccountAuthenticationProvider call(String x) {
                return AccountAuthenticationProvider.create(x);
              }
            }));

    builder.setOAuthToken(
        JSONParserUtilities.getStringOptional(obj, "oauth_token")
            .map(new FunctionType<String, HTTPOAuthToken>() {
              @Override
              public HTTPOAuthToken call(String x) {
                return HTTPOAuthToken.create(x);
              }
            }));

    builder.setAdobeCredentials(
        JSONParserUtilities.getObjectOptional(obj, "adobe_credentials")
            .mapPartial(new PartialFunctionType<ObjectNode, AccountAuthenticationAdobeCredentials, JSONParseException>() {
              @Override
              public AccountAuthenticationAdobeCredentials call(ObjectNode x)
                  throws JSONParseException {

                final AccountAuthenticationAdobeCredentials.Builder adobe_builder =
                    AccountAuthenticationAdobeCredentials.builder(
                        new AdobeVendorID(JSONParserUtilities.getString(x, "vendor_id")),
                        new AdobeUserID(JSONParserUtilities.getString(x, "user_id")),
                        new AdobeDeviceID(JSONParserUtilities.getString(x, "device_id")));

                adobe_builder.setDeviceToken(
                    JSONParserUtilities.getStringOptional(x, "device_token")
                        .map(new FunctionType<String, AccountAdobeDeviceToken>() {
                          @Override
                          public AccountAdobeDeviceToken call(String x) {
                            return AccountAdobeDeviceToken.create(x);
                          }
                        }));

                return adobe_builder.build();
              }
            }));

    Set<DRMLicensor> licensors = new HashSet<>();
    ArrayNode la = JSONParserUtilities.getArray(obj, "drm_licensors");
    for (int index = 0; index < la.size(); ++index) {
      ObjectNode la_obj = JSONParserUtilities.checkObject(null, la.get(index));
      DRMLicensor licensor = new DRMLicensor(
          JSONParserUtilities.getString(la_obj, "vendor"),
          JSONParserUtilities.getString(la_obj, "client_token"),
          JSONParserUtilities.getStringOptional(la_obj, "device_manager_url"));
      licensors.add(licensor);
    }

    builder.setDrmLicensors(licensors);
    return builder.build();
  }
}
