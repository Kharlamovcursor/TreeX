package ru.hdlabs.treex.domain.layout

import ru.hdlabs.treex.domain.graph.Family
import ru.hdlabs.treex.domain.graph.GenealogyGraph
import ru.hdlabs.treex.domain.graph.GenealogyGraphMapper
import ru.hdlabs.treex.domain.graph.Gender
import ru.hdlabs.treex.domain.graph.Person

/** 3 generations, 2 marriages, 5 children, 1 OpenLink family, 1 legacy family. */
object LayoutTestFixtures {

    fun integratedGraph(): GenealogyGraph {
        val people = listOf(
            Person("gp1", "GP1", Gender.MALE),
            Person("gp2", "GP2", Gender.FEMALE),
            Person("p1", "P1", Gender.MALE),
            Person("p1s", "P1S", Gender.FEMALE),
            Person("p2", "P2", Gender.MALE),
            Person("p2s", "P2S", Gender.FEMALE),
            Person("c1", "C1", Gender.MALE),
            Person("c2", "C2", Gender.FEMALE),
            Person("c3", "C3", Gender.MALE),
            Person("c4", "C4", Gender.FEMALE),
            Person("c5", "C5", Gender.MALE),
            Person("open", "Open", Gender.FEMALE),
            Person("openChild", "OpenChild", Gender.MALE),
            Person("leg", "Leg", Gender.MALE),
        )
        val famMap = linkedMapOf(
            "gf" to Family("gf", "gp1", "gp2", listOf("p1", "p2")),
            "f1" to Family("f1", "p1", "p1s", listOf("c1", "c2", "c3")),
            "f2" to Family("f2", "p2", "p2s", listOf("c4", "c5")),
            "open-f" to Family("open-f", null, "open", listOf("openChild")),
            "legacy-leg" to Family("legacy-leg", "gp1", null, listOf("leg")),
        )
        return GenealogyGraph(people.associateBy { it.id }, famMap)
    }
}
