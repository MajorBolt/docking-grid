package io.github.majorbolt.dockinggrid.persistence

import io.github.majorbolt.dockinggrid.CenterAxis
import io.github.majorbolt.dockinggrid.CenterPreset
import io.github.majorbolt.dockinggrid.DockFrameLevel
import io.github.majorbolt.dockinggrid.DockLayoutOperation
import io.github.majorbolt.dockinggrid.DockLocation
import io.github.majorbolt.dockinggrid.DockSide
import io.github.majorbolt.dockinggrid.DockStateTransitionResult
import io.github.majorbolt.dockinggrid.Dockable
import io.github.majorbolt.dockinggrid.DockableCatalog
import io.github.majorbolt.dockinggrid.DockableId
import io.github.majorbolt.dockinggrid.GroupHeaderStyle
import io.github.majorbolt.dockinggrid.DockingCapabilities
import io.github.majorbolt.dockinggrid.LayoutOrientation
import io.github.majorbolt.dockinggrid.PlacementMemory
import io.github.majorbolt.dockinggrid.WindowDockState
import io.github.majorbolt.dockinggrid.applyingOperation
import io.github.majorbolt.dockinggrid.closingDockable
import io.github.majorbolt.dockinggrid.matchingPreset
import io.github.majorbolt.dockinggrid.defaultWindowDockState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DockStateCodecTest {

    private val editor = DockableId("editor")
    private val explorer = DockableId("explorer")
    private val console = DockableId("console")
    private val catalog = DockableCatalog(
        listOf(
            Dockable(editor, "Editor", 300, 200, DockLocation.Center),
            Dockable(explorer, "Explorer", 200, 200, DockLocation.Side(DockFrameLevel.OUTER, DockSide.LEFT)),
            Dockable(console, "Console", 200, 120, DockLocation.Side(DockFrameLevel.OUTER, DockSide.BOTTOM)),
        ),
    )
    private val codec = DockStateCodec(catalog)
    private val desktop = DockingCapabilities.Desktop

    private fun WindowDockState.applyOrFail(operation: DockLayoutOperation, orientation: LayoutOrientation) =
        assertIs<DockStateTransitionResult.Applied>(applyingOperation(operation, orientation, desktop)).state

    /** Different arrangements per orientation, an inner dock, tabs and the placement memory of a closed dockable. */
    private fun customizedState(): WindowDockState = defaultWindowDockState(catalog, desktop)
        .applyOrFail(DockLayoutOperation.MoveToNewLane(explorer, DockFrameLevel.INNER, DockSide.RIGHT, 0), LayoutOrientation.LANDSCAPE)
        .applyOrFail(DockLayoutOperation.MoveToCenter(console, tabIndex = 0), LayoutOrientation.PORTRAIT)
        .closingDockable(console)

    @Test
    fun encodedStateDecodesToTheIdenticalState() {
        val originalState = customizedState()

        val decodeResult = codec.decode(codec.encode(originalState))

        val decoded = assertIs<DockStateDecodeResult.Decoded>(decodeResult)
        assertEquals(originalState, decoded.state)
        assertEquals(DockStateCodec.SCHEMA_VERSION, decoded.storedSchemaVersion)
        assertFalse(decoded.isNewerSchema)
        assertTrue(originalState.profile(LayoutOrientation.LANDSCAPE).placementMemory.containsKey(console), "Memory is part of the round trip")
    }

    @Test
    fun theDocumentUsesTheKeysOfSchemaFour() {
        val document = codec.encode(customizedState())

        assertFalse("ToolWindow" in document, "No key of schema 2 is written: $document")
        assertFalse("centerGroup" in document, "No key of schema 3 is written: $document")
        listOf("\"schemaVersion\":4", "\"center\"", "\"sections\"", "\"panels\"", "\"openDockableIds\"", "\"closedDockableIds\"", "\"dockableIds\"", "\"selectedDockableId\"")
            .forEach { storedKey -> assertTrue(storedKey in document, "Missing $storedKey in $document") }
    }

    @Test
    fun aDocumentOfTheCurrentFormatIsReadCompletely() {
        val document = """
            {
                "schemaVersion": 4,
                "openDockableIds": ["editor", "explorer"],
                "closedDockableIds": ["console"],
                "profiles": {
                    "LANDSCAPE": {
                        "layout": {
                            "outerFrame": { "left": [ { "id": "lane-1", "extentFraction": 0.3, "groups": [ { "id": "group-2", "dockableIds": ["explorer"] } ] } ] },
                            "center": { "sections": [ { "panels": [ { "id": "panel-1", "group": { "id": "group-1", "dockableIds": ["editor"], "selectedDockableId": "editor" } } ] } ] }
                        },
                        "placementMemory": {
                            "console": { "locationKind": "SIDE", "frameLevel": "OUTER", "side": "BOTTOM", "laneId": "lane-9", "laneExtentFraction": 0.2 }
                        }
                    }
                }
            }
        """.trimIndent()

        val state = assertIs<DockStateDecodeResult.Decoded>(codec.decode(document)).state

        assertEquals(setOf(editor, explorer), state.openDockableIds)
        val landscape = state.profile(LayoutOrientation.LANDSCAPE)
        val leftLane = landscape.layout.outerFrame.lanes(DockSide.LEFT).single()
        assertEquals(0.3f, leftLane.extentFraction)
        assertEquals(listOf(explorer), leftLane.groups.single().dockableIds)
        assertEquals(editor, landscape.layout.center.groups.single().selectedDockableId)
        assertEquals(
            PlacementMemory(DockLocation.Side(DockFrameLevel.OUTER, DockSide.BOTTOM), null, "lane-9", 0, 0, 0, 0.2f),
            landscape.placementMemory[console],
        )
    }

    @Test
    fun unknownIdentifiersAndOrientationsAreDroppedAndUnlistedDockablesCountAsOpen() {
        val document = """
            {
                "schemaVersion": 4,
                "openDockableIds": ["editor", "removed_dockable"],
                "closedDockableIds": ["explorer"],
                "profiles": {
                    "LANDSCAPE": { "layout": { "center": { "axis": "SOME_FUTURE_AXIS", "sections": [
                        { "panels": [ { "id": "a", "group": { "id": "center", "dockableIds": ["editor", "removed_dockable"] } } ] },
                        { "panels": [ { "id": "b", "group": { "id": "removed", "dockableIds": ["removed_dockable"] } } ] }
                    ] } } },
                    "SOME_FUTURE_ORIENTATION": {}
                }
            }
        """.trimIndent()

        val state = assertIs<DockStateDecodeResult.Decoded>(codec.decode(document)).state

        // The console is neither open nor closed in the document, as if it had been added in a newer version.
        assertEquals(setOf(editor, console), state.openDockableIds)
        assertEquals(setOf(LayoutOrientation.LANDSCAPE), state.profiles.keys)
        val center = state.profile(LayoutOrientation.LANDSCAPE).layout.center
        assertEquals(listOf(editor), center.groups.single().dockableIds)
        assertEquals(listOf("a"), center.panels.map { panel -> panel.id }, "A panel with only unknown dockables is dropped, not kept empty")
        assertEquals(CenterAxis.HORIZONTAL, center.axis)
    }

    @Test
    fun aNewerSchemaIsReadWithItsKnownFields() {
        val document = """{ "schemaVersion": 5, "futureField": 42, "openDockableIds": ["editor"], "closedDockableIds": ["explorer", "console"] }"""

        val decoded = assertIs<DockStateDecodeResult.Decoded>(codec.decode(document))

        assertEquals(setOf(editor), decoded.state.openDockableIds)
        assertEquals(5, decoded.storedSchemaVersion)
        assertTrue(decoded.isNewerSchema)
    }

    @Test
    fun anOutdatedSchemaIsNotMigrated() {
        // Schema 2, the format with Tool Window names, is not migrated before the first public release.
        val schemaTwoDocument = """{ "schemaVersion": 2, "openToolWindowIds": ["editor"], "closedToolWindowIds": ["explorer", "console"] }"""
        assertEquals(DockStateDecodeResult.OutdatedSchema(2), codec.decode(schemaTwoDocument))

        // Schema 3 had a single center group; it is not migrated either.
        val schemaThreeDocument = """
            { "schemaVersion": 3, "openDockableIds": ["editor"], "profiles": { "LANDSCAPE": { "layout": {
                "centerGroup": { "id": "group-1", "dockableIds": ["editor"] } } } } }
        """.trimIndent()
        assertEquals(DockStateDecodeResult.OutdatedSchema(3), codec.decode(schemaThreeDocument))
    }

    @Test
    fun aCenterGridWithEmptyPanelsAndResizedSplitsSurvivesTheRoundTrip() {
        val withGrid = defaultWindowDockState(catalog, desktop)
            .applyOrFail(DockLayoutOperation.ArrangeCenter(CenterPreset.THREE_FULL_LEFT), LayoutOrientation.LANDSCAPE)
            .applyOrFail(DockLayoutOperation.ResizeCenterSplit(sectionIndex = null, splitFraction = 0.3f), LayoutOrientation.LANDSCAPE)
            .applyOrFail(DockLayoutOperation.ResizeCenterSplit(sectionIndex = 1, splitFraction = 0.6f), LayoutOrientation.LANDSCAPE)
        val panelIds = withGrid.profile(LayoutOrientation.LANDSCAPE).layout.center.panelsInReadingOrder.map { panel -> panel.id }
        val state = withGrid.applyOrFail(DockLayoutOperation.MoveToCenterPanel(console, panelIds[2]), LayoutOrientation.LANDSCAPE)

        val decoded = assertIs<DockStateDecodeResult.Decoded>(codec.decode(codec.encode(state))).state

        assertEquals(state, decoded)
        val center = decoded.profile(LayoutOrientation.LANDSCAPE).layout.center
        assertEquals(CenterPreset.THREE_FULL_LEFT, center.matchingPreset)
        assertEquals(listOf(listOf(editor), emptyList(), listOf(console)), center.panelsInReadingOrder.map { panel -> panel.group?.dockableIds.orEmpty() })
        assertEquals(0.3f, center.splitFraction)
        assertEquals(0.6f, center.sections[1].splitFraction)
    }

    @Test
    fun headerStylesSurviveTheRoundTripAndDefaultWhenMissing() {
        val state = defaultWindowDockState(catalog, desktop)
        val explorerGroupId = state.profile(LayoutOrientation.LANDSCAPE).layout.findGroupOf(explorer)!!.id
        val styled = state
            .applyOrFail(DockLayoutOperation.SetGroupHeaderStyle(explorerGroupId, GroupHeaderStyle(isVertical = true, autoHides = true)), LayoutOrientation.LANDSCAPE)
            .applyOrFail(DockLayoutOperation.SetCenterHeaderAutoHide(true), LayoutOrientation.LANDSCAPE)

        val decoded = assertIs<DockStateDecodeResult.Decoded>(codec.decode(codec.encode(styled))).state
        assertEquals(styled, decoded)

        // Styles remembered per center position, and by an empty panel, survive storage as well.
        val centerPanelGroupId = styled.profile(LayoutOrientation.LANDSCAPE).layout.center.groups.single().id
        val withEmptyStyledPanel = styled
            .applyOrFail(DockLayoutOperation.ArrangeCenter(CenterPreset.SIDE_BY_SIDE), LayoutOrientation.LANDSCAPE)
            .applyOrFail(DockLayoutOperation.SetGroupHeaderStyle(centerPanelGroupId, GroupHeaderStyle(isVertical = true)), LayoutOrientation.LANDSCAPE)
            .applyOrFail(DockLayoutOperation.ArrangeCenter(CenterPreset.SINGLE), LayoutOrientation.LANDSCAPE)
            .applyOrFail(DockLayoutOperation.ArrangeCenter(CenterPreset.STACKED), LayoutOrientation.LANDSCAPE)
        assertEquals(
            withEmptyStyledPanel,
            assertIs<DockStateDecodeResult.Decoded>(codec.decode(codec.encode(withEmptyStyledPanel))).state,
        )

        // The style of a closed dock is remembered for reopening, also across storage.
        val closed = styled.closingDockable(explorer)
        val decodedClosed = assertIs<DockStateDecodeResult.Decoded>(codec.decode(codec.encode(closed))).state
        assertEquals(GroupHeaderStyle(isVertical = true, autoHides = true), decodedClosed.profile(LayoutOrientation.LANDSCAPE).placementMemory[explorer]?.headerStyle)

        // A schema-4 document written before header styles existed reads with the defaults.
        val olderDocument = """
            { "schemaVersion": 4, "openDockableIds": ["editor"], "profiles": { "LANDSCAPE": { "layout": { "center": { "sections": [
                { "panels": [ { "id": "panel-1", "group": { "id": "group-1", "dockableIds": ["editor"] } } ] } ] } } } } }
        """.trimIndent()
        val layout = assertIs<DockStateDecodeResult.Decoded>(codec.decode(olderDocument)).state.profile(LayoutOrientation.LANDSCAPE).layout
        assertEquals(GroupHeaderStyle(), layout.center.groups.single().headerStyle)
        assertFalse(layout.center.autoHidesHeader)
    }

    /**
     * A complete document as schema 2 wrote it: its tab groups use `toolWindowIds`, which the current format does not
     * accept. It must still be reported as outdated, not as unreadable, because the version is checked first.
     */
    @Test
    fun aCompleteSchemaTwoDocumentIsOutdatedNotUnreadable() {
        val schemaTwoDocument = """
            {
                "schemaVersion": 2,
                "openToolWindowIds": ["editor", "explorer"],
                "closedToolWindowIds": ["console"],
                "profiles": {
                    "LANDSCAPE": {
                        "layout": {
                            "outerFrame": { "left": [ { "id": "lane-1", "extentFraction": 0.25, "groups": [
                                { "id": "group-2", "toolWindowIds": ["explorer"], "selectedToolWindowId": "explorer", "sizeFraction": 1.0 }
                            ] } ] },
                            "centerGroup": { "id": "group-1", "toolWindowIds": ["editor"], "selectedToolWindowId": "editor" }
                        },
                        "placementMemory": {}
                    }
                }
            }
        """.trimIndent()

        assertEquals(DockStateDecodeResult.OutdatedSchema(2), codec.decode(schemaTwoDocument))
    }

    @Test
    fun invalidDocumentsAreReportedAsUnreadable() {
        listOf("{ broken", "[]", """{ "openDockableIds": ["editor"] }""", """{ "schemaVersion": "two" }""").forEach { document ->
            assertIs<DockStateDecodeResult.Unreadable>(codec.decode(document), "Document: $document")
        }
    }
}
