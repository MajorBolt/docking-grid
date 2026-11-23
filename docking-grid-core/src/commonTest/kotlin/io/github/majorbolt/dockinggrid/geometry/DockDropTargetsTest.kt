package io.github.majorbolt.dockinggrid.geometry

import io.github.majorbolt.dockinggrid.DockFrameLevel
import io.github.majorbolt.dockinggrid.DockLayout
import io.github.majorbolt.dockinggrid.DockLayoutOperation
import io.github.majorbolt.dockinggrid.DockSide
import io.github.majorbolt.dockinggrid.DockStateTransitionResult
import io.github.majorbolt.dockinggrid.DockableId
import io.github.majorbolt.dockinggrid.DockingCapabilities
import io.github.majorbolt.dockinggrid.LayoutOrientation
import io.github.majorbolt.dockinggrid.TabGroup
import io.github.majorbolt.dockinggrid.TestCapabilities
import io.github.majorbolt.dockinggrid.TestDockables
import io.github.majorbolt.dockinggrid.TestDockables.AudioMixer
import io.github.majorbolt.dockinggrid.TestDockables.GameControls
import io.github.majorbolt.dockinggrid.TestDockables.Players
import io.github.majorbolt.dockinggrid.TestDockables.Scenes
import io.github.majorbolt.dockinggrid.TestDockables.StreamInfo
import io.github.majorbolt.dockinggrid.WindowDockState
import io.github.majorbolt.dockinggrid.applyingOperation
import io.github.majorbolt.dockinggrid.closingDockable
import io.github.majorbolt.dockinggrid.defaultWindowDockState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Uses the default layout in 1600 × 900 dp (see [DockGeometryTest]): LEFT lane 0..400 with Scenes (y 0..446)
 * above StreamInfo, center 408..1192, RIGHT lane 1200..1600 with Players above AudioMixer. With the default
 * [GroupHeaderMetrics] a single-window header is 36 dp high, so the body of Scenes is (0, 36, 400, 410).
 */
class DockDropTargetsTest {

    private val desktop = DockingCapabilities.Desktop
    private val landscape = LayoutOrientation.LANDSCAPE
    private val contentSize = DockSize(widthDp = 1600f, heightDp = 900f)
    private val defaultState = defaultWindowDockState(TestDockables.catalog, desktop)
    private val defaultLayout = defaultState.profile(landscape).layout

    private fun DockLayout.groupIdOf(dockableId: DockableId): String = allGroups.first { group -> dockableId in group.dockableIds }.id

    private fun DockLayout.laneIdOf(dockableId: DockableId): String =
        DockSide.entries.flatMap { side -> outerFrame.lanes(side) }.first { lane -> lane.groups.any { group -> dockableId in group.dockableIds } }.id

    private fun WindowDockState.applyOrFail(operation: DockLayoutOperation): WindowDockState =
        assertIs<DockStateTransitionResult.Applied>(applyingOperation(operation, landscape, desktop)).state

    private fun targetAt(
        horizontalPosition: Float,
        verticalPosition: Float,
        layout: DockLayout = defaultLayout,
        capabilities: DockingCapabilities = desktop,
        headerMetrics: GroupHeaderMetrics = GroupHeaderMetrics(),
    ): DropTarget? = resolveDropTarget(
        layout = layout,
        geometry = calculateDockGeometry(layout, contentSize, TestDockables.catalog),
        horizontalPosition = horizontalPosition,
        verticalPosition = verticalPosition,
        capabilities = capabilities,
        orientation = landscape,
        headerMetrics = headerMetrics,
    )

    private fun previewAt(state: WindowDockState, dockableId: DockableId, horizontalPosition: Float, verticalPosition: Float, size: DockSize = contentSize) =
        resolveDropPreview(
            profile = state.profile(landscape),
            dragBase = liftedDragBase(state.profile(landscape).layout, dockableId, size, TestDockables.catalog),
            horizontalPosition = horizontalPosition,
            verticalPosition = verticalPosition,
            capabilities = desktop,
            orientation = landscape,
            catalog = TestDockables.catalog,
        )

    private val stateWithoutRight = defaultState.closingDockable(Players).closingDockable(AudioMixer)

