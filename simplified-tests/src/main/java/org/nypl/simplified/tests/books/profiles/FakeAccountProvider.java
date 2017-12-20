package org.nypl.simplified.tests.books.profiles;

import com.io7m.jfunctional.Option;
import com.io7m.jfunctional.OptionType;

import org.nypl.simplified.books.accounts.AccountProviderAuthenticationDescriptionType;
import org.nypl.simplified.books.accounts.AccountProviderType;

import java.net.URI;

public final class FakeAccountProvider implements AccountProviderType {
  private final URI uri;

  public FakeAccountProvider(String in_uri) {
    this.uri = URI.create(in_uri);
  }

  @Override
  public URI id() {
    return uri;
  }

  @Override
  public String displayName() {
    return "Fake Library";
  }

  @Override
  public String subtitle() {
    return "The finest imaginary books.";
  }

  @Override
  public URI logo() {
    return URI.create("http://example.com/logo.png");
  }

  @Override
  public OptionType<AccountProviderAuthenticationDescriptionType> authentication() {
    return Option.none();
  }

  @Override
  public boolean supportsSimplyESynchronization() {
    return false;
  }

  @Override
  public boolean supportsBarcodeScanner() {
    return false;
  }

  @Override
  public boolean supportsBarcodeDisplay() {
    return false;
  }

  @Override
  public boolean supportsReservations() {
    return false;
  }

  @Override
  public boolean supportsCardCreator() {
    return false;
  }

  @Override
  public boolean supportsHelpCenter() {
    return false;
  }

  @Override
  public URI catalogURI() {
    return URI.create("http://example.com/catalog.xml");
  }

  @Override
  public OptionType<URI> catalogURIForOver13s() {
    return Option.none();
  }

  @Override
  public OptionType<URI> catalogURIForUnder13s() {
    return Option.none();
  }

  @Override
  public String supportEmail() {
    return "support@example.com";
  }

  @Override
  public OptionType<URI> eula() {
    return Option.none();
  }

  @Override
  public OptionType<URI> license() {
    return Option.none();
  }

  @Override
  public OptionType<URI> privacyPolicy() {
    return Option.none();
  }

  @Override
  public String mainColor() {
    return "#da2527";
  }
}
