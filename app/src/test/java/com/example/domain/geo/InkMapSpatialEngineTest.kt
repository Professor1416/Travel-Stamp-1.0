package com.example.domain.geo

import org.junit.Assert.*
import org.junit.Test

class InkMapSpatialEngineTest {

    // ==================================================
    // VIEWPORT / TRANSFORM TESTS
    // ==================================================

    private val defaultViewport = InkMapViewport(
        width = 800.0,
        height = 600.0,
        zoom = 1.0,
        panX = 0.0,
        panY = 0.0
    )

    private val squareAspect = 1.0

    @Test
    fun testNormalizedTopLeftTransformsCorrectly() {
        // 1. normalized top-left transforms correctly
        // Since viewport (800x600) is wider than squareAspect (1.0),
        // we letterbox horizontally. baseScale = 600.0. Map base width = 600.0.
        // offsetX = (800 - 600) / 2.0 = 100.0. offsetY = 0.0.
        // baseX for (0,0) = 100.0, baseY = 0.0.
        // Zoom = 1.0, Pan = 0.0 -> center = (400, 300).
        // screenX = (100 - 400) * 1 + 400 = 100.0
        // screenY = (0 - 300) * 1 + 300 = 0.0
        val p = InkMapPoint("tl", 0.0, 0.0)
        val screenPt = InkMapSpatialEngine.transform(p, defaultViewport, squareAspect)
        assertNotNull(screenPt)
        assertEquals(100.0, screenPt!!.x, 1e-9)
        assertEquals(0.0, screenPt.y, 1e-9)
    }

    @Test
    fun testNormalizedBottomRightTransformsCorrectly() {
        // 2. normalized bottom-right transforms correctly
        // baseX for (1,1) = 100 + 1 * 600 = 700.0, baseY = 600.0.
        // screenX = (700 - 400) * 1 + 400 = 700.0
        // screenY = (600 - 300) * 1 + 300 = 600.0
        val p = InkMapPoint("br", 1.0, 1.0)
        val screenPt = InkMapSpatialEngine.transform(p, defaultViewport, squareAspect)
        assertNotNull(screenPt)
        assertEquals(700.0, screenPt!!.x, 1e-9)
        assertEquals(600.0, screenPt.y, 1e-9)
    }

    @Test
    fun testCenterTransformsCorrectly() {
        // 3. center transforms correctly
        // baseX for (0.5, 0.5) = 100 + 0.5 * 600 = 400.0, baseY = 300.0.
        // screenX = 400.0, screenY = 300.0.
        val p = InkMapPoint("ctr", 0.5, 0.5)
        val screenPt = InkMapSpatialEngine.transform(p, defaultViewport, squareAspect)
        assertNotNull(screenPt)
        assertEquals(400.0, screenPt!!.x, 1e-9)
        assertEquals(300.0, screenPt.y, 1e-9)
    }

    @Test
    fun testPortraitViewportPreservesMapAspect() {
        // 4. portrait viewport preserves map aspect ratio (e.g. 600x800 viewport, map aspect 1.0)
        // Viewport is taller than map -> letterbox vertically.
        // baseScale = 600.0. Map base height = 600.0. Map base width = 600.0.
        // offsetX = 0.0, offsetY = (800 - 600) / 2 = 100.0.
        val portraitViewport = InkMapViewport(
            width = 600.0,
            height = 800.0,
            zoom = 1.0,
            panX = 0.0,
            panY = 0.0
        )
        val pTl = InkMapPoint("tl", 0.0, 0.0)
        val pBr = InkMapPoint("br", 1.0, 1.0)

        val screenTl = InkMapSpatialEngine.transform(pTl, portraitViewport, squareAspect)
        val screenBr = InkMapSpatialEngine.transform(pBr, portraitViewport, squareAspect)

        assertNotNull(screenTl)
        assertNotNull(screenBr)

        // Width on screen: screenBr.x - screenTl.x = 600.0 - 0.0 = 600.0
        // Height on screen: screenBr.y - screenTl.y = 700.0 - 100.0 = 600.0
        // Screen aspect ratio is 1.0 (preserved!)
        assertEquals(600.0, screenBr!!.x - screenTl!!.x, 1e-9)
        assertEquals(600.0, screenBr.y - screenTl.y, 1e-9)
    }