    @Test
    fun emptyRightSideIsDetectableWithSeparateOuterAndInnerBands() {
        val layout = stateWithoutRight.profile(landscape).layout

        assertEquals(DropTarget.NewLane(DockFrameLevel.OUTER, DockSide.RIGHT, 0), targetAt(1590f, 450f, layout))
        assertEquals(DropTarget.NewLane(DockFrameLevel.INNER, DockSide.RIGHT, 0), targetAt(1560f, 450f, layout))
    }

    @Test
    fun previewOfAnEmptyRightSideShowsTheNewLaneAndShrinksTheCenter() {
        val preview = assertNotNull(previewAt(stateWithoutRight, Scenes, horizontalPosition = 1590f, verticalPosition = 450f))

        assertEquals(DockRect(1200f, 0f, 400f, 900f), preview.highlightRect)
        // The remaining LEFT lane keeps its 400 dp; the center loses exactly the new lane plus its divider.
        assertEquals(DockRect(408f, 0f, 784f, 900f), preview.geometry.centerRect)
        assertTrue(preview.changesLayout)
    }

    @Test
    fun previewIsExactlyTheCommittedResult() {
        val preview = assertNotNull(previewAt(stateWithoutRight, Scenes, horizontalPosition = 1590f, verticalPosition = 450f))

        val committed = stateWithoutRight.applyOrFail(assertNotNull(preview.operation))

        assertEquals(preview.layout, committed.profile(landscape).layout)
    }

    @Test
    fun laneBodySplitsByHalvesAndCreatesLanesOnlyInTheEdgeBands() {
        // Scenes body (0, 36, 400, 410): bands 0..100 and 300..400, middle at y = 241.
        val leftLaneId = defaultLayout.outerFrame.lanes(DockSide.LEFT).single().id

        assertEquals(DropTarget.NewLane(DockFrameLevel.OUTER, DockSide.LEFT, 0), targetAt(10f, 200f), "outer band")
        assertEquals(DropTarget.NewLane(DockFrameLevel.OUTER, DockSide.LEFT, 1), targetAt(390f, 200f), "inner band")
        assertEquals(DropTarget.NewGroup(leftLaneId, 0, defaultLayout.groupIdOf(Scenes)), targetAt(200f, 60f), "upper half")
        assertEquals(DropTarget.NewGroup(leftLaneId, 1, defaultLayout.groupIdOf(Scenes)), targetAt(200f, 440f), "lower half")
        // Close to the middle of the body the result is always a split, never a new lane.
        assertEquals(DropTarget.NewGroup(leftLaneId, 0, defaultLayout.groupIdOf(Scenes)), targetAt(120f, 236f))
        assertEquals(DropTarget.NewGroup(leftLaneId, 1, defaultLayout.groupIdOf(Scenes)), targetAt(280f, 246f))
    }

    @Test
    fun rowLaneBodyIsTheRotatedRule() {
        val withTopRow = defaultState
            .applyOrFail(DockLayoutOperation.MoveToNewLane(Players, DockFrameLevel.OUTER, DockSide.TOP, 0))
        val topLaneId = withTopRow.profile(landscape).layout.outerFrame.lanes(DockSide.TOP).single().id
        val layout = withTopRow
            .applyOrFail(DockLayoutOperation.MoveToNewGroup(AudioMixer, topLaneId, 1))
            .profile(landscape).layout
        val playersRect = calculateDockGeometry(layout, contentSize, TestDockables.catalog).groupRects.getValue(layout.groupIdOf(Players))
        val bodyTop = playersRect.top + GroupHeaderMetrics().headerRowHeightDp
        val bodyHeight = playersRect.bottom - bodyTop
        val middleHeight = bodyTop + bodyHeight / 2f

        assertEquals(DropTarget.NewGroup(topLaneId, 0, layout.groupIdOf(Players)), targetAt(playersRect.left + playersRect.width * 0.4f, middleHeight, layout), "left half")
        assertEquals(DropTarget.NewGroup(topLaneId, 1, layout.groupIdOf(Players)), targetAt(playersRect.left + playersRect.width * 0.6f, middleHeight, layout), "right half")
        assertEquals(
            DropTarget.NewLane(DockFrameLevel.OUTER, DockSide.TOP, 0),
            targetAt(playersRect.left + playersRect.width * 0.5f, bodyTop + bodyHeight * 0.1f, layout),
            "top band: outer side",
        )
        assertEquals(
            DropTarget.NewLane(DockFrameLevel.OUTER, DockSide.TOP, 1),
            targetAt(playersRect.left + playersRect.width * 0.5f, playersRect.bottom - bodyHeight * 0.1f, layout),
            "bottom band: inner side",
        )
    }

