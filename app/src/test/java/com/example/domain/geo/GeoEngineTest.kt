package com.example.domain.geo

import org.junit.Assert.*
import org.junit.Test

class GeoEngineTest {

    // ==========================================
    // VALIDATION TESTS
    // ==========================================

    @Test
    fun testValidNormalCoordinate() {
        // 1. valid normal coordinate
        val point = GeoPoint(15.0, 75.0)
        assertTrue(point.isValid())
    }

    @Test
    fun testLatitudeBoundaries() {
        // 2. latitude -90 and +90
        assertTrue(GeoPoint(-90.0, 0.0).isValid())
        assertTrue(GeoPoint(90.0, 0.0).isValid())
    }

    @Test
    fun testLongitudeBoundaries() {
        // 3. longitude -180 and +180
        assertTrue(GeoPoint(0.0, -180.0).isValid())
        assertTrue(GeoPoint(0.0, 180.0).isValid())
    }

    @Test
    fun testLatitudeOutsideRangeRejected() {
        // 4. latitude outside range rejected
        assertFalse(GeoPoint(-90.1, 0.0).isValid())
        assertFalse(GeoPoint(90.1, 0.0).isValid())
    }

    @Test
    fun testLongitudeOutsideRangeRejected() {
        // 5. longitude outside range rejected
        assertFalse(GeoPoint(0.0, -180.1).isValid())
        assertFalse(GeoPoint(0.0, 180.1).isValid())
    }

    @Test
    fun testNaNRejected() {
        // 6. NaN rejected
        assertFalse(GeoPoint(Double.NaN, 0.0).isValid())
        assertFalse(GeoPoint(0.0, Double.NaN).isValid())
    }

    @Test
    fun testInfinityRejected() {
        // 7. Infinity rejected
        assertFalse(GeoPoint(Double.NEGATIVE_INFINITY, 0.0).isValid())
        assertFalse(GeoPoint(Double.POSITIVE_INFINITY, 0.0).isValid())
        assertFalse(GeoPoint(0.0, Double.NEGATIVE_INFINITY).isValid())
        assertFalse(GeoPoint(0.0, Double.POSITIVE_INFINITY).isValid())
    }


    // ==========================================
    // SIMPLE POLYGON TESTS
    // ==========================================

    private val squarePolygon = GeoPolygon(
        outerRing = listOf(
            GeoPoint(0.0, 0.0),
            GeoPoint(0.0, 10.0),
            GeoPoint(10.0, 10.0),
            GeoPoint(10.0, 0.0)
        )
    )

    @Test
    fun testPointClearlyInside() {
        // 8. point clearly inside
        val p = GeoPoint(5.0, 5.0)
        assertEquals(GeoContainment.INSIDE, GeoEngine.classifyPolygon(p, squarePolygon))
    }

    @Test
    fun testPointClearlyOutside() {
        // 9. point clearly outside
        val p = GeoPoint(15.0, 5.0)
        assertEquals(GeoContainment.OUTSIDE, GeoEngine.classifyPolygon(p, squarePolygon))
    }

    @Test
    fun testPointOnHorizontalEdge() {
        // 10. point on horizontal edge -> INSIDE
        val p = GeoPoint(0.0, 5.0)
        assertEquals(GeoContainment.INSIDE, GeoEngine.classifyPolygon(p, squarePolygon))
    }

    @Test
    fun testPointOnVerticalEdge() {
        // 11. point on vertical edge -> INSIDE
        val p = GeoPoint(5.0, 10.0)
        assertEquals(GeoContainment.INSIDE, GeoEngine.classifyPolygon(p, squarePolygon))
    }

    @Test
    fun testPointExactlyOnVertex() {
        // 12. point exactly on vertex -> INSIDE
        val p = GeoPoint(10.0, 10.0)
        assertEquals(GeoContainment.INSIDE, GeoEngine.classifyPolygon(p, squarePolygon))
    }

    @Test
    fun testPolygonClosingSegmentHandledCorrectly() {
        // 13. polygon closing segment handled correctly
        // (10, 0) to (0, 0) is the closing segment
        val p = GeoPoint(5.0, 0.0)
        assertEquals(GeoContainment.INSIDE, GeoEngine.classifyPolygon(p, squarePolygon))
    }


