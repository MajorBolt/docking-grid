package io.github.majorbolt.dockinggrid.compose

import io.github.majorbolt.dockinggrid.DockFrameLevel
import io.github.majorbolt.dockinggrid.DockLayoutOperation
import io.github.majorbolt.dockinggrid.DockOperationResult
import io.github.majorbolt.dockinggrid.DockProfile
import io.github.majorbolt.dockinggrid.DockSide
import io.github.majorbolt.dockinggrid.DockingCapabilities
import io.github.majorbolt.dockinggrid.LayoutOrientation
import io.github.majorbolt.dockinggrid.applyOperation
import io.github.majorbolt.dockinggrid.closingDockable
import io.github.majorbolt.dockinggrid.defaultWindowDockState
import io.github.majorbolt.dockinggrid.geometry.DividerTarget
import io.github.majorbolt.dockinggrid.geometry.DockRect
import io.github.majorbolt.dockinggrid.geometry.DockSize
import io.github.majorbolt.dockinggrid.geometry.DockingMetrics
import io.github.majorbolt.dockinggrid.geometry.DropTarget
import io.github.majorbolt.dockinggrid.geometry.GroupHeaderMetrics
import io.github.majorbolt.dockinggrid.geometry.calculateDockGeometry
import io.github.majorbolt.dockinggrid.geometry.tabInsertionIndex
import io.github.majorbolt.dockinggrid.testing.TestDockables
import io.github.majorbolt.dockinggrid.testing.TestDockables.AudioMixer
import io.github.majorbolt.dockinggrid.testing.TestDockables.Players
import io.github.majorbolt.dockinggrid.testing.TestDockables.Scenes
import io.github.majorbolt.dockinggrid.testing.TestDockables.StreamInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Default layout without the RIGHT dock, 1600 × 900 dp. */
class DockInteractionTest {

    private val contentSize = DockSize(widthDp = 1600f, heightDp = 900f)
    private val profileWithoutRight: DockProfile = defaultWindowDockState(TestDockables.catalog, DockingCapabilities.Desktop)
        .closingDockable(Players)
        .closingDockable(AudioMixer)
        .profile(LayoutOrientation.LANDSCAPE)

    private fun environmentFor(profile: DockProfile) = DockInteractionEnvironment(
        committedProfile = profile,
        committedGeometry = calculateDockGeometry(profile.layout, contentSize, TestDockables.catalog),
        capabilities = DockingCapabilities.Desktop,
        orientation = LayoutOrientation.LANDSCAPE,
        catalog = TestDockables.catalog,
        metrics = DockingMetrics(),
        headerMetrics = GroupHeaderMetrics(),
    )

    @Test
    fun dragOverAnEmptyEdgeShowsTheTargetOnlyAndDropEmitsThePreviewedOperation() {
        val environment = environmentFor(profileWithoutRight)

        val dragging = startDockableDrag(Scenes, 200f, 200f, environment).movedTo(1590f, 450f, environment)

        assertEquals(DockRect(1200f, 0f, 400f, 900f), dragging.highlightRect)
        val (operation, afterDrop) = dragging.finished()
        assertEquals(DockLayoutOperation.MoveToNewLane(Scenes, DockFrameLevel.OUTER, DockSide.RIGHT, 0), operation)
        val awaitingCommit = assertIs<DockInteraction.AwaitingCommit>(afterDrop)
        assertEquals(dragging.preview?.layout, awaitingCommit.displayedLayout)
    }