    @Test
    fun laneBodySideMapsBandsAndHalves() {
        val rect = DockRect(0f, 0f, 400f, 200f)

        assertEquals(DockSide.LEFT, laneBodySide(rect, DockSide.RIGHT, 99f, 100f, bandShare = 0.25f))
        assertEquals(DockSide.TOP, laneBodySide(rect, DockSide.RIGHT, 100f, 99f, bandShare = 0.25f))
        assertEquals(DockSide.BOTTOM, laneBodySide(rect, DockSide.RIGHT, 299f, 100f, bandShare = 0.25f))
        assertEquals(DockSide.RIGHT, laneBodySide(rect, DockSide.RIGHT, 300f, 100f, bandShare = 0.25f))

        assertEquals(DockSide.TOP, laneBodySide(rect, DockSide.BOTTOM, 10f, 49f, bandShare = 0.25f))
        assertEquals(DockSide.LEFT, laneBodySide(rect, DockSide.BOTTOM, 199f, 50f, bandShare = 0.25f))
        assertEquals(DockSide.RIGHT, laneBodySide(rect, DockSide.BOTTOM, 200f, 149f, bandShare = 0.25f))
        assertEquals(DockSide.BOTTOM, laneBodySide(rect, DockSide.BOTTOM, 10f, 150f, bandShare = 0.25f))

        assertNull(laneBodySide(rect, DockSide.LEFT, 500f, 100f, bandShare = 0.25f))
        assertNull(laneBodySide(DockRect(0f, 0f, 0f, 200f), DockSide.LEFT, 0f, 100f, bandShare = 0.25f))
    }

    @Test
    fun triangleSidesAreDividedByTheDiagonals() {
        val rect = DockRect(0f, 0f, 400f, 200f)

        assertEquals(DockSide.LEFT, triangleSide(rect, 90f, 100f))
        assertEquals(DockSide.TOP, triangleSide(rect, 200f, 40f))
        assertEquals(DockSide.RIGHT, triangleSide(rect, 330f, 100f))
        assertEquals(DockSide.BOTTOM, triangleSide(rect, 200f, 160f))
        // On the diagonal from the top-left corner, top and left are equally close.
        assertEquals(DockSide.LEFT, triangleSide(rect, 100f, 50f))
        assertNull(triangleSide(rect, 500f, 100f))
        assertNull(triangleSide(DockRect(0f, 0f, 400f, 0f), 10f, 0f))
    }

    @Test
    fun rightSideBandsAreMirrored() {
        assertEquals(DropTarget.NewLane(DockFrameLevel.OUTER, DockSide.RIGHT, 0), targetAt(1590f, 200f))
        assertEquals(DropTarget.NewLane(DockFrameLevel.OUTER, DockSide.RIGHT, 1), targetAt(1210f, 200f))
    }

    @Test
    fun theHeaderWinsOverTheEmptyTopBandButNotOverTheWindowEdge() {
        val scenesGroupId = defaultLayout.groupIdOf(Scenes)

        // y = 20 lies in the empty outer TOP band (0..28); the single-window header (0..36) wins.
        // Its virtual tab is 4..94 (fallback width), so only its first half inserts before Scenes.
        assertEquals(DropTarget.Tab(scenesGroupId, 0), targetAt(30f, 20f))
        assertEquals(DropTarget.Tab(scenesGroupId, 1), targetAt(250f, 20f))
        // The outermost 8 dp still create the outer TOP dock.
        assertEquals(DropTarget.NewLane(DockFrameLevel.OUTER, DockSide.TOP, 0), targetAt(250f, 4f))
        // Below the center header the inner TOP band is still detectable.
        assertEquals(DropTarget.NewLane(DockFrameLevel.INNER, DockSide.TOP, 0), targetAt(800f, 40f))
    }

