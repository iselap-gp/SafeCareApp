package igp.pmpm.safecare.model

import com.google.firebase.database.PropertyName

data class Usuario(
    @get:PropertyName("uid")
    @PropertyName("uid")
    val idUsuario: String = "",

    val nombre: String = "",

    @get:PropertyName("email")
    @PropertyName("email")
    val correoElectronico: String = "",

    val contactoEmergencia: String = ""
)
