package com.gathin.tvhome;

/** Prefer known local device coordinates; a cached IP estimate cannot mask a system location. */
final class WeatherAutoPolicy {
    static boolean validDeviceCoordinates(double latitude, double longitude) {
        return Double.isFinite(latitude) && Double.isFinite(longitude) && latitude >= -90 && latitude <= 90
                && longitude >= -180 && longitude <= 180 && !(latitude == 0 && longitude == 0);
    }
    static boolean useCache(boolean hasCity, String cachedSource, String desiredSource,
                            String cachedDevice, String desiredDevice, long age) {
        return hasCity && age >= 0 && age < 6 * 60 * 60 * 1000L && desiredSource.equals(cachedSource)
                && desiredDevice.equals(cachedDevice);
    }
}
