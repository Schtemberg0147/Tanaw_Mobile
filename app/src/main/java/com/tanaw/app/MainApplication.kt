package com.tanaw.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

// If you already have an Application subclass somewhere, just add
// @HiltAndroidApp to it instead of creating a new one — there can only be one.
// Don't forget to register it in AndroidManifest.xml:
//   <application android:name=".MainApplication" ...>
@HiltAndroidApp
class MainApplication : Application()