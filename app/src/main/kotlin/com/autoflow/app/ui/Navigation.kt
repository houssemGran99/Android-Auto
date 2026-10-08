package com.autoflow.app.ui

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.ui.graphics.vector.ImageVector
import com.autoflow.app.R

enum class TopLevelDestination(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    HOME("home", R.string.nav_home, Icons.Outlined.Home),
    AUTOMATIONS("automations", R.string.nav_automations, Icons.AutoMirrored.Outlined.ViewList),
    TEMPLATES("templates", R.string.nav_templates, Icons.Outlined.AutoAwesome),
    HISTORY("history", R.string.nav_history, Icons.Outlined.History),
    VARIABLES("variables", R.string.nav_variables, Icons.Outlined.DataObject),
}

object Routes {
    const val SETTINGS = "settings"
    const val PERMISSIONS = "permissions"
    const val SEARCH = "search"
    const val ARG_AUTOMATION_ID = "automationId"
    const val ARG_TEMPLATE_ID = "templateId"
    const val ARG_EXECUTION_ID = "executionId"
    const val BUILDER = "builder?$ARG_AUTOMATION_ID={$ARG_AUTOMATION_ID}&$ARG_TEMPLATE_ID={$ARG_TEMPLATE_ID}"
    const val EXECUTION = "execution/{$ARG_EXECUTION_ID}"

    fun newAutomation() = "builder"
    fun editAutomation(id: String) = "builder?$ARG_AUTOMATION_ID=${Uri.encode(id)}"
    fun fromTemplate(templateId: String) = "builder?$ARG_TEMPLATE_ID=${Uri.encode(templateId)}"
    fun execution(id: String) = "execution/${Uri.encode(id)}"
}
