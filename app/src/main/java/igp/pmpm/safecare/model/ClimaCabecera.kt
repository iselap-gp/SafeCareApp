package igp.pmpm.safecare.model

import androidx.annotation.DrawableRes

data class ClimaCabecera(
    val descripcion: String,
    val temperaturaCelsius: Int,
    @DrawableRes val iconoResId: Int
)
