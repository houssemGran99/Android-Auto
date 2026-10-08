package com.autoflow.app.ui.builder

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.autoflow.app.R
import com.autoflow.app.ui.components.SpecCard
import com.autoflow.app.ui.text.SpecFormatter
import com.autoflow.app.ui.text.SpecIcons
import com.autoflow.app.ui.text.rememberSpecFormatter
import com.autoflow.core.model.ActionSpec
import com.autoflow.core.model.ConditionNode
import com.autoflow.core.model.typeKey

/** Picker listing catalog entries grouped by category. */
@Composable
fun <T> AddItemDialog(
    title: String,
    items: List<CatalogItem<T>>,
    itemTitle: (CatalogItem<T>) -> String,
    itemIcon: (CatalogItem<T>) -> ImageVector,
    onPick: (CatalogItem<T>) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        text = {
            val grouped = items.groupBy { it.category }
            LazyColumn(Modifier.heightIn(max = 480.dp)) {
                grouped.forEach { (category, entries) ->
                    item(key = "header-$category") {
                        Text(
                            stringResource(category),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        )
                    }
                    items(entries, key = { it.typeKey }) { entry ->
                        ListItem(
                            leadingContent = { Icon(if (entry.restriction != null) Icons.Outlined.Block else itemIcon(entry), null) },
                            headlineContent = { Text(itemTitle(entry)) },
                            supportingContent = entry.restriction?.let { { Text(stringResource(it)) } },
                            modifier = Modifier.clickable { onPick(entry) },
                        )
                    }
                }
            }
        },
    )
}

/**
 * Visual AND / OR / NOT tree editor. Works on an immutable root group and reports the
 * updated tree through [onChange]. Used for automation conditions and If/Else actions.
 */
@Composable
fun ConditionTreeEditor(root: ConditionNode, onChange: (ConditionNode) -> Unit, modifier: Modifier = Modifier) {
    val f = rememberSpecFormatter()
    var addTo by remember { mutableStateOf<NodePath?>(null) }
    var editing by remember { mutableStateOf<Pair<NodePath, ConditionNode>?>(null) }
    var creating by remember { mutableStateOf<Pair<NodePath, ConditionNode>?>(null) }

    val actions = TreeActions(
        toggleGroup = { onChange(ConditionTree.toggleGroupType(root, it)) },
        toggleNot = { onChange(ConditionTree.toggleNot(root, it)) },
        remove = { onChange(ConditionTree.remove(root, it)) },
        add = { addTo = it },
        edit = { path, node -> editing = path to node },
    )
    Box(modifier) {
        GroupView(root, emptyList(), notPath = null, isRoot = true, f = f, actions = actions)
    }

    addTo?.let { groupPath ->
        AddItemDialog(
            title = stringResource(R.string.add_condition),
            items = BuilderCatalog.conditions,
            itemTitle = { it.title?.let { res -> f.res(res) } ?: f.conditionTypeTitle(it.typeKey) },
            itemIcon = { SpecIcons.condition(it.typeKey) },
            onPick = { item ->
                addTo = null
                val node = item.create()
                if (item.needsConfiguration) creating = groupPath to node else onChange(ConditionTree.addChild(root, groupPath, node))
            },
            onDismiss = { addTo = null },
        )
    }
    creating?.let { (groupPath, node) ->
        SpecEditorDialog(
            title = f.conditionTitle(node),
            initial = node,
            isValid = ::isValidCondition,
            onConfirm = { onChange(ConditionTree.addChild(root, groupPath, it)); creating = null },
            onDismiss = { creating = null },
        ) { value, change -> ConditionForm(value, change) }
    }
    editing?.let { (path, node) ->
        SpecEditorDialog(
            title = f.conditionTitle(node),
            initial = node,
            isValid = ::isValidCondition,
            onConfirm = { onChange(ConditionTree.replace(root, path, it)); editing = null },
            onDismiss = { editing = null },
        ) { value, change -> ConditionForm(value, change) }
    }
}

private class TreeActions(
    val toggleGroup: (NodePath) -> Unit,
    val toggleNot: (NodePath) -> Unit,
    val remove: (NodePath) -> Unit,
    val add: (NodePath) -> Unit,
    val edit: (NodePath, ConditionNode) -> Unit,
)

