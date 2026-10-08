package com.autoflow.app

import com.autoflow.app.ui.builder.ActionListRef
import com.autoflow.app.ui.builder.ActionTree
import com.autoflow.app.ui.builder.Branch
import com.autoflow.app.ui.builder.ConditionTree
import com.autoflow.core.model.ActionSpec
import com.autoflow.core.model.Comparison
import com.autoflow.core.model.ConditionNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TreeOpsTest {
    private val battery = ConditionNode.BatteryLevel(Comparison.GREATER_THAN, 30)
    private val charging = ConditionNode.Charging(true)

    @Test
    fun conditionTreeEditing() {
        var root: ConditionNode = ConditionNode.And(emptyList())
        root = ConditionTree.addChild(root, emptyList(), battery)
        root = ConditionTree.addChild(root, emptyList(), ConditionNode.Or(emptyList()))
        root = ConditionTree.addChild(root, listOf(1), charging)
        root = ConditionTree.toggleNot(root, listOf(1, 0))
        assertEquals(
            ConditionNode.And(listOf(battery, ConditionNode.Or(listOf(ConditionNode.Not(charging))))),
            root,
        )
        root = ConditionTree.toggleGroupType(root, emptyList())
        assertEquals(ConditionNode.Or::class, root::class)

        // Removing the child of a NOT removes the NOT itself.
        root = ConditionTree.remove(root, listOf(1, 0, 0))
        assertEquals(ConditionNode.Or(listOf(battery, ConditionNode.Or(emptyList()))), root)
        assertEquals(ConditionNode.Or(listOf(battery)), ConditionTree.normalize(root))
        assertNull(ConditionTree.normalize(ConditionNode.And(listOf(ConditionNode.Or(emptyList())))))
    }

    @Test
    fun nestedActionEditing() {
        val ifElse = ActionSpec.IfElse(charging, thenActions = emptyList())
        var actions: List<ActionSpec> = listOf(ActionSpec.Delay(1), ifElse, ActionSpec.Repeat(2, emptyList()))
        val thenRef = ActionListRef().child(1, Branch.THEN)
        actions = ActionTree.add(actions, thenRef, ActionSpec.Vibrate())
        actions = ActionTree.add(actions, thenRef, ActionSpec.Speak("hi"))
        actions = ActionTree.add(actions, ActionListRef().child(2, Branch.BODY), ActionSpec.Delay(5))
        actions = ActionTree.move(actions, thenRef, 0, 1)
        assertEquals(listOf(ActionSpec.Speak("hi"), ActionSpec.Vibrate()), ActionTree.list(actions, thenRef))

        // Editing the If/Else condition keeps its branches.
        actions = ActionTree.replaceKeepingChildren(actions, ActionListRef(), 1, ifElse.copy(condition = battery))
        assertEquals(2, (actions[1] as ActionSpec.IfElse).thenActions.size)

        actions = ActionTree.remove(actions, thenRef, 0)
        assertEquals(listOf(ActionSpec.Vibrate()), ActionTree.list(actions, thenRef))
        assertEquals(listOf(ActionSpec.Delay(5)), (actions[2] as ActionSpec.Repeat).actions)
        actions = ActionTree.move(actions, ActionListRef(), 2, 0)
        assertEquals(ActionSpec.Repeat::class, actions[0]::class)
    }
}
