package igp.pmpm.safecare.model

data class ConfiguracionLlamadaEmergencia(
    val llamarA112: Boolean = true,
    val idContactoSeleccionado: String = ""
) {
    fun aMapaFirebase(): Map<String, Any> = mapOf(
        "llamarA112" to llamarA112,
        "idContactoSeleccionado" to idContactoSeleccionado
    )
}