    @Test
    fun testLandscapeViewportPreservesMapAspect() {
        // 5. landscape viewport preserves map aspect ratio (e.g. 1200x600 viewport, map aspect 1.5)
        // Viewport aspect = 2.0 > 1.5 -> letterbox horizontally
        // baseScale = 600.0. Map base height = 600.0. Map base width = 900.0.
        // offsetX = (1200 - 900) / 2 = 150.0, offsetY = 0.0.
        val landscapeViewport = InkMapViewport(
            width = 1200.0,
            height = 600.0,
            zoom = 1.0,
            panX = 0.0,
            panY = 0.0
        )
        val pTl = InkMapPoint("tl", 0.0, 0.0)
        val pBr = InkMapPoint("br", 1.0, 1.0)

        val screenTl = InkMapSpatialEngine.transform(pTl, landscapeViewport, 1.5)
        val screenBr = InkMapSpatialEngine.transform(pBr, landscapeViewport, 1.5)

        assertNotNull(screenTl)
        assertNotNull(screenBr)

        // Width on screen: screenBr.x - screenTl.x = (150 + 900) - 150 = 900.0
        // Height on screen: screenBr.y - screenTl.y = 600.0 - 0.0 = 600.0
        // Map Aspect = 900 / 600 = 1.5 (preserved!)
        assertEquals(900.0, screenBr!!.x - screenTl!!.x, 1e-9)
        assertEquals(600.0, screenBr.y - screenTl.y, 1e-9)
    }

    @Test
    fun testMapIsCenteredWhenLetterboxed() {
        // 6. map is centered when letterboxed
        // On defaultViewport (800x600), map aspect 1.0 -> letterboxed horizontally
        // offsetX is 100.0 on both sides.
        val pTl = InkMapPoint("tl", 0.0, 0.5)
        val screenTl = InkMapSpatialEngine.transform(pTl, defaultViewport, squareAspect)
        assertNotNull(screenTl)
        // Centering means left distance to edge (100.0) matches right distance from (1.0, 0.5) to viewport width (800.0)
        assertEquals(100.0, screenTl!!.x, 1e-9)

        val pRightEdge = InkMapPoint("re", 1.0, 0.5)
        val screenRe = InkMapSpatialEngine.transform(pRightEdge, defaultViewport, squareAspect)
        assertNotNull(screenRe)
        assertEquals(700.0, screenRe!!.x, 1e-9)
        assertEquals(100.0, 800.0 - screenRe.x, 1e-9)
    }

    @Test
    fun testZoomChangesScreenSeparationCorrectly() {
        // 7. zoom changes screen separation correctly
        val p1 = InkMapPoint("p1", 0.0, 0.0)
        val p2 = InkMapPoint("p2", 1.0, 1.0)

        val screen1X1 = InkMapSpatialEngine.transform(p1, defaultViewport, squareAspect)!!
        val screen2X1 = InkMapSpatialEngine.transform(p2, defaultViewport, squareAspect)!!
        val distAtZoom1 = kotlin.math.hypot(screen2X1.x - screen1X1.x, screen2X1.y - screen1X1.y)

        val viewportZoom2 = defaultViewport.copy(zoom = 2.0)
        val screen1X2 = InkMapSpatialEngine.transform(p1, viewportZoom2, squareAspect)!!
        val screen2X2 = InkMapSpatialEngine.transform(p2, viewportZoom2, squareAspect)!!
        val distAtZoom2 = kotlin.math.hypot(screen2X2.x - screen1X2.x, screen2X2.y - screen1X2.y)

        // At zoom 2.0, the distance between the points should be exactly 2x
        assertEquals(2.0 * distAtZoom1, distAtZoom2, 1e-9)
    }

    @Test
    fun testPanTranslatesPointsCorrectly() {
        // 8. pan translates points correctly
        val viewportPan = defaultViewport.copy(panX = 50.0, panY = -25.0)
        val p = InkMapPoint("p", 0.5, 0.5)
        val screenPtNormal = InkMapSpatialEngine.transform(p, defaultViewport, squareAspect)!!
        val screenPtPanned = InkMapSpatialEngine.transform(p, viewportPan, squareAspect)!!

        assertEquals(screenPtNormal.x + 50.0, screenPtPanned.x, 1e-9)
        assertEquals(screenPtNormal.y - 25.0, screenPtPanned.y, 1e-9)
    }

