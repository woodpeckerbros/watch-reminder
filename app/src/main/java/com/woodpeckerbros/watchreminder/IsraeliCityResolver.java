package com.woodpeckerbros.watchreminder;

public final class IsraeliCityResolver {
    private static final double MAX_APPROXIMATE_DISTANCE_KM = 45.0;

    private static final City[] CITIES = {
            new City("ירושלים", 31.7683, 35.2137),
            new City("תל אביב", 32.0853, 34.7818),
            new City("רמת גן", 32.0684, 34.8248),
            new City("גבעתיים", 32.0719, 34.8106),
            new City("בני ברק", 32.0833, 34.8333),
            new City("פתח תקווה", 32.0840, 34.8878),
            new City("חולון", 32.0158, 34.7874),
            new City("בת ים", 32.0238, 34.7519),
            new City("ראשון לציון", 31.9730, 34.7925),
            new City("רחובות", 31.8928, 34.8113),
            new City("יבנה", 31.8770, 34.7384),
            new City("גן יבנה", 31.7874, 34.7066),
            new City("אשדוד", 31.8014, 34.6435),
            new City("אשקלון", 31.6688, 34.5743),
            new City("קריית גת", 31.6100, 34.7642),
            new City("שדרות", 31.5250, 34.5969),
            new City("נתיבות", 31.4230, 34.5890),
            new City("אופקים", 31.3141, 34.6203),
            new City("באר שבע", 31.2530, 34.7915),
            new City("דימונה", 31.0700, 35.0336),
            new City("ירוחם", 30.9872, 34.9314),
            new City("ערד", 31.2589, 35.2128),
            new City("מצפה רמון", 30.6102, 34.8019),
            new City("אילת", 29.5577, 34.9519),
            new City("בית שמש", 31.7457, 34.9861),
            new City("מודיעין", 31.8969, 35.0063),
            new City("לוד", 31.9510, 34.8953),
            new City("רמלה", 31.9292, 34.8656),
            new City("אלעד", 32.0523, 34.9512),
            new City("מעלה אדומים", 31.7780, 35.2987),
            new City("אריאל", 32.1065, 35.1845),
            new City("הרצליה", 32.1663, 34.8433),
            new City("רעננה", 32.1848, 34.8713),
            new City("כפר סבא", 32.1782, 34.9076),
            new City("נתניה", 32.3215, 34.8532),
            new City("חדרה", 32.4340, 34.9196),
            new City("זכרון יעקב", 32.5712, 34.9514),
            new City("חיפה", 32.7940, 34.9896),
            new City("קריית אתא", 32.8056, 35.1064),
            new City("עכו", 32.9278, 35.0818),
            new City("נהריה", 33.0059, 35.0941),
            new City("כרמיאל", 32.9199, 35.2901),
            new City("נצרת", 32.6996, 35.3035),
            new City("מגדל העמק", 32.6751, 35.2393),
            new City("עפולה", 32.6091, 35.2892),
            new City("בית שאן", 32.4971, 35.4960),
            new City("טבריה", 32.7940, 35.5312),
            new City("צפת", 32.9646, 35.4960),
            new City("כפר שמאי", 32.9569, 35.4571),
            new City("מירון", 32.9870, 35.4403),
            new City("אור הגנוז", 33.0058, 35.4466),
            new City("בר יוחאי", 32.9971, 35.4487),
            new City("קריית שמונה", 33.2073, 35.5708),
            new City("קצרין", 32.9920, 35.6910)
    };

    private IsraeliCityResolver() {
    }

    public static String nearestName(double latitude, double longitude) {
        City nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (City city : CITIES) {
            double distance = distanceKm(latitude, longitude, city.latitude, city.longitude);
            if (distance < nearestDistance) {
                nearest = city;
                nearestDistance = distance;
            }
        }
        return nearest != null && nearestDistance <= MAX_APPROXIMATE_DISTANCE_KM
                ? nearest.name + " (בקירוב)"
                : null;
    }

    private static double distanceKm(double latitude1, double longitude1, double latitude2, double longitude2) {
        double latitudeDelta = Math.toRadians(latitude2 - latitude1);
        double longitudeDelta = Math.toRadians(longitude2 - longitude1);
        double sinLatitude = Math.sin(latitudeDelta / 2.0);
        double sinLongitude = Math.sin(longitudeDelta / 2.0);
        double a = sinLatitude * sinLatitude
                + Math.cos(Math.toRadians(latitude1))
                * Math.cos(Math.toRadians(latitude2))
                * sinLongitude * sinLongitude;
        return 6371.0088 * 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
    }

    private static final class City {
        final String name;
        final double latitude;
        final double longitude;

        City(String name, double latitude, double longitude) {
            this.name = name;
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }
}
