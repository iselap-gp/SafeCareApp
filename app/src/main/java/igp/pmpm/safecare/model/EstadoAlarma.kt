package igp.pmpm.safecare.model

enum class EstadoAlarma(val valorFirebase: String) {
    CASA("CASA"),
    FUERA("FUERA"),
    DESACTIVADO("DESACTIVADO");

    val estaArmado: Boolean
        get() = this != DESACTIVADO

    val esModoFuera: Boolean
        get() = this == FUERA

    val esModoCasa: Boolean
        get() = this == CASA

    companion object {
        fun desdeFirebase(valor: String?): EstadoAlarma =
            entries.find { it.valorFirebase == valor } ?: DESACTIVADO
    }
}
