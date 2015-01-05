package com.anjuke.dw.explorer.util

import java.util.{Calendar, Date}
import java.text.SimpleDateFormat

object DwDate {

  def dealDate: String = {
    val cal = Calendar.getInstance
    cal.add(Calendar.DATE, -1)
    new SimpleDateFormat("yyyy-MM-dd").format(cal.getTime)
  }

  def monthId: String = {
    val dt = dealDate
    dt.substring(0, 4) + "M" + dt.substring(5, 7)
  }

  def monthBegin: String = {
    val dt = dealDate
    dt.substring(0, 8) + "01"
  }

  def monthEnd: String = {
    val (dt, df, cal) = prepare
    cal.setTime(df.parse(dt))
    cal.set(Calendar.DATE, cal.getActualMaximum(Calendar.DATE))
    df.format(cal.getTime)
  }

  def weekId: String = {
    val (dt, df, cal) = prepare
    val saturday = getSaturday(cal)
    val week = if (saturday.get(Calendar.YEAR) > cal.get(Calendar.YEAR)) {
      cal.add(Calendar.DATE, -7)
      cal.get(Calendar.WEEK_OF_YEAR) + 1
    } else {
      cal.get(Calendar.WEEK_OF_YEAR)
    }
    "%sW%02d".format(dt.substring(0, 4), week)
  }

  def weekBegin: String = {
    val (dt, df, cal) = prepare
    val sunday = getSunday(cal)
    if (sunday.get(Calendar.YEAR) < cal.get(Calendar.YEAR)) {
      dt.substring(0, 5) + "01-01"
    } else {
      df.format(sunday.getTime)
    }
  }

  def weekEnd: String = {
    val (dt, df, cal) = prepare
    val saturday = getSaturday(cal)
    if (saturday.get(Calendar.YEAR) > cal.get(Calendar.YEAR)) {
      dt.substring(0, 5) + "12-31"
    } else {
      df.format(saturday.getTime)
    }
  }

  def nDaysAgo(n: Int): String = {
    val (dt, df, cal) = prepare
    cal.add(Calendar.DATE, -n)
    df.format(cal.getTime)
  }

  private def prepare(): Tuple3[String, SimpleDateFormat, Calendar] = {
    val dt = dealDate
    val df = new SimpleDateFormat("yyyy-MM-dd")
    val cal = Calendar.getInstance
    cal.setTime(df.parse(dt))
    (dt, df, cal)
  }

  private def getSunday(cal: Calendar): Calendar = {
    val sunday = Calendar.getInstance
    sunday.setTime(cal.getTime)
    sunday.add(Calendar.DATE, 1 - sunday.get(Calendar.DAY_OF_WEEK))
    sunday
  }

  private def getSaturday(cal: Calendar): Calendar = {
    val saturday = Calendar.getInstance
    saturday.setTime(cal.getTime)
    saturday.add(Calendar.DATE, 7 - saturday.get(Calendar.DAY_OF_WEEK))
    saturday
  }

}
