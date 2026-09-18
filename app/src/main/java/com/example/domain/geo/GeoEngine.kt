package com.example.domain.geo

import kotlin.math.abs
import kotlin.math.cos

data class GeoPoint(
    val latitude: Double,
    val longitude: Double
) {
    fun isValid(): Boolean {
        return latitude.isFinite() &&
                longitude.isFinite() &&
                latitude in -90.0..90.0 &&
                longitude in -180.0..180.0
    }
}

data class GeoPolygon(
    val outerRing: List<GeoPoint>,
    val holes: List<List<GeoPoint>> = emptyList()
)

data class GeoMultiPolygon(
    val polygons: List<GeoPolygon>
)

enum class GeoContainment {
    INSIDE,
    OUTSIDE
}

data class ProjectionConfig(
    val centerLatitude: Double,
    val centerLongitude: Double
)

data class ProjectedPoint(
    val x: Double,
    val y: Double
)

data class ProjectedBounds(
    val minX: Double,
    val minY: Double,
    val maxX: Double,
    val maxY: Double
)

data class NormalizedPoint(
    val x: Double,
    val y: Double
)

object GeoEngine {

    private const val EPSILON = 1e-9

    /**
     * Helper to retrieve all segments of a ring, implicitly closing it if needed.
     */
    fun getRingSegments(ring: List<GeoPoint>): List<Pair<GeoPoint, GeoPoint>> {
        if (ring.size < 3) return emptyList()
        val segments = mutableListOf<Pair<GeoPoint, GeoPoint>>()
        for (i in 0 until ring.size - 1) {
            segments.add(ring[i] to ring[i + 1])
        }
        val first = ring.first()
        val last = ring.last()
        if (first.latitude != last.latitude || first.longitude != last.longitude) {
            segments.add(last to first)
        }
        return segments
    }

    /**
     * Checks if a point lies exactly on a segment AB within a small epsilon.
     */
    fun isOnSegment(p: GeoPoint, a: GeoPoint, b: GeoPoint): Boolean {
        // Cross product to check collinearity: (P.x - A.x)*(B.y - A.y) - (P.y - A.y)*(B.x - A.x)
        val crossProduct = (p.longitude - a.longitude) * (b.latitude - a.latitude) - (p.latitude - a.latitude) * (b.longitude - a.longitude)
        if (abs(crossProduct) > EPSILON) {
            return false
        }
        // Check bounding box
        val minX = minOf(a.longitude, b.longitude) - EPSILON
        val maxX = maxOf(a.longitude, b.longitude) + EPSILON
        val minY = minOf(a.latitude, b.latitude) - EPSILON
        val maxY = maxOf(a.latitude, b.latitude) + EPSILON
        return p.longitude in minX..maxX && p.latitude in minY..maxY
    }

    /**
     * Checks if a point lies on the boundary (any segment) of a ring.
     */
    fun isOnBoundary(p: GeoPoint, ring: List<GeoPoint>): Boolean {
        val segments = getRingSegments(ring)
        for (segment in segments) {
            if (isOnSegment(p, segment.first, segment.second)) {
                return true
            }
        }
        return false
    }

    /**
     * Basic ray-casting algorithm to determine if a point is inside a closed ring.
     */
    fun isPointInRing(p: GeoPoint, ring: List<GeoPoint>): Boolean {
        val segments = getRingSegments(ring)
        if (segments.isEmpty()) return false

        var inside = false
        val py = p.latitude
        val px = p.longitude

        for (segment in segments) {
            val a = segment.first
            val b = segment.second

            val ay = a.latitude
            val ax = a.longitude
            val by = b.latitude
            val bx = b.longitude

            if ((ay > py) != (by > py)) {
                val intersectX = ax + (py - ay) * (bx - ax) / (by - ay)
                if (px < intersectX) {
                    inside = !inside
                }
            }
        }
        return inside
    }

    /**
     * Classifies a point against a single GeoPolygon.
     */
    fun classifyPolygon(p: GeoPoint, polygon: GeoPolygon): GeoContainment {
        // 1. Check outer boundary
        if (isOnBoundary(p, polygon.outerRing)) {
            return GeoContainment.INSIDE
        }

        // 2. Check hole boundaries
        for (hole in polygon.holes) {
            if (isOnBoundary(p, hole)) {
                return GeoContainment.INSIDE
            }
        }

        // 3. Ray-cast outer ring
        if (!isPointInRing(p, polygon.outerRing)) {
            return GeoContainment.OUTSIDE
        }

        // 4. Ray-cast holes
        for (hole in polygon.holes) {
            if (isPointInRing(p, hole)) {
                return GeoContainment.OUTSIDE
            }
        }

        return GeoContainment.INSIDE
    }

    /**
     * Classifies a point against a GeoMultiPolygon.
     */
    fun classify(p: GeoPoint, multiPolygon: GeoMultiPolygon): GeoContainment {
        if (!p.isValid()) {
            return GeoContainment.OUTSIDE
        }
        for (polygon in multiPolygon.polygons) {
            if (classifyPolygon(p, polygon) == GeoContainment.INSIDE) {
                return GeoContainment.INSIDE
            }
        }
        return GeoContainment.OUTSIDE
    }

    /**
     * Local projection from WGS84 coordinates to local flat coordinates.
     */
    fun project(p: GeoPoint, config: ProjectionConfig): ProjectedPoint {
        val latRad = Math.toRadians(config.centerLatitude)
        val x = (p.longitude - config.centerLongitude) * cos(latRad)
        val y = p.latitude - config.centerLatitude
        return ProjectedPoint(x, y)
    }

    /**
     * Normalizes a projected coordinate into 0.0..1.0 range based on explicit bounds.
     */
    fun normalize(pt: ProjectedPoint, bounds: ProjectedBounds): NormalizedPoint {
        val rangeX = bounds.maxX - bounds.minX
        val rangeY = bounds.maxY - bounds.minY

        if (rangeX <= 0.0 || rangeY <= 0.0) {
            return NormalizedPoint(0.0, 0.0)
        }

        val xNorm = (pt.x - bounds.minX) / rangeX
        val yNorm = (pt.y - bounds.minY) / rangeY

        return NormalizedPoint(xNorm, yNorm)
    }
}
