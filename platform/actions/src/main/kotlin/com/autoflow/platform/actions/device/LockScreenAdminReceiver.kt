package com.autoflow.platform.actions.device

import android.app.admin.DeviceAdminReceiver

/** Device admin receiver limited to the force-lock policy (see res/xml/device_admin.xml). */
class LockScreenAdminReceiver : DeviceAdminReceiver()
