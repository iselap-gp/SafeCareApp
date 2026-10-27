package igp.pmpm.safecare.ui

import android.util.Base64
import android.util.Size
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class TransmisorCamaraEsp32(
    private val activity: AppCompatActivity,
    private val vistaPrevia: PreviewView
) {

    private val ejecutor: Executor = ContextCompat.getMainExecutor(activity)
    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null

    suspend fun iniciar(): Result<Unit> = runCatching {
        val provider = obtenerProveedorCamara()
        cameraProvider = provider
        provider.unbindAll()
        val preview = Preview.Builder().build().also {
            it.surfaceProvider = vistaPrevia.surfaceProvider
        }
        val captura = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetResolution(Size(320, 240))
            .setJpegQuality(60)
            .build()
        imageCapture = captura
        provider.bindToLifecycle(
            activity,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            captura
        )
    }

    suspend fun capturarJpegBase64(): String? {
        val captura = imageCapture ?: return null
        return suspendCoroutine { continuacion ->
            captura.takePicture(
                ejecutor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val bytes = extraerJpeg(image)
                        image.close()
                        if (bytes == null) {
                            continuacion.resume(null)
                        } else {
                            continuacion.resume(
                                Base64.encodeToString(bytes, Base64.NO_WRAP)
                            )
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        continuacion.resume(null)
                    }
                }
            )
        }
    }

    fun detener() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        imageCapture = null
    }

    private suspend fun obtenerProveedorCamara(): ProcessCameraProvider = suspendCoroutine { cont ->
        val futuro = ProcessCameraProvider.getInstance(activity)
        futuro.addListener(
            {
                cont.resume(futuro.get())
            },
            ejecutor
        )
    }

    private fun extraerJpeg(image: ImageProxy): ByteArray? {
        val buffer = image.planes.firstOrNull()?.buffer ?: return null
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return bytes
    }
}
