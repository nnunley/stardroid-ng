package com.stardroid.awakening.ephemeris

import java.util.Calendar
import java.util.TimeZone

/**
 * Julian Date calculations for astronomical computations.
 *
 * Julian Date (JD) is a continuous count of days since the beginning of the Julian Period
 * (January 1, 4713 BC). It's the standard time system used in astronomy.
 */
object JulianDate {

    /**
     * Convert calendar date/time to Julian Date.
     *
     * @param year Year (e.g., 2024)
     * @param month Month (1-12)
     * @param day Day of month (1-31)
     * @param hour Hour (0-23)
     * @param minute Minute (0-59)
     * @param second Second (0-59)
     * @return Julian Date
     */
    fun fromCalendar(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 0,
        minute: Int = 0,
        second: Int = 0
    ): Double {
        // Algorithm from Meeus, "Astronomical Algorithms", Chapter 7
        var y = year
        var m = month

        if (m <= 2) {
            y -= 1
            m += 12
        }

        val a = y / 100
        val b = 2 - a + a / 4

        val dayFraction = (hour + minute / 60.0 + second / 3600.0) / 24.0

        return (365.25 * (y + 4716)).toInt() +
                (30.6001 * (m + 1)).toInt() +
                day + dayFraction + b - 1524.5
    }

    /**
     * Get current Julian Date in UTC.
     */
    fun now(): Double {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        return fromCalendar(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,  // Calendar months are 0-based
            cal.get(Calendar.DAY_OF_MONTH),
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            cal.get(Calendar.SECOND)
        )
    }

    /**
     * Get Julian Date from Unix timestamp (milliseconds).
     */
    fun fromUnixMillis(millis: Long): Double {
        // Unix epoch is JD 2440587.5 (January 1, 1970, 00:00:00 UTC)
        return 2440587.5 + millis / 86400000.0
    }

    /**
     * Convert Julian Date to Julian centuries since J2000.0.
     *
     * J2000.0 is January 1, 2000, 12:00:00 TT (JD 2451545.0)
     * This is the standard epoch for modern astronomical calculations.
     */
    fun toJulianCenturies(jd: Double): Double {
        return (jd - 2451545.0) / 36525.0
    }

    /**
     * Get Julian centuries since J2000.0 for current time.
     */
    fun centuriesNow(): Double {
        return toJulianCenturies(now())
    }
}
