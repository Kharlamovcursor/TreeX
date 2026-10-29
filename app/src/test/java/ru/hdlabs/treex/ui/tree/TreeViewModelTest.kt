package ru.hdlabs.treex.ui.tree

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.hdlabs.treex.domain.layout.LayoutTestFixtures
import ru.hdlabs.treex.domain.layout.LayoutValidator

class TreeViewModelTest {

    @Test
    fun loadProducesValidLayout() {
        val ready = TreeReadyBuilder.build(LayoutTestFixtures.integratedGraph())
        val result = LayoutValidator.validate(ready.layout, ready.graph)
        assertTrue(result.errors.toString(), result.errors.isEmpty())
    }

    @Test
    fun transformScaleClamped() {
        val t = Gestures.clampTransform(
            CanvasTransform(10f, 0f, 0f),
            ru.hdlabs.treex.domain.layout.Bounds(0f, 0f, 100f, 100f),
            200f,
            200f,
        )
        assertEquals(Gestures.MAX_SCALE, t.scale, 0.01f)
    }

    @Test
    fun readyStateCopyForPersonSelection() {
        val ready = TreeReadyBuilder.build(LayoutTestFixtures.integratedGraph())
        val next = ready.copy(
            selectedPersonId = "c1",
            sheet = BottomSheetState.PersonDetails("c1"),
        )
        assertEquals("c1", next.selectedPersonId)
        assertTrue(next.sheet is BottomSheetState.PersonDetails)
    }

    @Test
    fun slotClickSheetState() {
        val ready = TreeReadyBuilder.build(LayoutTestFixtures.integratedGraph())
        val slot = ready.openLinks.firstOrNull() ?: OpenLinkSlotState(
            "s", "f1", "p1", RelativeKind.SPOUSE, 0f, 0f, 90f, 80f,
        )
        val next = ready.copy(
            sheet = BottomSheetState.AddRelative(slot.anchorPersonId, slot.kind),
        )
        assertTrue(next.sheet is BottomSheetState.AddRelative)
    }

    @Test
    fun deterministicLayoutRebuild() {
        val graph = LayoutTestFixtures.integratedGraph()
        assertEquals(
            TreeReadyBuilder.build(graph).layout,
            TreeReadyBuilder.build(graph).layout,
        )
    }
}
