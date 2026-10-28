package com.example.floodguard

/**
 * Shared state manager between MapActivity (Road Map) and DashboardActivity (Dashboard).
 * Synchronizes real-time device location, active navigation route, detour hazard info,
 * and destination pins across both screens.
 */
object MapStateManager {
    // Active navigation route state
    var isRouteActive: Boolean = false
    var destLat: Double? = null
    var destLon: Double? = null
    var destTitle: String? = null
    var hazardAvoided: Boolean = false
    var hazardSummary: String? = null
    var distanceKm: Double = 0.0
    var durationMin: Double = 0.0

    // Latest user GPS location
    var userLat: Double = 14.6958
    var userLon: Double = 121.1207
    var userLocationName: String = "San Mateo Town Center"
    var isLiveGps: Boolean = false

    // Clear active route
    fun clearRoute() {
        isRouteActive = false
        destLat = null
        destLon = null
        destTitle = null
        hazardAvoided = false
        hazardSummary = null
        distanceKm = 0.0
        durationMin = 0.0
    }

    // Set active route
    fun setActiveRoute(
        destLat: Double,
        destLon: Double,
        destTitle: String,
        hazardAvoided: Boolean,
        hazardSummary: String,
        distanceKm: Double,
        durationMin: Double
    ) {
        this.isRouteActive = true
        this.destLat = destLat
        this.destLon = destLon
        this.destTitle = destTitle
        this.hazardAvoided = hazardAvoided
        this.hazardSummary = hazardSummary
        this.distanceKm = distanceKm
        this.durationMin = durationMin
    }

    // Update user location
    fun updateUserLocation(lat: Double, lon: Double, name: String, isLive: Boolean) {
        this.userLat = lat
        this.userLon = lon
        this.userLocationName = name
        this.isLiveGps = isLive
    }
}
