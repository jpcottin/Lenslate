package com.jpcottin.lenslate.ui.glasses

import android.content.Context
import android.content.Intent
import androidx.xr.projected.ProjectedContext
import androidx.xr.projected.experimental.ExperimentalProjectedApi

/** Starts [GlassesActivity] on the connected Display AI Glasses (never on the phone). */
@OptIn(ExperimentalProjectedApi::class)
object GlassesLauncher {
    fun launch(context: Context): Result<Unit> = runCatching {
        val options = ProjectedContext.createProjectedActivityOptions(context)
        val intent = Intent(context, GlassesActivity::class.java)
        context.startActivity(intent, options.toBundle())
    }
}
