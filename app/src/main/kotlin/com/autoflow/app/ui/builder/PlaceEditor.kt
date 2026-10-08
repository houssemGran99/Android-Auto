package com.autoflow.app.ui.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.autoflow.app.R
import com.autoflow.app.ui.permissions.PermissionPanel
import com.autoflow.core.model.Capability
import com.autoflow.core.model.GeoPlace
import com.autoflow.platform.triggers.Coordinates
import kotlinx.coroutines.launch
import java.util.Locale

/** Supplies a one-shot location fix to the place editor (provided by the builder screen). */
val LocalCurrentLocation = compositionLocalOf<suspend () -> Coordinates?> { { null } }

fun isValidPlace(place: GeoPlace): Boolean =
    place.name.isNotBlank() && !(place.latitude == 0.0 && place.longitude == 0.0)

/**
 * Edits a geofence: name, coordinates (typed or taken from the current location) and radius.
 * Coordinates are kept as text while typing so partial input like "48." is not lost.
 */
@Composable
fun PlaceEditor(place: GeoPlace, onChange: (GeoPlace) -> Unit) {
    val currentLocation = LocalCurrentLocation.current
    val scope = rememberCoroutineScope()
    var latitude by remember { mutableStateOf(if (isValidPlace(place)) format(place.latitude) else "") }
    var longitude by remember { mutableStateOf(if (isValidPlace(place)) format(place.longitude) else "") }
    var locating by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    fun update(lat: String = latitude, lng: String = longitude) {
        latitude = lat
        longitude = lng
        val la = lat.trim().toDoubleOrNull()?.takeIf { it in -90.0..90.0 }
        val lo = lng.trim().toDoubleOrNull()?.takeIf { it in -180.0..180.0 }
        if (la != null && lo != null) onChange(place.copy(latitude = la, longitude = lo))
    }

    TextInput(stringResource(R.string.field_place_name), place.name, { onChange(place.copy(name = it)) })
    PermissionPanel(setOf(Capability.LOCATION, Capability.BACKGROUND_LOCATION))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(
            onClick = {
                locating = true
                failed = false
                scope.launch {
                    val fix = currentLocation()
                    locating = false
                    if (fix == null) failed = true else update(format(fix.latitude), format(fix.longitude))
                }
            },
            enabled = !locating,
        ) { Text(stringResource(R.string.use_current_location)) }
        if (locating) CircularProgressIndicator(Modifier.size(20.dp).padding(2.dp))
    }
    if (failed) Hint(stringResource(R.string.current_location_unknown))
    TextInput(
        stringResource(R.string.field_latitude),
        latitude,
        { update(lat = it) },
        isError = latitude.isNotEmpty() && latitude.trim().toDoubleOrNull()?.takeIf { it in -90.0..90.0 } == null,
        keyboardType = KeyboardType.Decimal,
    )
    TextInput(
        stringResource(R.string.field_longitude),
        longitude,
        { update(lng = it) },
        isError = longitude.isNotEmpty() && longitude.trim().toDoubleOrNull()?.takeIf { it in -180.0..180.0 } == null,
        keyboardType = KeyboardType.Decimal,
    )
    PercentSlider(
        stringResource(R.string.field_radius),
        place.radiusMeters,
        { onChange(place.copy(radiusMeters = it)) },
        GeoPlace.MIN_RADIUS_METERS..2_000,
        suffix = " m",
    )
    Hint(stringResource(R.string.hint_geofence))
}

private fun format(value: Double) = String.format(Locale.ROOT, "%.6f", value)