    @Test
    fun theHeaderOfAMultiTabGroupIncludesTheToolWindowHeaderRowAndUsesTabCenters() {
        val tabbedLayout = defaultState
            .applyOrFail(DockLayoutOperation.MoveToGroup(StreamInfo, defaultLayout.groupIdOf(Scenes), 1))
            .profile(landscape).layout
        val groupId = tabbedLayout.groupIdOf(Scenes)
        // Tabs: Scenes 4..124 (center 64), StreamInfo 124..264 (center 194); header = 48 + 36 = 84 dp.
        val headerMetrics = GroupHeaderMetrics(tabWidthsDp = mapOf(Scenes to 120f, StreamInfo to 140f))

        assertEquals(DropTarget.Tab(groupId, 0), targetAt(50f, 20f, tabbedLayout, headerMetrics = headerMetrics))
        assertEquals(DropTarget.Tab(groupId, 1), targetAt(100f, 20f, tabbedLayout, headerMetrics = headerMetrics))
        assertEquals(DropTarget.Tab(groupId, 2), targetAt(200f, 60f, tabbedLayout, headerMetrics = headerMetrics), "header row below the tabs")
        assertEquals(
            DropTarget.NewGroup(tabbedLayout.laneIdOf(Scenes), 0, tabbedLayout.groupIdOf(Scenes)),
            targetAt(200f, 100f, tabbedLayout, headerMetrics = headerMetrics),
            "the body starts below the header",
        )
    }

    @Test
    fun headerGeometryOfSingleAndMultiTabGroups() {
        val groupRect = DockRect(100f, 50f, 300f, 400f)
        val headerMetrics = GroupHeaderMetrics(tabWidthsDp = mapOf(Scenes to 120f))

        val single = TabGroup(id = "single", dockableIds = listOf(Scenes)).headerGeometry(groupRect, headerMetrics)
        assertEquals(DockRect(100f, 50f, 300f, 36f), single.rect)
        assertEquals(listOf(DockRect(104f, 50f, 120f, 36f)), single.tabRects, "the virtual tab of the single Tool Window")

        val multi = TabGroup(id = "multi", dockableIds = listOf(Scenes, Players)).headerGeometry(groupRect, headerMetrics)
        assertEquals(DockRect(100f, 50f, 300f, 84f), multi.rect)
        assertEquals(listOf(DockRect(104f, 50f, 120f, 48f), DockRect(224f, 50f, 90f, 48f)), multi.tabRects, "Players uses the fallback width")

        val tooSmall = TabGroup(id = "single", dockableIds = listOf(Scenes)).headerGeometry(groupRect.copy(height = 20f), headerMetrics)
        assertEquals(20f, tooSmall.rect.height)
    }

    @Test
    fun centerTrianglesCreateInnerLanesAndItsHeaderAddsATab() {
        val centerGroupId = defaultLayout.groupIdOf(GameControls)

        assertEquals(DropTarget.NewLane(DockFrameLevel.INNER, DockSide.LEFT, 0), targetAt(420f, 450f), "inner band")
        assertEquals(DropTarget.NewLane(DockFrameLevel.INNER, DockSide.LEFT, 0), targetAt(450f, 450f), "left triangle")
        assertEquals(DropTarget.NewLane(DockFrameLevel.INNER, DockSide.TOP, 0), targetAt(800f, 420f), "top triangle")
        assertEquals(DropTarget.Tab(centerGroupId, 1), targetAt(900f, 20f), "header")
    }

    @Test
    fun emptyCenterPlaceholderIsATarget() {
        val stateWithoutCenter = defaultState.closingDockable(GameControls)

        assertEquals(DropTarget.Center, targetAt(800f, 450f, stateWithoutCenter.profile(landscape).layout))
    }

    @Test
    fun dividersAreNotDropTargets() {
        assertNull(targetAt(404f, 450f))
    }

    @Test
    fun tabletHasNoInnerBandsAndCenterTrianglesTargetTheOuterFrame() {
        val tabletLayout = defaultWindowDockState(TestDockables.catalog, TestCapabilities.Tablet).profile(landscape).layout

        assertEquals(
            DropTarget.NewLane(DockFrameLevel.OUTER, DockSide.TOP, 0),
            targetAt(800f, 40f, tabletLayout, TestCapabilities.Tablet),
        )
        assertEquals(
            DropTarget.NewLane(DockFrameLevel.OUTER, DockSide.LEFT, 1),
            targetAt(420f, 450f, tabletLayout, TestCapabilities.Tablet),
        )
    }

