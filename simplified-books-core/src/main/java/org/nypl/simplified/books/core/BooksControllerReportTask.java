package org.nypl.simplified.books.core;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.io7m.jfunctional.FunctionType;
import com.io7m.jfunctional.OptionType;
import com.io7m.jfunctional.Some;
import com.io7m.jnull.NullCheck;

import org.nypl.simplified.books.accounts.AccountAuthenticatedHTTP;
import org.nypl.simplified.books.accounts.AccountAuthenticationCredentials;
import org.nypl.simplified.http.core.HTTPAuthType;
import org.nypl.simplified.http.core.HTTPType;
import org.nypl.simplified.json.core.JSONSerializerUtilities;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URI;

/**
 * <p>The logic for reporting a problem with a book.</p>
 */

public class BooksControllerReportTask implements Runnable {

  private static final Logger LOG;

  static {
    LOG = LogUtilities.getLog(BooksControllerReportTask.class);
  }

  private final String report_type;
  private final FeedEntryOPDS feed_entry;
  private final HTTPType http;
  private final AccountsDatabaseReadableType accounts_database;

  BooksControllerReportTask(
      final String in_report_type,
      final FeedEntryOPDS in_feed_entry,
      final HTTPType in_http,
      final AccountsDatabaseReadableType in_accounts_database) {
    this.report_type = NullCheck.notNull(in_report_type);
    this.feed_entry = NullCheck.notNull(in_feed_entry);
    this.http = NullCheck.notNull(in_http);
    this.accounts_database = NullCheck.notNull(in_accounts_database);
  }

  @Override
  public void run() {
    final ObjectNode report = JsonNodeFactory.instance.objectNode();
    report.set("type", JsonNodeFactory.instance.textNode(this.report_type));
    final OptionType<AccountAuthenticationCredentials> credentials_opt =
        this.accounts_database.accountGetCredentials();

    OptionType<HTTPAuthType> http_auth =
        credentials_opt.map(new FunctionType<AccountAuthenticationCredentials, HTTPAuthType>() {
          @Override
          public HTTPAuthType call(AccountAuthenticationCredentials creds) {
            return AccountAuthenticatedHTTP.createAuthenticatedHTTP(creds);
          }
        });

    try {
      final String report_string = JSONSerializerUtilities.serializeToString(report);
      final OptionType<URI> issues_opt = this.feed_entry.getFeedEntry().getIssues();
      if (issues_opt.isSome()) {
        final Some<URI> issues_some = (Some<URI>) issues_opt;
        this.http.post(http_auth, issues_some.get(), report_string.getBytes(), "application/problem+json");
      }
    } catch (IOException e) {
      LOG.warn("Failed to submit problem report.");
    }
  }
}
