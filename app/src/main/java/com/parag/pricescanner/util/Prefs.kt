package com.parag.pricescanner.util

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Small app settings: monthly budget and whether to follow the wallpaper colour. */
class Prefs private constructor(ctx: Context) {
    private val sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _budget = MutableStateFlow(sp.getFloat(KEY_BUDGET, 0f).toDouble())
    /** Monthly budget in rupees; 0 means not set. */
    val budget: StateFlow<Double> = _budget.asStateFlow()

    private val _dynamic = MutableStateFlow(sp.getBoolean(KEY_DYNAMIC, false))
    val dynamicColor: StateFlow<Boolean> = _dynamic.asStateFlow()

    fun setBudget(v: Double) {
        val clean = v.coerceAtLeast(0.0)
        sp.edit().putFloat(KEY_BUDGET, clean.toFloat()).apply()
        _budget.value = clean
    }

    fun setDynamicColor(on: Boolean) {
        sp.edit().putBoolean(KEY_DYNAMIC, on).apply()
        _dynamic.value = on
    }

    companion object {
        private const val KEY_BUDGET = "monthly_budget"
        private const val KEY_DYNAMIC = "dynamic_color"
        @Volatile private var instance: Prefs? = null
        fun get(ctx: Context): Prefs = instance ?: synchronized(this) {
            instance ?: Prefs(ctx.applicationContext).also { instance = it }
        }
    }
}
