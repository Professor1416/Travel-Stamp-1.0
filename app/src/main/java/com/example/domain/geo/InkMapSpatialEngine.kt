package com.example.domain.geo

import java.util.LinkedList
import java.util.Queue
import kotlin.math.sqrt

data class InkMapPoint(
    val id: String,
    val normalizedX: Double,
    val normalizedY: Double
)

data class InkMapViewport(
    val width: Double,
    val height: Double,
    val zoom: Double,
    val panX: Double,
    val panY: Double
)

data class InkScreenPoint(
    val id: String,
    val x: Double,
    val y: Double
)

sealed class InkClusterResult {
    data class Individual(
        val point: InkScreenPoint
    ) : InkClusterResult()

    data class Cluster(
        val id: String,
        val points: List<InkScreenPoint>,
        val center: InkScreenPoint
    ) : InkClusterResult()
}

object InkMapSpatialEngine {

    /**
     * Converts normalized map coordinates to screen coordinates.
     * Coordinate convention:
     * - Normalized map coordinate 0,0 is top-left, 1,1 is bottom-right.
     * - aspect-ratio-safe base fit scales and centers the map within the viewport.
     * - Zoom scales up/down everything from the center of the viewport.
     * - Pan translates the viewport in screen pixels.
     *
     * Returns null if any input coordinate, viewport dimension, scale, or aspect is invalid or non-finite.
     */
    fun transform(
        point: InkMapPoint,
        viewport: InkMapViewport,
        mapAspect: Double
    ): InkScreenPoint? {
        if (viewport.width <= 0.0 || !viewport.width.isFinite() ||
            viewport.height <= 0.0 || !viewport.height.isFinite() ||
            viewport.zoom <= 0.0 || !viewport.zoom.isFinite() ||
            !viewport.panX.isFinite() || !viewport.panY.isFinite() ||
            !point.normalizedX.isFinite() || !point.normalizedY.isFinite() ||
            mapAspect <= 0.0 || !mapAspect.isFinite()
        ) {
            return null
        }

        val viewportAspect = viewport.width / viewport.height

        val baseScale: Double
        val offsetX: Double
        val offsetY: Double

        if (viewportAspect > mapAspect) {
            // Viewport is wider than the map -> Letterbox horizontally (padding on left/right)
            // Map height fits viewport height (normalized Y: 0.0..1.0 maps to 0.0..viewport.height)
            baseScale = viewport.height
            offsetX = (viewport.width - mapAspect * baseScale) / 2.0
            offsetY = 0.0
        } else {
            // Viewport is taller than the map -> Letterbox vertically (padding on top/bottom)
            // Map width fits viewport width (normalized X: 0.0..1.0 maps to 0.0..viewport.width)
            baseScale = viewport.width / mapAspect
            offsetX = 0.0
            offsetY = (viewport.height - baseScale) / 2.0
        }

        // Base screen coordinates before zoom and pan
        val baseX = offsetX + point.normalizedX * (baseScale * mapAspect)
        val baseY = offsetY + point.normalizedY * baseScale

        // Apply zoom with respect to the viewport center
        val centerX = viewport.width / 2.0
        val centerY = viewport.height / 2.0

        val screenX = (baseX - centerX) * viewport.zoom + centerX + viewport.panX
        val screenY = (baseY - centerY) * viewport.zoom + centerY + viewport.panY

        return InkScreenPoint(point.id, screenX, screenY)
    }

    /**
     * Groups screen-space points using a deterministic connected-components style clustering algorithm.
     * Points are connected if their Euclidean distance is <= radius.
     *
     * Returns null if:
     * - radius < 0.0 or non-finite
     * - any point coordinates are non-finite
     * - duplicate point IDs exist in the input
     */
    fun cluster(
        points: List<InkScreenPoint>,
        radius: Double
    ): List<InkClusterResult>? {
        if (radius < 0.0 || !radius.isFinite()) {
            return null
        }

        // Check for duplicate IDs or non-finite coordinates
        val seenIds = mutableSetOf<String>()
        for (pt in points) {
            if (!pt.x.isFinite() || !pt.y.isFinite()) {
                return null
            }
            if (!seenIds.add(pt.id)) {
                return null // Explicit failure for duplicate ID
            }
        }

        if (points.isEmpty()) {
            return emptyList()
        }

        // Stable sorting by ID guarantees input order independence and deterministic results
        val sortedPoints = points.sortedBy { it.id }
        val n = sortedPoints.size

        // Build adjacency matrix/list for Euclidean distance <= radius
        val adj = List(n) { mutableListOf<Int>() }
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                val p1 = sortedPoints[i]
                val p2 = sortedPoints[j]
                val dx = p1.x - p2.x
                val dy = p1.y - p2.y
                val dist = sqrt(dx * dx + dy * dy)
                if (dist <= radius) {
                    adj[i].add(j)
                    adj[j].add(i)
                }
            }
        }

        val visited = BooleanArray(n) { false }
        val results = mutableListOf<InkClusterResult>()

        for (i in 0 until n) {
            if (visited[i]) continue

            // Traverse the connected component using BFS
            val componentIndices = mutableListOf<Int>()
            val queue: Queue<Int> = LinkedList()
            queue.add(i)
            visited[i] = true

            while (queue.isNotEmpty()) {
                val curr = queue.poll()!!
                componentIndices.add(curr)
                for (neighbor in adj[curr]) {
                    if (!visited[neighbor]) {
                        visited[neighbor] = true
                        queue.add(neighbor)
                    }
                }
            }

            // Since the points are processed via sortedPoints, they are already in deterministic order of ID
            val componentPoints = componentIndices.sorted().map { sortedPoints[it] }

            if (componentPoints.size == 1) {
                results.add(InkClusterResult.Individual(componentPoints[0]))
            } else {
                val sumX = componentPoints.sumOf { it.x }
                val sumY = componentPoints.sumOf { it.y }
                val avgX = sumX / componentPoints.size
                val avgY = sumY / componentPoints.size

                val clusterId = "cluster_" + componentPoints.joinToString("_") { it.id }
                val center = InkScreenPoint(clusterId, avgX, avgY)

                results.add(InkClusterResult.Cluster(clusterId, componentPoints, center))
            }
        }

        // Deterministic sorting of final outputs based on the ID of the first point in each result
        return results.sortedBy { result ->
            when (result) {
                is InkClusterResult.Individual -> result.point.id
                is InkClusterResult.Cluster -> result.points.first().id
            }
        }
    }

    /**
     * Determines if a point is visible within the viewport boundaries plus an optional margin.
     * Returns false for any invalid, negative, or non-finite parameter values.
     */
    fun isVisible(
        p: InkScreenPoint,
        viewport: InkMapViewport,
        margin: Double = 0.0
    ): Boolean {
        if (!p.x.isFinite() || !p.y.isFinite() ||
            !viewport.width.isFinite() || !viewport.height.isFinite() ||
            viewport.width <= 0.0 || viewport.height <= 0.0 ||
            !margin.isFinite() || margin < 0.0
        ) {
            return false
        }
        return p.x in -margin..(viewport.width + margin) &&
                p.y in -margin..(viewport.height + margin)
    }
}
