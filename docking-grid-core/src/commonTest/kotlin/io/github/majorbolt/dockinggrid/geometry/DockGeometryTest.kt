package io.github.majorbolt.dockinggrid.geometry

import io.github.majorbolt.dockinggrid.DockFrameLevel
import io.github.majorbolt.dockinggrid.DockLayout
import io.github.majorbolt.dockinggrid.DockLayoutOperation
import io.github.majorbolt.dockinggrid.DockOperationResult
import io.github.majorbolt.dockinggrid.DockSide
import io.github.majorbolt.dockinggrid.DockableId
import io.github.majorbolt.dockinggrid.DockingCapabilities
import io.github.majorbolt.dockinggrid.LayoutOrientation
import io.github.majorbolt.dockinggrid.TestDockables
import io.github.majorbolt.dockinggrid.TestDockables.AudioMixer
import io.github.majorbolt.dockinggrid.TestDockables.Players
import io.github.majorbolt.dockinggrid.TestDockables.Scenes
import io.github.majorbolt.dockinggrid.TestDockables.StreamInfo
import io.github.majorbolt.dockinggrid.applyOperation
import io.github.majorbolt.dockinggrid.closingDockable
import io.github.majorbolt.dockinggrid.defaultWindowDockState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Default layout in a 1600 × 900 dp content area with 8 dp dividers:
 * LEFT lane 400 wide (Scenes over StreamInfo), RIGHT lane 400 wide (Players over AudioMixer),
 * center 784 wide starting at x = 408.
 */
class DockGeometryTest {

    private val contentSize = DockSize(widthDp = 1600f, heightDp = 900f)
    private val defaultLayout = defaultWindowDockState(TestDockables.catalog, DockingCapabilities.Desktop).profile(LayoutOrientation.LANDSCAPE).layout

    private fun DockLayout.applyOrFail(operation: DockLayoutOperation): DockLayout =
        assertIs<DockOperationResult.Applied>(applyOperation(operation, LayoutOrientation.LANDSCAPE, DockingCapabilities.Desktop)).layout

    private fun DockLayout.groupIdOf(dockableId: DockableId): String =
        allGroups.first { group -> dockableId in group.dockableIds }.id

    @Test
    fun defaultLayoutPlacesSideLanesByFractionAndCenterFillsTheRest() {
        val geometry = calculateDockGeometry(defaultLayout, contentSize, TestDockables.catalog)

        assertEquals(DockRect(0f, 0f, 400f, 446f), geometry.groupRects.getValue(defaultLayout.groupIdOf(Scenes)))
        assertEquals(DockRect(0f, 454f, 400f, 446f), geometry.groupRects.getValue(defaultLayout.groupIdOf(StreamInfo)))
        assertEquals(DockRect(1200f, 0f, 400f, 446f), geometry.groupRects.getValue(defaultLayout.groupIdOf(Players)))
        assertEquals(DockRect(408f, 0f, 784f, 900f), geometry.centerRect)
        assertEquals(geometry.centerRect, geometry.innerFrameRect)
    }

    @Test
    fun dividersReportOrientationAndWhatTheyResize() {
        val geometry = calculateDockGeometry(defaultLayout, contentSize, TestDockables.catalog)
        val leftLaneId = defaultLayout.outerFrame.lanes(DockSide.LEFT).single().id

        val laneDivider = geometry.dividers.single { divider -> divider.target == DividerTarget.LaneExtent(leftLaneId, DockFrameLevel.OUTER, DockSide.LEFT) }
        assertEquals(DockRect(400f, 0f, 8f, 900f), laneDivider.rect)
        assertTrue(laneDivider.movesHorizontally)

        val groupDivider = geometry.dividers.single { divider -> divider.target == DividerTarget.GroupBoundary(leftLaneId, 1) }
        assertEquals(DockRect(0f, 446f, 400f, 8f), groupDivider.rect)
        assertFalse(groupDivider.movesHorizontally)
        assertEquals(4, geometry.dividers.size)
    }

    @Test
    fun closingAnUnrelatedDockKeepsTheOtherLaneWidthAndTheCenterGrows() {
        val layoutWithoutRight = defaultWindowDockState(TestDockables.catalog, DockingCapabilities.Desktop)
            .closingDockable(Players)
            .closingDockable(AudioMixer)
            .profile(LayoutOrientation.LANDSCAPE)
            .layout

        val geometry = calculateDockGeometry(layoutWithoutRight, contentSize, TestDockables.catalog)

        assertEquals(400f, geometry.laneRects.getValue(layoutWithoutRight.outerFrame.lanes(DockSide.LEFT).single().id).width)
        assertEquals(DockRect(408f, 0f, 1192f, 900f), geometry.centerRect)
    }

    @Test
    fun outerTopSpansTheFullWidthAndSideLanesSitBelowIt() {
        val layout = defaultLayout.applyOrFail(DockLayoutOperation.MoveToNewLane(StreamInfo, DockFrameLevel.OUTER, DockSide.TOP, 0))

        val geometry = calculateDockGeometry(layout, contentSize, TestDockables.catalog)

        assertEquals(DockRect(0f, 0f, 1600f, 180f), geometry.groupRects.getValue(layout.groupIdOf(StreamInfo)))
        assertEquals(DockRect(0f, 188f, 400f, 712f), geometry.groupRects.getValue(layout.groupIdOf(Scenes)))
    }

