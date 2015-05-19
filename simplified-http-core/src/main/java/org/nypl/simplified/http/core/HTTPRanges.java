package org.nypl.simplified.http.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.io7m.jfunctional.Option;
import com.io7m.jfunctional.OptionType;
import com.io7m.jnull.NullCheck;
import com.io7m.junreachable.UnreachableCodeException;

/**
 * Functions to parse Range requests.
 */

public final class HTTPRanges
{
  private static final Pattern PATTERN_INCLUSIVE;
  private static final Pattern PATTERN_SUFFIX;

  static {
    PATTERN_INCLUSIVE =
      NullCheck.notNull(Pattern.compile("bytes=([0-9]+)-([0-9]*)"));
    PATTERN_SUFFIX = NullCheck.notNull(Pattern.compile("bytes=-([0-9]+)"));
  }

  /**
   * @param text
   *          A range string of the form <tt>bytes=([0-9]*)-([0-9]*)</tt>)
   * @return The parsed byte range
   */

  public static OptionType<HTTPRangeType> fromRangeString(
    final String text)
  {
    final String nn_text = NullCheck.notNull(text);

    try {
      {
        final Matcher matcher = HTTPRanges.PATTERN_SUFFIX.matcher(nn_text);
        if (matcher.matches()) {
          final String end = matcher.group(1);
          final long end_val = Long.valueOf(end).longValue();

          final HTTPByteRangeSuffix rbs = new HTTPByteRangeSuffix(end_val);
          return Option.some((HTTPRangeType) rbs);
        }
      }

      {
        final Matcher matcher = HTTPRanges.PATTERN_INCLUSIVE.matcher(nn_text);
        if (matcher.matches()) {
          final String start = matcher.group(1);
          final String end = matcher.group(2);

          final long start_val = Long.valueOf(start).longValue();
          final long end_val;
          if (end.isEmpty() == false) {
            end_val = Long.valueOf(end).longValue();
          } else {
            end_val = -1;
          }

          final HTTPByteRangeInclusive rbi =
            new HTTPByteRangeInclusive(start_val, end_val);
          return Option.some((HTTPRangeType) rbi);
        }
      }

      return Option.none();
    } catch (final NumberFormatException e) {
      return Option.none();
    }
  }

  private HTTPRanges()
  {
    throw new UnreachableCodeException();
  }
}