    @Test
    fun testOutsidePointsNotClamped() {
        // 9. outside-0..1 normalized points are not silently clamped
        val pOutside = InkMapPoint("out", 2.0, -1.0)
        val screenPt = InkMapSpatialEngine.transform(pOutside, defaultViewport, squareAspect)
        assertNotNull(screenPt)
        // baseX = 100 + 2.0 * 600 = 1300
        // screenX = (1300 - 400) * 1 + 400 = 1300
        // baseY = 0 - 1.0 * 600 = -600
        // screenY = (-600 - 300) * 1 + 300 = -600
        assertEquals(1300.0, screenPt!!.x, 1e-9)
        assertEquals(-600.0, screenPt.y, 1e-9)
    }

    @Test
    fun testInvalidViewportFails() {
        val p = InkMapPoint("p", 0.5, 0.5)
        // 10. zero-width viewport fails explicitly
        assertNull(InkMapSpatialEngine.transform(p, defaultViewport.copy(width = 0.0), squareAspect))
        // 11. zero-height viewport fails explicitly
        assertNull(InkMapSpatialEngine.transform(p, defaultViewport.copy(height = 0.0), squareAspect))
        // 12. negative/zero zoom fails explicitly
        assertNull(InkMapSpatialEngine.transform(p, defaultViewport.copy(zoom = 0.0), squareAspect))
        assertNull(InkMapSpatialEngine.transform(p, defaultViewport.copy(zoom = -1.0), squareAspect))
    }

    @Test
    fun testInfiniteAndNanViewportFails() {
        val p = InkMapPoint("p", 0.5, 0.5)
        // 13. NaN/Infinity input fails explicitly
        assertNull(InkMapSpatialEngine.transform(p, defaultViewport.copy(width = Double.NaN), squareAspect))
        assertNull(InkMapSpatialEngine.transform(p.copy(normalizedX = Double.POSITIVE_INFINITY), defaultViewport, squareAspect))
        assertNull(InkMapSpatialEngine.transform(p, defaultViewport, Double.NaN))
    }


    // ==================================================
    // CLUSTERING TESTS
    // ==================================================

    @Test
    fun testEmptyInputClustering() {
        // 14. empty input -> empty output
        val result = InkMapSpatialEngine.cluster(emptyList(), 48.0)
        assertNotNull(result)
        assertTrue(result!!.isEmpty())
    }

    @Test
    fun testOnePointClustering() {
        // 15. one point -> individual
        val pt = InkScreenPoint("p1", 100.0, 100.0)
        val result = InkMapSpatialEngine.cluster(listOf(pt), 48.0)
        assertNotNull(result)
        assertEquals(1, result!!.size)
        assertTrue(result[0] is InkClusterResult.Individual)
        assertEquals("p1", (result[0] as InkClusterResult.Individual).point.id)
    }

    @Test
    fun testTwoDistantPointsClustering() {
        // 16. two distant points -> two individuals
        val pt1 = InkScreenPoint("p1", 100.0, 100.0)
        val pt2 = InkScreenPoint("p2", 200.0, 200.0) // Distance = sqrt(100^2 + 100^2) = ~141.4 > 48
        val result = InkMapSpatialEngine.cluster(listOf(pt1, pt2), 48.0)
        assertNotNull(result)
        assertEquals(2, result!!.size)
        assertTrue(result[0] is InkClusterResult.Individual)
        assertTrue(result[1] is InkClusterResult.Individual)
        assertEquals("p1", (result[0] as InkClusterResult.Individual).point.id)
        assertEquals("p2", (result[1] as InkClusterResult.Individual).point.id)
    }

    @Test
    fun testTwoNearbyPointsClustering() {
        // 17. two nearby points -> one cluster
        val pt1 = InkScreenPoint("p1", 100.0, 100.0)
        val pt2 = InkScreenPoint("p2", 110.0, 110.0) // Distance = sqrt(10^2 + 10^2) = ~14.14 <= 48
        val result = InkMapSpatialEngine.cluster(listOf(pt1, pt2), 48.0)
        assertNotNull(result)
        assertEquals(1, result!!.size)
        assertTrue(result[0] is InkClusterResult.Cluster)
        val cluster = result[0] as InkClusterResult.Cluster
        assertEquals("cluster_p1_p2", cluster.id)
        assertEquals(2, cluster.points.size)
    }

    @Test
    fun testExactRadiusPointsCluster() {
        // 18. exact-radius points cluster
        val pt1 = InkScreenPoint("p1", 0.0, 0.0)
        val pt2 = InkScreenPoint("p2", 48.0, 0.0) // Distance = exactly 48.0
        val result = InkMapSpatialEngine.cluster(listOf(pt1, pt2), 48.0)
        assertNotNull(result)
        assertEquals(1, result!!.size)
        assertTrue(result[0] is InkClusterResult.Cluster)
    }

