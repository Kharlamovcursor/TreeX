package ru.hdlabs.treex.domain
import org.junit.Test
import org.junit.Assert.*
import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.data.local.UnionEntity

class AttachFlowDebugTest {
    @Test fun fatherMotherSon() {
        val self = PersonEntity(id="s", gender="m", firstName="Son")
        val father = PersonEntity(id="f", gender="m", firstName="Dad")
        val mother = PersonEntity(id="m", gender="f", firstName="Mom")
        var unions = UnionErAttach.applyRelativeAttach(listOf(self), emptyList(), "s", "father", father)
        val people = listOf(self, father)
        unions = UnionErAttach.applyRelativeAttach(people, unions, "s", "mother", mother)
        val all = listOf(self, father, mother)
        val compact = UnionEr.compactUnions(all, unions)
        val parents = UnionEr.parentIdsOf(self, all.map{it.id}.toSet(), compact)
        assertTrue("parents=$parents unions=$compact", parents.contains("f"))
        val placed = TreeLayout.place(all, compact, homePersonId="s")
        assertTrue("groups=${placed.childGroups}", placed.childGroups.any { "s" in it.childIds })
    }

    @Test
    fun savePipelineMergeInferred() {
        val self = PersonEntity(id = "s", gender = "m", firstName = "Son")
        val father = PersonEntity(id = "f", gender = "m", firstName = "Dad")
        val afterAttach = UnionErAttach.applyRelativeAttach(listOf(self), emptyList(), "s", "father", father)
        val merged = mergeInferredParentUnions(afterAttach, listOf(self, father))
        assertTrue("merged=$merged", merged.any { "s" in it.childIdsList() && "f" in it.partnerIds() })
    }
}