    @Test
    fun nothingMovesWhenADragStartsAndTheDisplayedLayoutFollowsThePointer() {
        val environment = environmentFor(profileWithoutRight)
        val dragging = startDockableDrag(Scenes, 200f, 200f, environment)

        val overDivider = dragging.movedTo(404f, 450f, environment)
        val overRightEdge = overDivider.movedTo(1590f, 450f, environment)
        val backOverItsPlaceholder = overRightEdge.movedTo(200f, 200f, environment)

        // Over no target the committed layout stays in place; Scenes is shown as its placeholder (lifted).
        assertEquals(profileWithoutRight.layout, overDivider.displayedLayout(profileWithoutRight.layout))
        assertEquals(Scenes, overDivider.liftedDockableId)
        assertNull(overDivider.highlightRect)
        // Hovering a real target shows its preview; the window is no longer shown as lifted.
        assertEquals(1, overRightEdge.displayedLayout(profileWithoutRight.layout).outerFrame.lanes(DockSide.RIGHT).size)
        assertNull(overRightEdge.liftedDockableId)
        // The own placeholder is highlighted, but dropping there commits nothing.
        assertEquals(DropTarget.Origin, backOverItsPlaceholder.preview?.target)
        assertEquals(Scenes, backOverItsPlaceholder.liftedDockableId)
        assertEquals(DockRect(0f, 0f, 400f, 446f), backOverItsPlaceholder.highlightRect)
        assertEquals(null to DockInteraction.Idle, backOverItsPlaceholder.finished())
        assertEquals(profileWithoutRight, environment.committedProfile)
    }

    @Test
    fun dropWithoutTargetEmitsNothing() {
        val environment = environmentFor(profileWithoutRight)
        val overDivider = startDockableDrag(Scenes, 200f, 200f, environment).movedTo(404f, 450f, environment)

        assertEquals(null to DockInteraction.Idle, overDivider.finished())
    }

    @Test
    fun cancelRestoresTheCommittedLayoutExactly() {
        val environment = environmentFor(profileWithoutRight)
        val dragging = startDockableDrag(Scenes, 200f, 200f, environment).movedTo(1590f, 450f, environment)

        val cancelled = dragging.cancelled()

        assertEquals(DockInteraction.Idle, cancelled)
        assertEquals(profileWithoutRight.layout, cancelled.displayedLayout(profileWithoutRight.layout))
    }

    @Test
    fun aChangeOfTheCommittedProfileCancelsARunningDragAndCompletesAnAwaitedCommit() {
        val environment = environmentFor(profileWithoutRight)
        val dragging = startDockableDrag(Scenes, 200f, 200f, environment).movedTo(1590f, 450f, environment)
        val changedProfile = defaultWindowDockState(TestDockables.catalog, DockingCapabilities.Desktop).profile(LayoutOrientation.LANDSCAPE)

        assertEquals(dragging, dragging.afterCommittedProfileChanged(profileWithoutRight))
        assertEquals(DockInteraction.Idle, dragging.afterCommittedProfileChanged(changedProfile))

        val awaitingCommit = dragging.finished().second
        assertEquals(DockInteraction.Idle, awaitingCommit.afterCommittedProfileChanged(changedProfile))
        assertEquals(awaitingCommit, awaitingCommit.cancelled(), "An awaited commit is not cancellable")
    }

    @Test
    fun dividerResizePreviewsAndCommitsOneClampedOperation() {
        val environment = environmentFor(profileWithoutRight)
        val leftLaneId = profileWithoutRight.layout.outerFrame.lanes(DockSide.LEFT).single().id
        val divider = environment.committedGeometry.dividers.single { candidate ->
            candidate.target == DividerTarget.LaneExtent(leftLaneId, DockFrameLevel.OUTER, DockSide.LEFT)
        }

        val resizing = startDividerResize(divider, profileWithoutRight)
            .draggedTo(50f, environment)
            .draggedTo(100f, environment)

        val previewLane = resizing.previewLayout.outerFrame.lanes(DockSide.LEFT).single()
        assertEquals(500f / 1600f, previewLane.extentFraction, 0.0001f)
        val (operation, afterRelease) = resizing.finished()
        assertEquals(DockLayoutOperation.ResizeLane(leftLaneId, 500f / 1600f), operation)
        assertIs<DockInteraction.AwaitingCommit>(afterRelease)
    }

    @Test
    fun resizeWithoutMovementEmitsNothingAndCancelRestores() {
        val environment = environmentFor(profileWithoutRight)
        val divider = environment.committedGeometry.dividers.first()
        val resizing = startDividerResize(divider, profileWithoutRight)

        assertEquals(null to DockInteraction.Idle, resizing.finished())
        assertEquals(profileWithoutRight.layout, resizing.draggedTo(80f, environment).cancelled().displayedLayout(profileWithoutRight.layout))
    }