    @Test
    fun testDuplicateCoordinatesCluster() {
        // 19. duplicate coordinates cluster
        val pt1 = InkScreenPoint("p1", 50.0, 50.0)
        val pt2 = InkScreenPoint("p2", 50.0, 50.0)
        val result = InkMapSpatialEngine.cluster(listOf(pt1, pt2), 10.0)
        assertNotNull(result)
        assertEquals(1, result!!.size)
        assertTrue(result[0] is InkClusterResult.Cluster)
        val cluster = result[0] as InkClusterResult.Cluster
        assertEquals(2, cluster.points.size)
        assertEquals(50.0, cluster.center.x, 1e-9)
        assertEquals(50.0, cluster.center.y, 1e-9)
    }

    @Test
    fun testTransitiveConnectivity() {
        // 20. transitive A-B-C connectivity produces one cluster
        // A(0,0), B(30,0), C(60,0). Radius = 40.
        // A to B = 30 (connected). B to C = 30 (connected). A to C = 60 (not directly connected).
        val pA = InkScreenPoint("pA", 0.0, 0.0)
        val pB = InkScreenPoint("pB", 30.0, 0.0)
        val pC = InkScreenPoint("pC", 60.0, 0.0)

        val result = InkMapSpatialEngine.cluster(listOf(pA, pB, pC), 40.0)
        assertNotNull(result)
        assertEquals(1, result!!.size)
        assertTrue(result[0] is InkClusterResult.Cluster)
        val cluster = result[0] as InkClusterResult.Cluster
        assertEquals(3, cluster.points.size)
        assertEquals("pA", cluster.points[0].id)
        assertEquals("pB", cluster.points[1].id)
        assertEquals("pC", cluster.points[2].id)
    }

    @Test
    fun testInputPermutationDoesNotChangeCluster() {
        // 21. input permutation does not change cluster membership
        val pA = InkScreenPoint("pA", 0.0, 0.0)
        val pB = InkScreenPoint("pB", 30.0, 0.0)
        val pC = InkScreenPoint("pC", 60.0, 0.0)

        val perm1 = listOf(pA, pB, pC)
        val perm2 = listOf(pC, pA, pB)
        val perm3 = listOf(pB, pC, pA)

        val r1 = InkMapSpatialEngine.cluster(perm1, 40.0)
        val r2 = InkMapSpatialEngine.cluster(perm2, 40.0)
        val r3 = InkMapSpatialEngine.cluster(perm3, 40.0)

        assertEquals(r1, r2)
        assertEquals(r1, r3)
    }

    @Test
    fun testClusterCentroid() {
        // 22. cluster centroid is mathematically correct (arithmetic mean)
        val pt1 = InkScreenPoint("p1", 10.0, 20.0)
        val pt2 = InkScreenPoint("p2", 20.0, 40.0)
        val pt3 = InkScreenPoint("p3", 30.0, 60.0)

        val result = InkMapSpatialEngine.cluster(listOf(pt1, pt2, pt3), 50.0)
        assertNotNull(result)
        assertEquals(1, result!!.size)
        assertTrue(result[0] is InkClusterResult.Cluster)
        val cluster = result[0] as InkClusterResult.Cluster

        // Mean X: (10 + 20 + 30) / 3 = 20.0
        // Mean Y: (20 + 40 + 60) / 3 = 40.0
        assertEquals(20.0, cluster.center.x, 1e-9)
        assertEquals(40.0, cluster.center.y, 1e-9)
    }

    @Test
    fun testMixedOutputIsolatedPlusClustered() {
        // 23. isolated + clustered points produce correct mixed output
        val pt1 = InkScreenPoint("p1", 0.0, 0.0)
        val pt2 = InkScreenPoint("p2", 10.0, 0.0) // clusters with pt1
        val pt3 = InkScreenPoint("p3", 500.0, 500.0) // isolated

        val result = InkMapSpatialEngine.cluster(listOf(pt1, pt2, pt3), 20.0)
        assertNotNull(result)
        assertEquals(2, result!!.size)

        // Result 1 should be the cluster containing p1 and p2 (since first member is p1)
        assertTrue(result[0] is InkClusterResult.Cluster)
        assertEquals("cluster_p1_p2", (result[0] as InkClusterResult.Cluster).id)

        // Result 2 should be the individual p3
        assertTrue(result[1] is InkClusterResult.Individual)
        assertEquals("p3", (result[1] as InkClusterResult.Individual).point.id)
    }

