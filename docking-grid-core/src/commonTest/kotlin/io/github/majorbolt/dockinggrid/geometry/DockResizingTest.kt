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
import io.github.majorbolt.dockinggrid.TestDockables.Players
import io.github.majorbolt.dockinggrid.TestDockables.Scenes
import io.github.majorbolt.dockinggrid.TestDockables.StreamInfo
import io.github.majorbolt.dockinggrid.applyOperation
import io.github.majorbolt.dockinggrid.defaultWindowDockState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/** Default layout in 1600 × 900 dp: LEFT and RIGHT lanes 400 dp, center 784 dp (minimum 320 dp). */
class DockResizingTest {

    private val contentSize = DockSize(widthDp = 1600f, heightDp = 900f)
    private val defaultLayout = defaultWindowDockState(TestDockables.catalog, DockingCapabilities.Desktop).profile(LayoutOrientation.LANDSCAPE).layout

    private fun DockLayout.applyOrFail(operation: DockLayoutOperation): DockLayout =
        assertIs<DockOperationResult.Applied>(applyOperation(operation, LayoutOrientation.LANDSCAPE, DockingCapabilities.Desktop)).layout

    private fun DockLayout.laneDivider(side: DockSide, frameLevel: DockFrameLevel = DockFrameLevel.OUTER, laneIndex: Int = 0): DockDivider {
        val laneId = frame(frameLevel).lanes(side)[laneIndex].id
        return calculateDockGeometry(this, contentSize, TestDockables.catalog).dividers.single { divider ->
            divider.target == DividerTarget.LaneExtent(laneId, frameLevel, side)
        }
    }

    private fun DockLayout.resizedBy(divider: DockDivider, dragDistanceDp: Float): DockLayout =
        applyOrFail(assertNotNull(resizeOperationFor(this, divider, dragDistanceDp, contentSize, TestDockables.catalog)))

    private fun DockLayout.laneWidth(side: DockSide, frameLevel: DockFrameLevel = DockFrameLevel.OUTER): Float =
        calculateDockGeometry(this, contentSize, TestDockables.catalog).laneRects.getValue(frame(frameLevel).lanes(side).first().id).width

    private fun DockLayout.laneHeight(side: DockSide, frameLevel: DockFrameLevel = DockFrameLevel.OUTER): Float =
        calculateDockGeometry(this, contentSize, TestDockables.catalog).laneRects.getValue(frame(frameLevel).lanes(side).first().id).height

    private fun DockLayout.groupHeight(dockableId: DockableId): Float {
        val groupId = allGroups.first { group -> dockableId in group.dockableIds }.id
        return calculateDockGeometry(this, contentSize, TestDockables.catalog).groupRects.getValue(groupId).height
    }

    @Test
    fun draggingTheLeftDividerResizesOnlyThatLaneAndTheCenterAbsorbsTheChange() {
        val resized = defaultLayout.resizedBy(defaultLayout.laneDivider(DockSide.LEFT), dragDistanceDp = 100f)

        assertEquals(500f, resized.laneWidth(DockSide.LEFT), 0.01f)
        assertEquals(400f, resized.laneWidth(DockSide.RIGHT), 0.01f)
        assertEquals(684f, calculateDockGeometry(resized, contentSize, TestDockables.catalog).centerRect.width, 0.01f)
        assertEquals(500f / 1600f, resized.outerFrame.lanes(DockSide.LEFT).single().extentFraction, 0.0001f)
    }

    @Test
    fun theRightLaneGrowsWhenItsDividerMovesLeft() {
        val resized = defaultLayout.resizedBy(defaultLayout.laneDivider(DockSide.RIGHT), dragDistanceDp = -100f)

        assertEquals(500f, resized.laneWidth(DockSide.RIGHT), 0.01f)
    }

    @Test
    fun growingStopsWhenTheCenterReachesItsMinimum() {
        val resized = defaultLayout.resizedBy(defaultLayout.laneDivider(DockSide.LEFT), dragDistanceDp = 1000f)

        // The center can give up 784 - 320 = 464 dp.
        assertEquals(864f, resized.laneWidth(DockSide.LEFT), 0.5f)
        assertEquals(400f, resized.laneWidth(DockSide.RIGHT), 0.01f)
    }

    @Test
    fun shrinkingStopsAtTheLaneMinimum() {
        val resized = defaultLayout.resizedBy(defaultLayout.laneDivider(DockSide.LEFT), dragDistanceDp = -1000f)

        // Scenes needs 220 dp and StreamInfo 240 dp.
        assertEquals(240f, resized.laneWidth(DockSide.LEFT), 0.01f)
    }