    @Test
    fun nothingMovesWhenADragStartsAndTargetsAreWhereTheyAreDrawn() {
        // Regressions B3 and B6: RIGHT has a wide edge lane (AudioMixer) and an inner lane (Players).
        val withTwoRightLanes = defaultState.applyOrFail(DockLayoutOperation.MoveToNewLane(AudioMixer, DockFrameLevel.OUTER, DockSide.RIGHT, 0))
        val edgeLaneId = withTwoRightLanes.profile(landscape).layout.laneIdOf(AudioMixer)
        val state = withTwoRightLanes.applyOrFail(DockLayoutOperation.ResizeLane(edgeLaneId, 0.35f))
        val layout = state.profile(landscape).layout
        val playersLaneId = layout.laneIdOf(Players)
        val committedGeometry = calculateDockGeometry(layout, contentSize, TestDockables.catalog)

        // Lifting AudioMixer keeps its slot as a placeholder: the drag geometry is the committed geometry.
        assertEquals(committedGeometry, liftedDragBase(layout, AudioMixer, contentSize, TestDockables.catalog).geometry)
        val playersRect = committedGeometry.groupRects.getValue(layout.groupIdOf(Players))
        val audioMixerRect = committedGeometry.groupRects.getValue(layout.groupIdOf(AudioMixer))

        val originPreview = assertNotNull(previewAt(state, AudioMixer, audioMixerRect.left + audioMixerRect.width / 2, 450f))
        assertEquals(DropTarget.Origin, originPreview.target)
        assertFalse(originPreview.changesLayout)
        assertEquals(audioMixerRect, originPreview.highlightRect)

        val splitPreview = assertNotNull(previewAt(state, AudioMixer, playersRect.left + playersRect.width / 2, playersRect.top + 150f))
        assertEquals(DropTarget.NewGroup(playersLaneId, 0, layout.groupIdOf(Players)), splitPreview.target)
        assertTrue(splitPreview.changesLayout)
        assertEquals(splitPreview.layout, state.applyOrFail(assertNotNull(splitPreview.operation)).profile(landscape).layout)

        val tabPreview = assertNotNull(previewAt(state, AudioMixer, playersRect.right - 2f, playersRect.top + 20f))
        assertEquals(DropTarget.Tab(layout.groupIdOf(Players), 1), tabPreview.target)
        assertEquals(listOf(Players, AudioMixer), tabPreview.layout.findGroup(layout.groupIdOf(Players))?.dockableIds)
    }

    @Test
    fun laneIndicesSkipTheLiftedPlaceholderLane() {
        // LEFT: [edge lane: AudioMixer], [lane: Scenes above StreamInfo]. Lifting AudioMixer keeps its lane as a
        // placeholder at index 0; the outer side of the Scenes lane is index 0 of the remaining lanes, not 1.
        val withTwoLeftLanes = defaultState.applyOrFail(DockLayoutOperation.MoveToNewLane(AudioMixer, DockFrameLevel.OUTER, DockSide.LEFT, 0))
        val layout = withTwoLeftLanes.profile(landscape).layout
        val scenesRect = calculateDockGeometry(layout, contentSize, TestDockables.catalog).groupRects.getValue(layout.groupIdOf(Scenes))

        val outerPreview = assertNotNull(previewAt(withTwoLeftLanes, AudioMixer, scenesRect.left + 4f, scenesRect.top + 200f))
        assertEquals(DropTarget.NewLane(DockFrameLevel.OUTER, DockSide.LEFT, 0), outerPreview.target)
        assertFalse(outerPreview.changesLayout, "AudioMixer goes back to the edge, where it was")

        val innerPreview = assertNotNull(previewAt(withTwoLeftLanes, AudioMixer, scenesRect.right - 4f, scenesRect.top + 200f))
        assertEquals(DropTarget.NewLane(DockFrameLevel.OUTER, DockSide.LEFT, 1), innerPreview.target)
        assertEquals(
            listOf(listOf(Scenes, StreamInfo), listOf(AudioMixer)),
            innerPreview.layout.outerFrame.lanes(DockSide.LEFT).map { lane -> lane.groups.flatMap { group -> group.dockableIds } },
        )
    }

