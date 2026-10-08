package com.autoflow.app.ui.builder

import com.autoflow.core.model.ActionSpec
import com.autoflow.core.model.ConditionNode

/** Path of child indexes from the root group to a node. Empty path = root. */
typealias NodePath = List<Int>

/** Immutable edit operations on condition trees (AND / OR / NOT groups with leaves). */
object ConditionTree {
    fun children(node: ConditionNode): List<ConditionNode> = when (node) {
        is ConditionNode.And -> node.children
        is ConditionNode.Or -> node.children
        is ConditionNode.Not -> listOf(node.child)
        else -> emptyList()
    }

    private fun withChildren(node: ConditionNode, children: List<ConditionNode>): ConditionNode = when (node) {
        is ConditionNode.And -> node.copy(children = children)
        is ConditionNode.Or -> node.copy(children = children)
        is ConditionNode.Not -> children.singleOrNull()?.let { node.copy(child = it) } ?: node
        else -> node
    }

    fun get(root: ConditionNode, path: NodePath): ConditionNode =
        path.fold(root) { node, index -> children(node)[index] }

    fun replace(root: ConditionNode, path: NodePath, replacement: ConditionNode): ConditionNode {
        if (path.isEmpty()) return replacement
        val index = path.first()
        val kids = children(root).toMutableList()
        kids[index] = replace(kids[index], path.drop(1), replacement)
        return withChildren(root, kids)
    }

    /** Removes the node at [path]. Removing the only child of a NOT removes the NOT too. */
    fun remove(root: ConditionNode, path: NodePath): ConditionNode {
        require(path.isNotEmpty()) { "Cannot remove the root" }
        val parentPath = path.dropLast(1)
        val parent = get(root, parentPath)
        if (parent is ConditionNode.Not) return remove(root, parentPath)
        val kids = children(parent).toMutableList().apply { removeAt(path.last()) }
        return replace(root, parentPath, withChildren(parent, kids))
    }

    /** Appends [child] to the AND / OR group at [groupPath]. */
    fun addChild(root: ConditionNode, groupPath: NodePath, child: ConditionNode): ConditionNode {
        val group = get(root, groupPath)
        require(group is ConditionNode.And || group is ConditionNode.Or) { "Not a group" }
        return replace(root, groupPath, withChildren(group, children(group) + child))
    }

    /** Switches a group between AND and OR. */
    fun toggleGroupType(root: ConditionNode, path: NodePath): ConditionNode = when (val node = get(root, path)) {
        is ConditionNode.And -> replace(root, path, ConditionNode.Or(node.children))
        is ConditionNode.Or -> replace(root, path, ConditionNode.And(node.children))
        else -> root
    }

    /** Wraps the node in NOT, or unwraps it if it already is a NOT. */
    fun toggleNot(root: ConditionNode, path: NodePath): ConditionNode = when (val node = get(root, path)) {
        is ConditionNode.Not -> replace(root, path, node.child)
        else -> replace(root, path, ConditionNode.Not(node))
    }

    /** Drops empty groups; returns null when nothing is left (meaning "always true"). */
    fun normalize(node: ConditionNode): ConditionNode? = when (node) {
        is ConditionNode.And -> node.children.mapNotNull(::normalize).takeIf { it.isNotEmpty() }?.let { ConditionNode.And(it) }
        is ConditionNode.Or -> node.children.mapNotNull(::normalize).takeIf { it.isNotEmpty() }?.let { ConditionNode.Or(it) }
        is ConditionNode.Not -> normalize(node.child)?.let { ConditionNode.Not(it) }
        else -> node
    }

    /** Ensures the root is a group the editor can add children to. */
    fun asEditableRoot(node: ConditionNode?): ConditionNode = when (node) {
        null -> ConditionNode.And(emptyList())
        is ConditionNode.And, is ConditionNode.Or -> node
        else -> ConditionNode.And(listOf(node))
    }
}

enum class Branch { THEN, ELSE, BODY }

/** Identifies an action list: the root list or a nested branch of an If/Else or Repeat. */
data class ActionListRef(val steps: List<Pair<Int, Branch>> = emptyList()) {
    fun child(index: Int, branch: Branch) = ActionListRef(steps + (index to branch))
}

/** Immutable edit operations on (possibly nested) action lists. */
object ActionTree {
    fun list(root: List<ActionSpec>, ref: ActionListRef): List<ActionSpec> =
        ref.steps.fold(root) { actions, (index, branch) -> branchOf(actions[index], branch) }

    fun update(
        root: List<ActionSpec>,
        ref: ActionListRef,
        transform: (List<ActionSpec>) -> List<ActionSpec>,
    ): List<ActionSpec> {
        if (ref.steps.isEmpty()) return transform(root)
        val (index, branch) = ref.steps.first()
        val rest = ActionListRef(ref.steps.drop(1))
        val parent = root[index]
        val updated = when (parent) {
            is ActionSpec.IfElse -> when (branch) {
                Branch.THEN -> parent.copy(thenActions = update(parent.thenActions, rest, transform))
                Branch.ELSE -> parent.copy(elseActions = update(parent.elseActions, rest, transform))
                Branch.BODY -> parent
            }
            is ActionSpec.Repeat -> if (branch == Branch.BODY) parent.copy(actions = update(parent.actions, rest, transform)) else parent
            else -> parent
        }
        return root.toMutableList().also { it[index] = updated }
    }

    fun add(root: List<ActionSpec>, ref: ActionListRef, action: ActionSpec) = update(root, ref) { it + action }

    fun replace(root: List<ActionSpec>, ref: ActionListRef, index: Int, action: ActionSpec) =
        update(root, ref) { list -> list.toMutableList().also { it[index] = action } }

    /** Replacing the configuration of an If/Else or Repeat keeps its nested actions. */
    fun replaceKeepingChildren(root: List<ActionSpec>, ref: ActionListRef, index: Int, action: ActionSpec): List<ActionSpec> {
        val existing = list(root, ref)[index]
        val merged = when {
            existing is ActionSpec.IfElse && action is ActionSpec.IfElse ->
                action.copy(thenActions = existing.thenActions, elseActions = existing.elseActions)
            existing is ActionSpec.Repeat && action is ActionSpec.Repeat -> action.copy(actions = existing.actions)
            else -> action
        }
        return replace(root, ref, index, merged)
    }

    fun remove(root: List<ActionSpec>, ref: ActionListRef, index: Int) =
        update(root, ref) { list -> list.toMutableList().also { it.removeAt(index) } }

    fun move(root: List<ActionSpec>, ref: ActionListRef, from: Int, to: Int) = update(root, ref) { list ->
        if (from !in list.indices || to !in list.indices) list else list.toMutableList().also { it.add(to, it.removeAt(from)) }
    }

    private fun branchOf(action: ActionSpec, branch: Branch): List<ActionSpec> = when (action) {
        is ActionSpec.IfElse -> if (branch == Branch.ELSE) action.elseActions else action.thenActions
        is ActionSpec.Repeat -> action.actions
        else -> emptyList()
    }
}
