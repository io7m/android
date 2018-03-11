package org.nypl.simplified.books.analytics;

import com.io7m.jnull.NullCheck;

import org.nypl.simplified.books.core.LogUtilities;
import org.slf4j.Logger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;

/**
 * Created by Skullbonez on 3/11/2018.
 */

public class AnalyticsLogger {

  private static final Logger LOG = LogUtilities.getLog(AnalyticsLogger.class);

  private final String log_file_name = "analytics_log.txt";
  private final int log_file_size_limit =  1024 * 1024 * 10;
  private BufferedWriter analytics_output = null;

  private AnalyticsLogger(
      File directory_analytics)
  {
    NullCheck.notNull(directory_analytics, "assets");
    try {
      File log_file = new File(directory_analytics, log_file_name);
      // Stop logging after 10MB (future releases will transmit then delete this file)
      if (log_file.length() < log_file_size_limit) {
        FileWriter logWriter = new FileWriter(log_file, true);
        analytics_output = new BufferedWriter(logWriter);
      }
    } catch (Exception e) {
      LOG.debug("Ignoring exception: AnalyticsLogger constructor raised: ", e);
    }
  }

  public static AnalyticsLogger create(
      final File directory_analytics)
  {
    return new AnalyticsLogger(directory_analytics);
  }

  public void logToAnalytics(String message) {
    if ( analytics_output != null ) {
      try {
        String date_str = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss,").format(new Date());
        analytics_output.write(date_str + message + "\n");
        analytics_output.close();
      } catch (Exception e) {
        LOG.debug("Ignoring exception: logToAnalytics raised: ", e);
      }
    }
  }
}