    @Test
    fun tabInsertionIndexUsesTabCenters() {
        val tabs = listOf(DockRect(0f, 0f, 100f, 36f), DockRect(100f, 0f, 80f, 36f), DockRect(180f, 0f, 120f, 36f))

        assertEquals(0, tabInsertionIndex(tabs, 20f))
        assertEquals(1, tabInsertionIndex(tabs, 60f))
        assertEquals(2, tabInsertionIndex(tabs, 150f))
        assertEquals(3, tabInsertionIndex(tabs, 290f))
        assertEquals(0, tabInsertionIndex(emptyList(), 60f))
    }

    @Test
    fun hoveringATabStripTargetsTheExactInsertionIndex() {
        val profile = defaultWindowDockState(TestDockables.catalog, DockingCapabilities.Desktop).profile(LayoutOrientation.LANDSCAPE)
        val centerGroupId = assertNotNull(profile.layout.center.groups.singleOrNull()).id
        // The center group (408, 0, 784, 900) has a single header (y 0..36); its virtual tab is 412..502.
        val environment = environmentFor(profile)

        val dragging = startDockableDrag(Scenes, 200f, 200f, environment).movedTo(420f, 20f, environment)

        assertEquals(DockLayoutOperation.MoveToGroup(Scenes, centerGroupId, 0), dragging.preview?.operation)
    }

    @Test
    fun draggingATabOverItsOwnStripReordersTheTabsInsteadOfSplitting() {
        // Regression B2: Scenes and StreamInfo share the LEFT group. Lifting Scenes leaves StreamInfo alone; behind
        // the center of its (virtual) tab is index 1.
        val defaultProfile = defaultWindowDockState(TestDockables.catalog, DockingCapabilities.Desktop).profile(LayoutOrientation.LANDSCAPE)
        val scenesGroupId = defaultProfile.layout.allGroups.first { group -> Scenes in group.dockableIds }.id
        val mergedLayout = assertIs<DockOperationResult.Applied>(
            defaultProfile.applyOperation(DockLayoutOperation.MoveToGroup(StreamInfo, scenesGroupId, 1), LayoutOrientation.LANDSCAPE, DockingCapabilities.Desktop),
        ).layout
        val profile = defaultProfile.copy(layout = mergedLayout)
        val environment = environmentFor(profile)

        val dragging = startDockableDrag(Scenes, 60f, 20f, environment).movedTo(230f, 20f, environment)

        assertEquals(DockLayoutOperation.MoveToGroup(Scenes, scenesGroupId, 1), dragging.preview?.operation)
        assertEquals(listOf(StreamInfo, Scenes), dragging.preview?.layout?.findGroup(scenesGroupId)?.dockableIds)
        assertEquals(DockLayoutOperation.MoveToGroup(Scenes, scenesGroupId, 1), dragging.finished().first)
    }

    @Test
    fun aClosedDockableLiftsNothingAndADropInsertsIt() {
        val environment = environmentFor(profileWithoutRight)

        val dragging = startDockableDrag(Players, 800f, 450f, environment, isExternal = true)
        assertEquals(profileWithoutRight.layout, dragging.dragBase.layout, "Nothing is lifted out of the layout")
        assertNull(dragging.dragBase.sourceGroupId)

        val overRightEdge = dragging.movedTo(1590f, 450f, environment)

        assertEquals(DockRect(1200f, 0f, 400f, 900f), overRightEdge.highlightRect)
        assertEquals(DockLayoutOperation.MoveToNewLane(Players, DockFrameLevel.OUTER, DockSide.RIGHT, 0), overRightEdge.finished().first)
    }

    @Test
    fun anExternalDragOutsideTheContentHasNoTargetAndDropsNothing() {
        val environment = environmentFor(profileWithoutRight)

        val outside = startDockableDrag(Players, 800f, 450f, environment, isExternal = true).movedTo(-20f, 450f, environment)

        assertNull(outside.preview)
        assertEquals(null to DockInteraction.Idle, outside.finished())
    }
}