    // ==========================================
    // HOLES TESTS
    // ==========================================

    private val polygonWithHole = GeoPolygon(
        outerRing = listOf(
            GeoPoint(0.0, 0.0),
            GeoPoint(0.0, 10.0),
            GeoPoint(10.0, 10.0),
            GeoPoint(10.0, 0.0)
        ),
        holes = listOf(
            listOf(
                GeoPoint(2.0, 2.0),
                GeoPoint(2.0, 8.0),
                GeoPoint(8.0, 8.0),
                GeoPoint(8.0, 2.0)
            )
        )
    )

    @Test
    fun testPointInsideOuterAndOutsideHole() {
        // 14. point inside outer and outside hole -> INSIDE
        val p = GeoPoint(1.0, 1.0)
        assertEquals(GeoContainment.INSIDE, GeoEngine.classifyPolygon(p, polygonWithHole))
    }

    @Test
    fun testPointStrictlyInsideHole() {
        // 15. point strictly inside hole -> OUTSIDE
        val p = GeoPoint(5.0, 5.0)
        assertEquals(GeoContainment.OUTSIDE, GeoEngine.classifyPolygon(p, polygonWithHole))
    }

    @Test
    fun testPointOnHoleEdge() {
        // 16. point on hole edge -> INSIDE
        val p = GeoPoint(2.0, 5.0)
        assertEquals(GeoContainment.INSIDE, GeoEngine.classifyPolygon(p, polygonWithHole))
    }

    @Test
    fun testPointOnHoleVertex() {
        // 17. point on hole vertex -> INSIDE
        val p = GeoPoint(8.0, 8.0)
        assertEquals(GeoContainment.INSIDE, GeoEngine.classifyPolygon(p, polygonWithHole))
    }


    // ==========================================
    // MULTIPOLYGON TESTS
    // ==========================================

    private val multiPolygon = GeoMultiPolygon(
        polygons = listOf(
            GeoPolygon(
                outerRing = listOf(
                    GeoPoint(0.0, 0.0),
                    GeoPoint(0.0, 2.0),
                    GeoPoint(2.0, 2.0),
                    GeoPoint(2.0, 0.0)
                )
            ),
            GeoPolygon(
                outerRing = listOf(
                    GeoPoint(5.0, 5.0),
                    GeoPoint(5.0, 7.0),
                    GeoPoint(7.0, 7.0),
                    GeoPoint(7.0, 5.0)
                )
            )
        )
    )

    @Test
    fun testPointInPolygonA() {
        // 18. point in polygon A -> INSIDE
        val p = GeoPoint(1.0, 1.0)
        assertEquals(GeoContainment.INSIDE, GeoEngine.classify(p, multiPolygon))
    }

    @Test
    fun testPointInPolygonB() {
        // 19. point in polygon B -> INSIDE
        val p = GeoPoint(6.0, 6.0)
        assertEquals(GeoContainment.INSIDE, GeoEngine.classify(p, multiPolygon))
    }

    @Test
    fun testPointInNeither() {
        // 20. point in neither -> OUTSIDE
        val p = GeoPoint(3.0, 3.0)
        assertEquals(GeoContainment.OUTSIDE, GeoEngine.classify(p, multiPolygon))
    }


    // ==========================================
    // PROJECTION TESTS
    // ==========================================

    private val projConfig = ProjectionConfig(
        centerLatitude = 20.0,
        centerLongitude = 75.0
    )

    @Test
    fun testProjectionCenter() {
        // 21. projection center -> x=0, y=0
        val p = GeoPoint(20.0, 75.0)
        val proj = GeoEngine.project(p, projConfig)
        assertEquals(0.0, proj.x, 1e-9)
        assertEquals(0.0, proj.y, 1e-9)
    }

