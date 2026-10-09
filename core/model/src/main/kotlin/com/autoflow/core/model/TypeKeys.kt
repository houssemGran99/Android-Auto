package com.autoflow.core.model

/** Stable type identifiers (same as the JSON "type" values), used for logs and UI lookups. */
val TriggerSpec.typeKey: String
    get() = when (this) {
        is TriggerSpec.Time -> "TIME"
        is TriggerSpec.Interval -> "INTERVAL"
        is TriggerSpec.WifiConnected -> "WIFI_CONNECTED"
        is TriggerSpec.WifiDisconnected -> "WIFI_DISCONNECTED"
        is TriggerSpec.BluetoothConnected -> "BLUETOOTH_CONNECTED"
        is TriggerSpec.BluetoothDisconnected -> "BLUETOOTH_DISCONNECTED"
        is TriggerSpec.BatteryLevel -> "BATTERY_LEVEL"
        TriggerSpec.ChargerConnected -> "CHARGER_CONNECTED"
        TriggerSpec.ChargerDisconnected -> "CHARGER_DISCONNECTED"
        is TriggerSpec.AppOpened -> "APP_OPENED"
        is TriggerSpec.HeadphonesConnected -> "HEADPHONES_CONNECTED"
        is TriggerSpec.HeadphonesDisconnected -> "HEADPHONES_DISCONNECTED"
        is TriggerSpec.LocationEnter -> "LOCATION_ENTER"
        is TriggerSpec.LocationExit -> "LOCATION_EXIT"
        is TriggerSpec.NotificationReceived -> "NOTIFICATION_RECEIVED"
        is TriggerSpec.CalendarEventStart -> "CALENDAR_EVENT_START"
        is TriggerSpec.CalendarEventEnd -> "CALENDAR_EVENT_END"
        is TriggerSpec.SunEvent -> "SUN_EVENT"
    }

val ConditionNode.typeKey: String
    get() = when (this) {
        is ConditionNode.And -> "AND"
        is ConditionNode.Or -> "OR"
        is ConditionNode.Not -> "NOT"
        is ConditionNode.TimeRange -> "TIME_RANGE"
        is ConditionNode.DaysOfWeek -> "DAYS_OF_WEEK"
        is ConditionNode.BatteryLevel -> "BATTERY_LEVEL"
        is ConditionNode.Charging -> "CHARGING"
        is ConditionNode.WifiState -> "WIFI_STATE"
        is ConditionNode.BluetoothState -> "BLUETOOTH_STATE"
        is ConditionNode.HeadphonesState -> "HEADPHONES_STATE"
        is ConditionNode.VariableCompare -> "VARIABLE"
    }

val ActionSpec.typeKey: String
    get() = when (this) {
        is ActionSpec.ShowNotification -> "NOTIFICATION"
        is ActionSpec.LaunchApp -> "LAUNCH_APP"
        is ActionSpec.OpenUrl -> "OPEN_URL"
        is ActionSpec.OpenSettings -> "OPEN_SETTINGS"
        is ActionSpec.SetBrightness -> "SET_BRIGHTNESS"
        is ActionSpec.SetVolume -> "SET_VOLUME"
        is ActionSpec.SetDoNotDisturb -> "DO_NOT_DISTURB"
        is ActionSpec.Speak -> "SPEAK"
        is ActionSpec.PlaySound -> "PLAY_SOUND"
        is ActionSpec.Vibrate -> "VIBRATE"
        is ActionSpec.HttpRequest -> "HTTP_REQUEST"
        is ActionSpec.Delay -> "DELAY"
        is ActionSpec.SetVariable -> "SET_VARIABLE"
        is ActionSpec.IfElse -> "IF_ELSE"
        is ActionSpec.Repeat -> "REPEAT"
        is ActionSpec.DismissNotifications -> "DISMISS_NOTIFICATIONS"
        is ActionSpec.While -> "WHILE"
        is ActionSpec.WaitUntil -> "WAIT_UNTIL"
        ActionSpec.Stop -> "STOP"
        is ActionSpec.VariableOperation -> "VARIABLE_OPERATION"
        is ActionSpec.ParseJson -> "PARSE_JSON"
    }