    @Test
    fun testZoomChangesClustering() {
        // 24. different zoom levels can change cluster membership after transformation
        val p1 = InkMapPoint("p1", 0.5, 0.5)
        val p2 = InkMapPoint("p2", 0.55, 0.5) // moderately close in normalized space

        // Under 1x zoom:
        val screen1X1 = InkMapSpatialEngine.transform(p1, defaultViewport, squareAspect)!!
        val screen2X1 = InkMapSpatialEngine.transform(p2, defaultViewport, squareAspect)!!
        // dx = 0.05 * 600 = 30.0 px. Clustered when radius = 40.
        val cluster1x = InkMapSpatialEngine.cluster(listOf(screen1X1, screen2X1), 40.0)
        assertNotNull(cluster1x)
        assertEquals(1, cluster1x!!.size) // forms 1 cluster
        assertTrue(cluster1x[0] is InkClusterResult.Cluster)

        // Under 5x zoom:
        val viewportZoom5 = defaultViewport.copy(zoom = 5.0)
        val screen1X5 = InkMapSpatialEngine.transform(p1, viewportZoom5, squareAspect)!!
        val screen2X5 = InkMapSpatialEngine.transform(p2, viewportZoom5, squareAspect)!!
        // dx = 30 * 5 = 150 px. Separated when radius = 40.
        val cluster5x = InkMapSpatialEngine.cluster(listOf(screen1X5, screen2X5), 40.0)
        assertNotNull(cluster5x)
        assertEquals(2, cluster5x!!.size) // splits into 2 individuals
        assertTrue(cluster5x[0] is InkClusterResult.Individual)
        assertTrue(cluster5x[1] is InkClusterResult.Individual)
    }

    @Test
    fun testDeterministicMemberOrdering() {
        // 25. deterministic member ordering
        val p1 = InkScreenPoint("p1", 0.0, 0.0)
        val p2 = InkScreenPoint("p2", 5.0, 5.0)
        val p3 = InkScreenPoint("p3", 10.0, 10.0)

        // Permutations of inputs should yield the same member list sorted by ID: p1, p2, p3
        val r1 = InkMapSpatialEngine.cluster(listOf(p2, p3, p1), 20.0)!![0] as InkClusterResult.Cluster
        val r2 = InkMapSpatialEngine.cluster(listOf(p3, p1, p2), 20.0)!![0] as InkClusterResult.Cluster

        assertEquals("p1", r1.points[0].id)
        assertEquals("p2", r1.points[1].id)
        assertEquals("p3", r1.points[2].id)

        assertEquals("p1", r2.points[0].id)
        assertEquals("p2", r2.points[1].id)
        assertEquals("p3", r2.points[2].id)
    }

    @Test
    fun testDeterministicOutputOrdering() {
        // 26. deterministic output ordering
        val pA = InkScreenPoint("pA", 0.0, 0.0) // individual
        val pB = InkScreenPoint("pB", 200.0, 200.0)
        val pC = InkScreenPoint("pC", 205.0, 205.0) // cluster with B

        // Output results list should always be sorted by first member's ID: cluster_pB_pC comes after pA (or before, depending on sorting key)
        // Our key sorts Individual by point.id, Cluster by points.first().id.
        // First member of Individual pA -> "pA". First member of Cluster pB_pC -> "pB".
        // Sorted: "pA" first, then "pB".
        val r1 = InkMapSpatialEngine.cluster(listOf(pC, pB, pA), 20.0)!!
        assertEquals(2, r1.size)
        assertTrue(r1[0] is InkClusterResult.Individual)
        assertEquals("pA", (r1[0] as InkClusterResult.Individual).point.id)
        assertTrue(r1[1] is InkClusterResult.Cluster)
        assertEquals("cluster_pB_pC", (r1[1] as InkClusterResult.Cluster).id)
    }

