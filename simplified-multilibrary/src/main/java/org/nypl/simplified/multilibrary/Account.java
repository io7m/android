package org.nypl.simplified.multilibrary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.io7m.jfunctional.Option;
import com.io7m.jfunctional.OptionType;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.Serializable;
import java.net.URI;

public class Account implements Serializable {

  private Integer id;
  private String path_component;
  private String name;
  private String subtitle;
  private String logo;
  private boolean needs_auth;
  private boolean supports_simplye_sync;
  private boolean supports_barcode_scanner;
  private boolean supports_barcode_display;
  private boolean supports_reservations;
  private boolean supports_card_creator;
  private boolean supports_help_center;
  private String support_email;
  private String card_creator_url;
  private String catalog_url;
  private String main_color;
  private String eula;
  private String content_license;
  private String privacy_policy;
  private String catalog_url_under_13;
  private String catalog_url_13_and_over;
  private Integer pin_length;
  private boolean pin_allows_letters;

  public String getSupportEmail() {
    return support_email;
  }

  /**
   * @return the ID
   */
  public int getId() {
    return this.id;
  }


  /**
   * @param account The Json Account
   */
  public Account(final JSONObject account) {

    try {
      this.id = account.getInt("id");
      this.path_component = account.getString("pathComponent");
      this.name = account.getString("name");
      this.subtitle = account.getString("subtitle");
      this.logo = account.getString("logo");
      this.catalog_url = account.getString("catalogUrl");
      if (!account.isNull("catalogUrlUnder13")) {
        this.catalog_url_under_13 = account.getString("catalogUrlUnder13");
      }
      if (!account.isNull("catalogUrl13")) {
        this.catalog_url_13_and_over = account.getString("catalogUrl13");
      }
      if (!account.isNull("cardCreatorUrl")) {
        this.card_creator_url = account.getString("cardCreatorUrl");
      }
      if (!account.isNull("needsAuth")) {
        this.needs_auth = account.getBoolean("needsAuth");
      }
      if (!account.isNull("supportsReservations")) {
        this.supports_reservations = account.getBoolean("supportsReservations");
      }
      if (!account.isNull("supportsCardCreator")) {
        this.supports_card_creator = account.getBoolean("supportsCardCreator");
      }
      if (!account.isNull("supportsSimplyESync")) {
        this.supports_simplye_sync = account.getBoolean("supportsSimplyESync");
      }
      if (!account.isNull("supportsHelpCenter")) {
        this.supports_help_center = account.getBoolean("supportsHelpCenter");
      }
      if (!account.isNull("supportsBarcodeScanner")) {
        this.supports_barcode_scanner = account.getBoolean("supportsBarcodeScanner");
      }
      if (!account.isNull("supportsBarcodeDisplay")) {
        this.supports_barcode_display = account.getBoolean("supportsBarcodeDisplay");
      }
      if (!account.isNull("supportEmail")) {
        this.support_email = account.getString("supportEmail");
      }
      if (!account.isNull("mainColor")) {
        this.main_color = account.getString("mainColor");
      }
      if (!account.isNull("eulaUrl")) {
        this.eula = account.getString("eulaUrl");
      }
      if (!account.isNull("privacyUrl")) {
        this.privacy_policy = account.getString("privacyUrl");
      }
      if (!account.isNull("licenseUrl")) {
        this.content_license = account.getString("licenseUrl");
      }
      if (!account.isNull("authPasscodeLength")) {
        this.pin_length = account.getInt("authPasscodeLength");
      } else {
        this.pin_length = 0;
      }
      if (!account.isNull("authPasscodeAllowsLetters")) {
        this.pin_allows_letters = account.getBoolean("authPasscodeAllowsLetters");
      } else {
        this.pin_allows_letters = true;
      }

    } catch (JSONException e) {
      e.printStackTrace();
    }

  }

}