    @Test
    fun mergingByHeaderPreviewKeepsTheUntouchedGroup() {
        // Regressions B5 and B6: LEFT holds Scenes, StreamInfo and Players, a third each. Lifting Players moves
        // nothing, so the StreamInfo header is where it is drawn; dropping on it merges and leaves Scenes untouched.
        val leftLaneId = defaultLayout.outerFrame.lanes(DockSide.LEFT).single().id
        val state = defaultState
            .applyOrFail(DockLayoutOperation.MoveToNewGroup(Players, leftLaneId, 2))
            .applyOrFail(DockLayoutOperation.ResizeGroups(leftLaneId, listOf(1f / 3f, 1f / 3f, 1f / 3f)))
        val layout = state.profile(landscape).layout
        val streamInfoGroupId = layout.groupIdOf(StreamInfo)
        val streamInfoRect = calculateDockGeometry(layout, contentSize, TestDockables.catalog).groupRects.getValue(streamInfoGroupId)

        val preview = assertNotNull(previewAt(state, Players, horizontalPosition = 300f, verticalPosition = streamInfoRect.top + 20f))

        assertEquals(DropTarget.Tab(streamInfoGroupId, 1), preview.target)
        val previewLane = preview.layout.outerFrame.lanes(DockSide.LEFT).single()
        assertEquals(listOf(listOf(Scenes), listOf(StreamInfo, Players)), previewLane.groups.map { group -> group.dockableIds })
        assertEquals(1f / 3f, previewLane.groups[0].sizeFraction, 0.0001f)
        assertEquals(2f / 3f, previewLane.groups[1].sizeFraction, 0.0001f)
        assertEquals(preview.layout, state.applyOrFail(assertNotNull(preview.operation)).profile(landscape).layout)
    }

    @Test
    fun droppingIntoTheOwnPlaceholderChangesNothing() {
        // Lifting Scenes keeps its slot (0, 0, 400, 446) as a placeholder.
        val preview = assertNotNull(previewAt(defaultState, Scenes, horizontalPosition = 200f, verticalPosition = 200f))

        assertEquals(DropTarget.Origin, preview.target)
        assertNull(preview.operation)
        assertFalse(preview.changesLayout)
        assertEquals(DockRect(0f, 0f, 400f, 446f), preview.highlightRect)
        assertEquals(defaultLayout, preview.layout)
    }

    @Test
    fun aTargetThatRestoresTheArrangementIsShownButChangesNothing() {
        // Lifting StreamInfo: the lower half of Scenes puts it back below Scenes.
        val preview = assertNotNull(previewAt(defaultState, StreamInfo, horizontalPosition = 200f, verticalPosition = 400f))

        assertEquals(DropTarget.NewGroup(defaultLayout.outerFrame.lanes(DockSide.LEFT).single().id, 1, defaultLayout.groupIdOf(Scenes)), preview.target)
        assertFalse(preview.changesLayout)
    }

    @Test
    fun dropsThatWouldNotFitTheMinimumSizesAreNotOffered() {
        // 900 dp fits the default layout (needs 836 dp) but not a third column (needs 1064 dp).
        // In both sizes the pointer is in the inner triangle of the Scenes group.
        val narrowSize = DockSize(widthDp = 900f, heightDp = 900f)
        assertNull(previewAt(defaultState, AudioMixer, horizontalPosition = 230f, verticalPosition = 200f, size = narrowSize))
        assertNotNull(previewAt(defaultState, AudioMixer, horizontalPosition = 390f, verticalPosition = 200f))
    }

    @Test
    fun whenTheWindowIsAlreadyTooSmallDropsThatNeedNoMoreSpaceAreStillAllowed() {
        val tooSmall = DockSize(widthDp = 700f, heightDp = 900f)

        // Moving AudioMixer into the Players tab group adds nothing to the width requirement.
        val playersRect = calculateDockGeometry(defaultLayout, tooSmall, TestDockables.catalog).groupRects.getValue(defaultLayout.groupIdOf(Players))
        val preview = previewAt(
            defaultState,
            AudioMixer,
            horizontalPosition = playersRect.right - 2f,
            verticalPosition = playersRect.top + 20f,
            size = tooSmall,
        )

        assertEquals(DropTarget.Tab(defaultLayout.groupIdOf(Players), 1), assertNotNull(preview).target)
    }
}
