package igp.pmpm.safecare.model

data class ContactoEmergencia(
    val id: String = "",
    val nombre: String = "",
    val telefono: String = ""
) {
    val inicialAvatar: String
        get() = nombre.firstOrNull { it.isLetter() }?.uppercase() ?: "?"

    fun aMapaFirebase(): Map<String, Any> = mapOf(
        "id" to id,
        "nombre" to nombre,
        "telefono" to telefono
    )

    companion object {
        const val ID_CONTACTO_PERFIL = "__contacto_perfil__"
    }
}