@Composable
private fun GroupView(
    group: ConditionNode,
    path: NodePath,
    notPath: NodePath?,
    isRoot: Boolean,
    f: SpecFormatter,
    actions: TreeActions,
) {
    val isAnd = group is ConditionNode.And
    val children = ConditionTree.children(group)
    val connector = stringResource(if (isAnd) R.string.logic_and else R.string.logic_or)
    OutlinedCard(
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (notPath != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (notPath != null) {
                    Text(
                        stringResource(R.string.logic_not),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                FilterChip(
                    selected = isAnd,
                    onClick = { if (!isAnd) actions.toggleGroup(path) },
                    label = { Text(stringResource(R.string.logic_all)) },
                )
                FilterChip(
                    selected = !isAnd,
                    onClick = { if (isAnd) actions.toggleGroup(path) },
                    label = { Text(stringResource(R.string.logic_any)) },
                    modifier = Modifier.padding(start = 6.dp),
                )
                Box(Modifier.weight(1f))
                if (!isRoot) {
                    NodeMenu(
                        negated = notPath != null,
                        onToggleNot = { actions.toggleNot(notPath ?: path) },
                        onRemove = { actions.remove(path) },
                    )
                }
            }
            if (children.isEmpty()) {
                Text(
                    stringResource(if (isRoot) R.string.conditions_empty else R.string.group_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            children.forEachIndexed { index, child ->
                if (index > 0) {
                    Text(
                        connector,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
                NodeView(child, path + index, f, actions)
            }
            TextButton(onClick = { actions.add(path) }) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Text(stringResource(R.string.add_condition), modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}

@Composable
private fun NodeView(node: ConditionNode, path: NodePath, f: SpecFormatter, actions: TreeActions) {
    when (node) {
        is ConditionNode.And, is ConditionNode.Or -> GroupView(node, path, notPath = null, isRoot = false, f = f, actions = actions)
        is ConditionNode.Not -> when (val child = node.child) {
            is ConditionNode.And, is ConditionNode.Or ->
                GroupView(child, path + 0, notPath = path, isRoot = false, f = f, actions = actions)
            else -> LeafView(child, path + 0, notPath = path, f = f, actions = actions)
        }
        else -> LeafView(node, path, notPath = null, f = f, actions = actions)
    }
}

@Composable
private fun LeafView(node: ConditionNode, path: NodePath, notPath: NodePath?, f: SpecFormatter, actions: TreeActions) {
    val not = stringResource(R.string.logic_not)
    SpecCard(
        icon = SpecIcons.condition(node.typeKey),
        title = if (notPath != null) "$not ${f.conditionTitle(node)}" else f.conditionTitle(node),
        detail = f.conditionDetail(node),
        onClick = { actions.edit(path, node) },
        trailing = {
            NodeMenu(
                negated = notPath != null,
                onToggleNot = { actions.toggleNot(notPath ?: path) },
                onRemove = { actions.remove(path) },
            )
        },
    )
}

@Composable
private fun NodeMenu(negated: Boolean, onToggleNot: () -> Unit, onRemove: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.more_options))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(if (negated) R.string.remove_not else R.string.add_not)) },
                onClick = { open = false; onToggleNot() },
            )
            DropdownMenuItem(text = { Text(stringResource(R.string.action_remove)) }, onClick = { open = false; onRemove() })
        }
    }
}

/** Callbacks for editing (nested) action lists. */
class ActionListCallbacks(
    val onAdd: (ActionListRef) -> Unit,
    val onEdit: (ActionListRef, Int, ActionSpec) -> Unit,
    val onRemove: (ActionListRef, Int) -> Unit,
    val onMove: (ActionListRef, Int, Int) -> Unit,
)

/** Reorderable list of action cards; If/Else and Repeat show their nested lists inline. */
@Composable
fun ActionListEditor(actions: List<ActionSpec>, ref: ActionListRef, callbacks: ActionListCallbacks, f: SpecFormatter) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ReorderableColumn(items = actions, onMove = { from, to -> callbacks.onMove(ref, from, to) }) { index, action, handle, _ ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val dragLabel = stringResource(R.string.drag_to_reorder)
                SpecCard(
                    icon = SpecIcons.action(action.typeKey),
                    title = f.actionTitle(action),
                    detail = f.actionDetail(action),
                    onClick = { callbacks.onEdit(ref, index, action) },
                    leading = {
                        Icon(
                            Icons.Outlined.DragIndicator,
                            contentDescription = null,
                            modifier = handle
                                .padding(end = 8.dp)
                                .semantics { contentDescription = dragLabel },
                        )
                    },
                    trailing = {
                        ActionMenu(
                            canMoveUp = index > 0,
                            canMoveDown = index < actions.lastIndex,
                            onMoveUp = { callbacks.onMove(ref, index, index - 1) },
                            onMoveDown = { callbacks.onMove(ref, index, index + 1) },
                            onRemove = { callbacks.onRemove(ref, index) },
                        )
                    },
                )
                when (action) {
                    is ActionSpec.IfElse -> {
                        NestedBranch(stringResource(R.string.branch_then), action.thenActions, ref.child(index, Branch.THEN), callbacks, f)
                        NestedBranch(stringResource(R.string.branch_else), action.elseActions, ref.child(index, Branch.ELSE), callbacks, f)
                    }
                    is ActionSpec.Repeat ->
                        NestedBranch(stringResource(R.string.branch_repeat), action.actions, ref.child(index, Branch.BODY), callbacks, f)
                    else -> Unit
                }
            }
        }
        TextButton(onClick = { callbacks.onAdd(ref) }) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Text(stringResource(R.string.add_action), modifier = Modifier.padding(start = 4.dp))
        }
    }
}

@Composable
private fun NestedBranch(label: String, actions: List<ActionSpec>, ref: ActionListRef, callbacks: ActionListCallbacks, f: SpecFormatter) {
    Column(Modifier.padding(start = 24.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        ActionListEditor(actions, ref, callbacks, f)
    }
}

@Composable
private fun ActionMenu(
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.more_options))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (canMoveUp) {
                DropdownMenuItem(text = { Text(stringResource(R.string.move_up)) }, onClick = { open = false; onMoveUp() })
            }
            if (canMoveDown) {
                DropdownMenuItem(text = { Text(stringResource(R.string.move_down)) }, onClick = { open = false; onMoveDown() })
            }
            DropdownMenuItem(text = { Text(stringResource(R.string.action_remove)) }, onClick = { open = false; onRemove() })
        }
    }
}
