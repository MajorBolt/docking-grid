package io.github.majorbolt.dockinggrid.persistence

import io.github.majorbolt.dockinggrid.DockFrameLevel
import io.github.majorbolt.dockinggrid.DockLayoutOperation
import io.github.majorbolt.dockinggrid.DockLocation
import io.github.majorbolt.dockinggrid.DockSide
import io.github.majorbolt.dockinggrid.DockStateTransitionResult
import io.github.majorbolt.dockinggrid.Dockable
import io.github.majorbolt.dockinggrid.DockableCatalog
import io.github.majorbolt.dockinggrid.DockableId
import io.github.majorbolt.dockinggrid.DockingCapabilities
import io.github.majorbolt.dockinggrid.LayoutOrientation
import io.github.majorbolt.dockinggrid.PlacementMemory
import io.github.majorbolt.dockinggrid.WindowDockState
import io.github.majorbolt.dockinggrid.applyingOperation
import io.github.majorbolt.dockinggrid.closingDockable
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
    fun theDocumentUsesTheKeysOfSchemaThree() {
        val document = codec.encode(customizedState())

        assertFalse("ToolWindow" in document, "No key of schema 2 is written: $document")
        listOf("\"schemaVersion\":3", "\"openDockableIds\"", "\"closedDockableIds\"", "\"dockableIds\"", "\"selectedDockableId\"")
            .forEach { storedKey -> assertTrue(storedKey in document, "Missing $storedKey in $document") }
    }

    @Test
    fun aDocumentOfTheCurrentFormatIsReadCompletely() {
        val document = """
            {
                "schemaVersion": 3,
                "openDockableIds": ["editor", "explorer"],
                "closedDockableIds": ["console"],
                "profiles": {
                    "LANDSCAPE": {
                        "layout": {
                            "outerFrame": { "left": [ { "id": "lane-1", "extentFraction": 0.3, "groups": [ { "id": "group-2", "dockableIds": ["explorer"] } ] } ] },
                            "centerGroup": { "id": "group-1", "dockableIds": ["editor"], "selectedDockableId": "editor" }
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
        assertEquals(editor, landscape.layout.centerGroup?.selectedDockableId)
        assertEquals(
            PlacementMemory(DockLocation.Side(DockFrameLevel.OUTER, DockSide.BOTTOM), null, "lane-9", 0, 0, 0, 0.2f),
            landscape.placementMemory[console],
        )
    }

    @Test
    fun unknownIdentifiersAndOrientationsAreDroppedAndUnlistedDockablesCountAsOpen() {
        val document = """
            {
                "schemaVersion": 3,
                "openDockableIds": ["editor", "removed_dockable"],
                "closedDockableIds": ["explorer"],
                "profiles": {
                    "LANDSCAPE": { "layout": { "centerGroup": { "id": "center", "dockableIds": ["editor", "removed_dockable"] } } },
                    "SOME_FUTURE_ORIENTATION": {}
                }
            }
        """.trimIndent()

        val state = assertIs<DockStateDecodeResult.Decoded>(codec.decode(document)).state

        // The console is neither open nor closed in the document, as if it had been added in a newer version.
        assertEquals(setOf(editor, console), state.openDockableIds)
        assertEquals(setOf(LayoutOrientation.LANDSCAPE), state.profiles.keys)
        assertEquals(listOf(editor), state.profile(LayoutOrientation.LANDSCAPE).layout.centerGroup?.dockableIds)
    }

    @Test
    fun aNewerSchemaIsReadWithItsKnownFields() {
        val document = """{ "schemaVersion": 4, "futureField": 42, "openDockableIds": ["editor"], "closedDockableIds": ["explorer", "console"] }"""

        val decoded = assertIs<DockStateDecodeResult.Decoded>(codec.decode(document))

        assertEquals(setOf(editor), decoded.state.openDockableIds)
        assertEquals(4, decoded.storedSchemaVersion)
        assertTrue(decoded.isNewerSchema)
    }

    @Test
    fun anOutdatedSchemaIsNotMigrated() {
        // Schema 2, the format with Tool Window names, is not migrated before the first public release.
        val schemaTwoDocument = """{ "schemaVersion": 2, "openToolWindowIds": ["editor"], "closedToolWindowIds": ["explorer", "console"] }"""
        assertEquals(DockStateDecodeResult.OutdatedSchema(2), codec.decode(schemaTwoDocument))
    }

    @Test
    fun invalidDocumentsAreReportedAsUnreadable() {
        listOf("{ broken", "[]", """{ "openDockableIds": ["editor"] }""", """{ "schemaVersion": "two" }""").forEach { document ->
            assertIs<DockStateDecodeResult.Unreadable>(codec.decode(document), "Document: $document")
        }
    }
}