    @Test
    fun testEastWestLongitudeDirection() {
        // 22. east/west longitude direction correct
        val east = GeoPoint(20.0, 76.0)
        val west = GeoPoint(20.0, 74.0)
        val projEast = GeoEngine.project(east, projConfig)
        val projWest = GeoEngine.project(west, projConfig)

        assertTrue(projEast.x > 0.0)
        assertTrue(projWest.x < 0.0)
    }

    @Test
    fun testNorthSouthLatitudeDirection() {
        // 23. north/south latitude direction correct
        val north = GeoPoint(21.0, 75.0)
        val south = GeoPoint(19.0, 75.0)
        val projNorth = GeoEngine.project(north, projConfig)
        val projSouth = GeoEngine.project(south, projConfig)

        assertTrue(projNorth.y > 0.0)
        assertTrue(projSouth.y < 0.0)
    }

    @Test
    fun testCenterLatitudeCosineUsesRadians() {
        // 24. center latitude cosine uses radians
        // Lat = 20 degrees. Cosine(20 deg) is ~0.93969262.
        // If it incorrectly used degrees, cos(20) would be ~0.408 (which is cos of 20 radians).
        val p = GeoPoint(20.0, 76.0)
        val proj = GeoEngine.project(p, projConfig)
        // expected x = (76 - 75) * cos(Math.toRadians(20)) = 1 * cos(0.349) = ~0.93969262
        assertEquals(kotlin.math.cos(Math.toRadians(20.0)), proj.x, 1e-9)
    }

    @Test
    fun testProjectionDeterministic() {
        // 25. projection deterministic for same input
        val p = GeoPoint(21.5, 78.3)
        val proj1 = GeoEngine.project(p, projConfig)
        val proj2 = GeoEngine.project(p, projConfig)
        assertEquals(proj1.x, proj2.x, 1e-15)
        assertEquals(proj1.y, proj2.y, 1e-15)
    }


    // ==========================================
    // NORMALIZATION TESTS
    // ==========================================

    private val bounds = ProjectedBounds(
        minX = -5.0,
        minY = -5.0,
        maxX = 5.0,
        maxY = 5.0
    )

    @Test
    fun testProjectedMinBounds() {
        // 26. projected min bounds -> normalized 0,0
        val pt = ProjectedPoint(-5.0, -5.0)
        val norm = GeoEngine.normalize(pt, bounds)
        assertNotNull(norm)
        assertEquals(0.0, norm!!.x, 1e-9)
        assertEquals(0.0, norm.y, 1e-9)
    }

    @Test
    fun testProjectedMaxBounds() {
        // 27. projected max bounds -> normalized 1,1
        val pt = ProjectedPoint(5.0, 5.0)
        val norm = GeoEngine.normalize(pt, bounds)
        assertNotNull(norm)
        assertEquals(1.0, norm!!.x, 1e-9)
        assertEquals(1.0, norm.y, 1e-9)
    }

    @Test
    fun testCenterNormalizedValue() {
        // 28. center -> expected normalized value
        val pt = ProjectedPoint(0.0, 0.0)
        val norm = GeoEngine.normalize(pt, bounds)
        assertNotNull(norm)
        assertEquals(0.5, norm!!.x, 1e-9)
        assertEquals(0.5, norm.y, 1e-9)
    }

    @Test
    fun testOutsideBoundsNotClamped() {
        // 29. outside bounds is NOT silently clamped
        val pt = ProjectedPoint(10.0, -10.0)
        val norm = GeoEngine.normalize(pt, bounds)
        assertNotNull(norm)
        // xNorm = (10 - (-5)) / 10 = 1.5
        // yNorm = (-10 - (-5)) / 10 = -0.5
        assertEquals(1.5, norm!!.x, 1e-9)
        assertEquals(-0.5, norm.y, 1e-9)
    }

    @Test
    fun testInvalidDegenerateBoundsHandledSafely() {
        // 30. invalid/degenerate bounds handled safely (returns null)
        val badBounds = ProjectedBounds(0.0, 0.0, -2.0, -2.0)
        val pt = ProjectedPoint(1.0, 1.0)
        val norm = GeoEngine.normalize(pt, badBounds)
        assertNull(norm)
    }