    @Test
    fun innerTopSitsBetweenTheOuterSideLanes() {
        val layout = defaultLayout.applyOrFail(DockLayoutOperation.MoveToNewLane(StreamInfo, DockFrameLevel.INNER, DockSide.TOP, 0))

        val geometry = calculateDockGeometry(layout, contentSize, TestDockables.catalog)

        assertEquals(DockRect(408f, 0f, 784f, 180f), geometry.groupRects.getValue(layout.groupIdOf(StreamInfo)))
        assertEquals(DockRect(408f, 188f, 784f, 712f), geometry.centerRect)
        assertEquals(DockRect(0f, 0f, 400f, 900f), geometry.groupRects.getValue(layout.groupIdOf(Scenes)))
    }

    @Test
    fun lanesNeverRenderBelowTheirMinimumWhileSpaceSuffices() {
        // Desired 225 dp each; Scenes/StreamInfo need 240 dp, Players/AudioMixer 260 dp, the center 320 dp.
        val geometry = calculateDockGeometry(defaultLayout, DockSize(widthDp = 900f, heightDp = 900f), TestDockables.catalog)

        assertEquals(240f, geometry.laneRects.getValue(defaultLayout.outerFrame.lanes(DockSide.LEFT).single().id).width)
        assertEquals(260f, geometry.laneRects.getValue(defaultLayout.outerFrame.lanes(DockSide.RIGHT).single().id).width)
        assertEquals(384f, geometry.centerRect.width)
    }

    @Test
    fun whenMinimumsCannotFitEverythingShrinksProportionallyWithoutNegativeSizes() {
        val geometry = calculateDockGeometry(defaultLayout, DockSize(widthDp = 500f, heightDp = 300f), TestDockables.catalog)

        val leftWidth = geometry.laneRects.getValue(defaultLayout.outerFrame.lanes(DockSide.LEFT).single().id).width
        val rightWidth = geometry.laneRects.getValue(defaultLayout.outerFrame.lanes(DockSide.RIGHT).single().id).width
        assertEquals(500f, leftWidth + rightWidth + 16f + geometry.centerRect.width, 0.01f)
        assertEquals(240f / 260f, leftWidth / rightWidth, 0.001f)
        geometry.groupRects.values.forEach { rect -> assertTrue(rect.width >= 0f && rect.height >= 0f) }
    }

    @Test
    fun minimumContentSizeAndFitCheck() {
        // Width: 240 + 8 + 260 + 8 + 320 (center). Height: the RIGHT column 200 + 8 + 220.
        assertEquals(DockSize(widthDp = 836f, heightDp = 428f), defaultLayout.minimumContentSize(TestDockables.catalog))
        assertTrue(defaultLayout.fitsInto(DockSize(836f, 428f), TestDockables.catalog))
        assertFalse(defaultLayout.fitsInto(DockSize(835f, 900f), TestDockables.catalog))
    }

    @Test
    fun groupFractionsSplitTheLaneAndRespectGroupMinimums() {
        val leftLaneId = defaultLayout.outerFrame.lanes(DockSide.LEFT).single().id
        val layout = defaultLayout.applyOrFail(DockLayoutOperation.ResizeGroups(leftLaneId, listOf(0.9f, 0.1f)))

        val geometry = calculateDockGeometry(layout, contentSize, TestDockables.catalog)

        // StreamInfo would get 0.1 × 900 − 4 = 86 dp but needs 180 dp.
        assertEquals(180f, geometry.groupRects.getValue(layout.groupIdOf(StreamInfo)).height, 0.01f)
        assertEquals(712f, geometry.groupRects.getValue(layout.groupIdOf(Scenes)).height, 0.01f)
    }

    @Test
    fun splittingAGroupLeavesTheOtherGroupsOfTheLaneExactlyInPlace() {
        // Regression B7: Players splits StreamInfo; the new divider comes out of StreamInfo and Players only.
        val leftLaneId = defaultLayout.outerFrame.lanes(DockSide.LEFT).single().id
        val scenesRectBefore = calculateDockGeometry(defaultLayout, contentSize, TestDockables.catalog).groupRects.getValue(defaultLayout.groupIdOf(Scenes))
        val layout = defaultLayout.applyOrFail(
            DockLayoutOperation.MoveToNewGroup(Players, leftLaneId, groupIndex = 2, splitGroupId = defaultLayout.groupIdOf(StreamInfo)),
        )

        val groupRects = calculateDockGeometry(layout, contentSize, TestDockables.catalog).groupRects

        assertEquals(DockRect(0f, 0f, 400f, 446f), scenesRectBefore)
        assertEquals(scenesRectBefore, groupRects.getValue(layout.groupIdOf(Scenes)))
        // Each divider belongs half to each neighbour: the middle group owns two halves, the last one half.
        assertEquals(DockRect(0f, 454f, 400f, 217f), groupRects.getValue(layout.groupIdOf(StreamInfo)))
        assertEquals(DockRect(0f, 679f, 400f, 221f), groupRects.getValue(layout.groupIdOf(Players)))
    }

    @Test
    fun theLengthsOfAllGroupsAndDividersFillTheLane() {
        val leftLaneId = defaultLayout.outerFrame.lanes(DockSide.LEFT).single().id
        val layout = defaultLayout
            .applyOrFail(DockLayoutOperation.MoveToNewGroup(Players, leftLaneId, groupIndex = 2))
            .applyOrFail(DockLayoutOperation.ResizeGroups(leftLaneId, listOf(0.3f, 0.3f, 0.4f)))

        val groupRects = calculateDockGeometry(layout, contentSize, TestDockables.catalog).groupRects
        val heights = listOf(Scenes, StreamInfo, Players).map { dockableId -> groupRects.getValue(layout.groupIdOf(dockableId)).height }

        assertEquals(listOf(266f, 262f, 356f), heights.map { height -> kotlin.math.round(height) })
        assertEquals(900f, heights.sum() + 2 * 8f, 0.01f)
    }
}