    @Test
    fun testZeroRadiusHandlesDuplicates() {
        // 27. zero cluster radius handles duplicate coordinates correctly
        val p1 = InkScreenPoint("p1", 50.0, 50.0)
        val p2 = InkScreenPoint("p2", 50.0, 50.0) // duplicate
        val p3 = InkScreenPoint("p3", 51.0, 51.0) // close but not duplicate

        val result = InkMapSpatialEngine.cluster(listOf(p1, p2, p3), 0.0)
        assertNotNull(result)
        // p1 and p2 must cluster together (distance 0.0 <= 0.0)
        // p3 must remain isolated
        assertEquals(2, result!!.size)
        assertTrue(result[0] is InkClusterResult.Cluster)
        assertEquals("cluster_p1_p2", (result[0] as InkClusterResult.Cluster).id)
        assertTrue(result[1] is InkClusterResult.Individual)
        assertEquals("p3", (result[1] as InkClusterResult.Individual).point.id)
    }

    @Test
    fun testInvalidClusterRadiusFails() {
        // 28. negative/non-finite cluster radius fails explicitly
        val points = listOf(InkScreenPoint("p", 10.0, 10.0))
        assertNull(InkMapSpatialEngine.cluster(points, -5.0))
        assertNull(InkMapSpatialEngine.cluster(points, Double.NaN))
    }

    @Test
    fun testDuplicateIdsRejected() {
        // Duplicate IDs should NOT be silently accepted.
        val p1 = InkScreenPoint("p1", 10.0, 10.0)
        val p2 = InkScreenPoint("p1", 20.0, 20.0)
        assertNull(InkMapSpatialEngine.cluster(listOf(p1, p2), 48.0))
    }


    // ==================================================
    // VISIBILITY TESTS
    // ==================================================

    @Test
    fun testPointInsideViewportIsVisible() {
        // 29. point inside viewport is visible
        val pt = InkScreenPoint("p", 400.0, 300.0)
        assertTrue(InkMapSpatialEngine.isVisible(pt, defaultViewport))
    }

    @Test
    fun testPointOutsideViewportIsNotVisible() {
        // 30. point outside viewport is not visible
        val pt = InkScreenPoint("p", 900.0, 300.0)
        assertFalse(InkMapSpatialEngine.isVisible(pt, defaultViewport))
    }

    @Test
    fun testVisibilityMargin() {
        // 31. visibility margin behaves correctly
        val pt = InkScreenPoint("p", 850.0, 300.0)
        assertFalse(InkMapSpatialEngine.isVisible(pt, defaultViewport, margin = 0.0))
        assertTrue(InkMapSpatialEngine.isVisible(pt, defaultViewport, margin = 100.0))
    }

    @Test
    fun testInvalidVisibilityParametersFailSafely() {
        // 32. invalid visibility parameters fail safely
        val pt = InkScreenPoint("p", 400.0, 300.0)
        assertFalse(InkMapSpatialEngine.isVisible(pt, defaultViewport.copy(width = -100.0)))
        assertFalse(InkMapSpatialEngine.isVisible(pt, defaultViewport, margin = -10.0))
        assertFalse(InkMapSpatialEngine.isVisible(pt.copy(x = Double.NaN), defaultViewport))
    }


    // ==================================================
    // 500-POINT PERFORMANCE SANITY TEST
    // ==================================================

    @Test
    fun test500PointsPerformanceSanity() {
        // 33. performance check with ~500 points
        // Create 500 synthetic points distributed in a grid
        val points = mutableListOf<InkScreenPoint>()
        for (i in 0 until 500) {
            val gridX = (i % 25) * 50.0
            val gridY = (i / 25) * 50.0
            points.add(InkScreenPoint("p$i", gridX, gridY))
        }

        // Run the clustering at 48.0 pixels radius
        val startTime = System.nanoTime()
        val results = InkMapSpatialEngine.cluster(points, 48.0)
        val endTime = System.nanoTime()

        assertNotNull(results)

        // All IDs must appear exactly once across the final outputs
        val foundIds = mutableSetOf<String>()
        var pointCount = 0

        for (res in results!!) {
            when (res) {
                is InkClusterResult.Individual -> {
                    assertTrue(foundIds.add(res.point.id))
                    pointCount++
                }
                is InkClusterResult.Cluster -> {
                    pointCount += res.points.size
                    for (member in res.points) {
                        assertTrue(foundIds.add(member.id))
                    }
                }
            }
        }

        assertEquals(500, pointCount)
        assertEquals(500, foundIds.size)

        // Verify that we can run without issues in microseconds or milliseconds
        val durationMs = (endTime - startTime) / 1_000_000.0
        println("Clustering 500 points took: $durationMs ms")
    }
}
