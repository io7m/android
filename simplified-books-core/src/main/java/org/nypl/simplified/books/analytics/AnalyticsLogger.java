package org.nypl.simplified.books.analytics;

import com.io7m.jnull.NullCheck;

import org.nypl.simplified.books.core.LogUtilities;
import org.slf4j.Logger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by Skullbonez on 3/11/2018.
 *
 * This logger is to be AS FAILSAFE AS POSSIBLE.
 * Silent failures are allowed here.  We want this
 * to be a "best effort" logger - it is not to
 * crash the app!
 *
 */

public class AnalyticsLogger {

  private static final Logger LOG = LogUtilities.getLog(AnalyticsLogger.class);

  private final String log_file_name = "analytics_log.txt";
  private final int log_file_size_limit =  1024 * 1024 * 10;
  private BufferedWriter analytics_output = null;
  private File directory_analytics = null;
  private boolean log_size_limit_reached = false;

  private AnalyticsLogger(
      File in_directory_analytics)
  {
    directory_analytics = NullCheck.notNull(in_directory_analytics, "analytics");
    init();
  }

  public static AnalyticsLogger create(
      final File directory_analytics)
  {
    return new AnalyticsLogger(directory_analytics);
  }

  private void init() {
    if ( log_size_limit_reached ) {
      // Don't bother trying to re-init if the log is full.
      return;
    }
    try {
      File log_file = new File(directory_analytics, log_file_name);
      // Stop logging after 10MB (future releases will transmit then delete this file)
      if (log_file.length() < log_file_size_limit) {
        FileWriter logWriter = new FileWriter(log_file, true);
        analytics_output = new BufferedWriter(logWriter);
      } else {
        log_size_limit_reached = true;
      }
    } catch (Exception e) {
      LOG.debug("Ignoring exception: init raised: ", e);
    }
  }

  public void logToAnalytics(String message) {
    if ( analytics_output == null ) {
      init();
    }
    if ( analytics_output != null ) {
      try {
        String date_str = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss,").format(new Date());
        analytics_output.write(date_str + message + "\n");
        analytics_output.flush();  // Make small synchronous additions for now
      } catch (Exception e) {
        LOG.debug("Ignoring exception: logToAnalytics raised: ", e);
      }
    }
  }
}