    @Test
    fun testZeroWidthBoundsFail() {
        // Zero-width bounds must fail (return null)
        val badBounds = ProjectedBounds(5.0, 0.0, 5.0, 10.0)
        val pt = ProjectedPoint(5.0, 5.0)
        assertNull(GeoEngine.normalize(pt, badBounds))
    }

    @Test
    fun testZeroHeightBoundsFail() {
        // Zero-height bounds must fail (return null)
        val badBounds = ProjectedBounds(0.0, 5.0, 10.0, 5.0)
        val pt = ProjectedPoint(5.0, 5.0)
        assertNull(GeoEngine.normalize(pt, badBounds))
    }

    @Test
    fun testNanBoundsFail() {
        // NaN bounds must fail (return null)
        val badBounds = ProjectedBounds(Double.NaN, 0.0, 10.0, 10.0)
        val pt = ProjectedPoint(5.0, 5.0)
        assertNull(GeoEngine.normalize(pt, badBounds))
    }

    @Test
    fun testInfiniteBoundsFail() {
        // Infinite bounds must fail (return null)
        val badBounds = ProjectedBounds(Double.NEGATIVE_INFINITY, 0.0, 10.0, 10.0)
        val pt = ProjectedPoint(5.0, 5.0)
        assertNull(GeoEngine.normalize(pt, badBounds))
    }

    @Test
    fun testNanProjectedPointFail() {
        // NaN projected points must fail (return null)
        val pt = ProjectedPoint(Double.NaN, 5.0)
        assertNull(GeoEngine.normalize(pt, bounds))
    }

    @Test
    fun testInfiniteProjectedPointFail() {
        // Infinite projected points must fail (return null)
        val pt = ProjectedPoint(Double.POSITIVE_INFINITY, 5.0)
        assertNull(GeoEngine.normalize(pt, bounds))
    }


    // ==========================================
    // ROBUSTNESS TESTS
    // ==========================================

    @Test
    fun testEmptyMultiPolygon() {
        // 31. empty MultiPolygon -> OUTSIDE
        val emptyMulti = GeoMultiPolygon(emptyList())
        val p = GeoPoint(1.0, 1.0)
        assertEquals(GeoContainment.OUTSIDE, GeoEngine.classify(p, emptyMulti))
    }

    @Test
    fun testMalformedInsufficientPolygonRing() {
        // 32. malformed/insufficient polygon ring handled safely
        // A ring must have at least 3 points to form a polygon
        val badPolygon = GeoPolygon(
            outerRing = listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 5.0))
        )
        val p = GeoPoint(0.0, 2.0)
        assertEquals(GeoContainment.OUTSIDE, GeoEngine.classifyPolygon(p, badPolygon))
    }

    @Test
    fun testDuplicateClosingPoint() {
        // 33. duplicate closing point does not break classification
        val closedPolygon = GeoPolygon(
            outerRing = listOf(
                GeoPoint(0.0, 0.0),
                GeoPoint(0.0, 10.0),
                GeoPoint(10.0, 10.0),
                GeoPoint(10.0, 0.0),
                GeoPoint(0.0, 0.0) // explicit closed
            )
        )
        val p = GeoPoint(5.0, 5.0)
        assertEquals(GeoContainment.INSIDE, GeoEngine.classifyPolygon(p, closedPolygon))

        val boundaryPoint = GeoPoint(0.0, 5.0)
        assertEquals(GeoContainment.INSIDE, GeoEngine.classifyPolygon(boundaryPoint, closedPolygon))
    }

    @Test
    fun testCoordinatesNearSegmentBoundary() {
        // 34. coordinates near segment boundary remain deterministic
        val pLeft = GeoPoint(5.0, 0.00000000001) // inside/on boundary depending on epsilon
        val pRight = GeoPoint(5.0, -0.00000000001) // on boundary
        // Edge check works perfectly inside/on boundary
        assertEquals(GeoContainment.INSIDE, GeoEngine.classifyPolygon(pLeft, squarePolygon))
        assertEquals(GeoContainment.INSIDE, GeoEngine.classifyPolygon(pRight, squarePolygon))
    }
}