    @Test
    fun growingAnOuterLaneNeverSqueezesAnInnerLane() {
        val layout = defaultLayout.applyOrFail(DockLayoutOperation.MoveToNewLane(Players, DockFrameLevel.INNER, DockSide.LEFT, 0))
        // Center: 1600 - 3 × (400 + 8) = 376 dp, minimum 320 dp, so 56 dp of slack.

        val resized = layout.resizedBy(layout.laneDivider(DockSide.LEFT), dragDistanceDp = 500f)

        assertEquals(456f, resized.laneWidth(DockSide.LEFT), 0.5f)
        assertEquals(400f, resized.laneWidth(DockSide.LEFT, DockFrameLevel.INNER), 0.5f)
    }

    @Test
    fun anOuterColumnGrowsTowardsTheCenterWhenTheInnerFrameHasARow() {
        // Regression B1: the inner TOP row gets narrower, which is a change of its length, not of its extent.
        val layout = defaultLayout.applyOrFail(DockLayoutOperation.MoveToNewLane(Players, DockFrameLevel.INNER, DockSide.TOP, 0))

        val innerRowHeightBefore = layout.laneHeight(DockSide.TOP, DockFrameLevel.INNER)

        val resized = layout.resizedBy(layout.laneDivider(DockSide.LEFT), dragDistanceDp = 50f)

        assertEquals(450f, resized.laneWidth(DockSide.LEFT), 0.01f)
        assertEquals(innerRowHeightBefore, resized.laneHeight(DockSide.TOP, DockFrameLevel.INNER), 0.01f)
    }

    @Test
    fun anOuterRowGrowsTowardsTheCenterWhenColumnsExist() {
        // Regression B1: the LEFT and RIGHT columns get shorter, which is a change of their length, not of their extent.
        val layout = defaultLayout.applyOrFail(DockLayoutOperation.MoveToNewLane(Players, DockFrameLevel.OUTER, DockSide.TOP, 0))

        val rowHeightBefore = layout.laneHeight(DockSide.TOP)

        val resized = layout.resizedBy(layout.laneDivider(DockSide.TOP), dragDistanceDp = 50f)

        assertEquals(rowHeightBefore + 50f, resized.laneHeight(DockSide.TOP), 0.01f)
        assertEquals(400f, resized.laneWidth(DockSide.LEFT), 0.01f)
        assertEquals(400f, resized.laneWidth(DockSide.RIGHT), 0.01f)
    }

    @Test
    fun groupBoundaryMovesBetweenTheTwoNeighboursAndRespectsTheirMinimums() {
        val leftLaneId = defaultLayout.outerFrame.lanes(DockSide.LEFT).single().id
        val boundary = calculateDockGeometry(defaultLayout, contentSize, TestDockables.catalog).dividers.single { divider ->
            divider.target == DividerTarget.GroupBoundary(leftLaneId, 1)
        }

        val moved = defaultLayout.resizedBy(boundary, dragDistanceDp = 100f)
        assertEquals(546f, moved.groupHeight(Scenes), 0.01f)
        assertEquals(346f, moved.groupHeight(StreamInfo), 0.01f)

        val clamped = defaultLayout.resizedBy(boundary, dragDistanceDp = 1000f)
        assertEquals(180f, clamped.groupHeight(StreamInfo), 0.01f)
        assertEquals(712f, clamped.groupHeight(Scenes), 0.01f)
    }

    @Test
    fun aGroupDividerInAThreeGroupLaneMovesOnlyItsNeighboursAndFollowsThePointer() {
        // Thirds of 900 dp: Scenes 296, StreamInfo 292 (two divider halves), Players 296.
        val leftLaneId = defaultLayout.outerFrame.lanes(DockSide.LEFT).single().id
        val layout = defaultLayout
            .applyOrFail(DockLayoutOperation.MoveToNewGroup(Players, leftLaneId, groupIndex = 2))
            .applyOrFail(DockLayoutOperation.ResizeGroups(leftLaneId, listOf(1f / 3f, 1f / 3f, 1f / 3f)))
        val boundary = calculateDockGeometry(layout, contentSize, TestDockables.catalog).dividers.single { divider ->
            divider.target == DividerTarget.GroupBoundary(leftLaneId, 2)
        }

        val moved = layout.resizedBy(boundary, dragDistanceDp = 40f)

        assertEquals(296f, moved.groupHeight(Scenes), 0.01f)
        assertEquals(332f, moved.groupHeight(StreamInfo), 0.01f)
        assertEquals(256f, moved.groupHeight(Players), 0.01f)
    }
}
