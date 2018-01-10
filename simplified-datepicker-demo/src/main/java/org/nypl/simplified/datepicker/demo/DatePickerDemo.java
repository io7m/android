package org.nypl.simplified.datepicker.demo;

import android.app.Activity;
import android.os.Bundle;

import com.io7m.jnull.NullCheck;

import org.nypl.simplified.datepicker.DatePicker;

public final class DatePickerDemo extends Activity {

  private DatePicker picker;

  @Override
  protected void onCreate(final Bundle state) {
    super.onCreate(state);

    this.setContentView(R.layout.date_picker_demo);

    this.picker =
        NullCheck.notNull(this.findViewById(R.id.date_picker), "R.id.date_picker");
  }
}
