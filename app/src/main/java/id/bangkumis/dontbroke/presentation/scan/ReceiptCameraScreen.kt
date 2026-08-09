package id.bangkumis.dontbroke.presentation.scan

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.viewfinder.compose.MutableCoordinateTransformer
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation

/**
 * JPEG output keeps the whole encoded frame in plane 0. [BitmapFactory] ignores
 * EXIF, so the rotation CameraX reports is applied here instead — a sideways
 * receipt is markedly harder for the vision model to read than an upright one.
 */
private fun ImageProxy.toUprightBitmap(): Bitmap? {
    val buffer = planes.firstOrNull()?.buffer ?: return null
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val degrees = imageInfo.rotationDegrees
    if (degrees == 0) return decoded
    val rotated = Bitmap.createBitmap(
        decoded, 0, 0, decoded.width, decoded.height,
        Matrix().apply { postRotate(degrees.toFloat()) }, true
    )
    if (rotated !== decoded) decoded.recycle()
    return rotated
}

/**
 * Full-screen CameraX viewfinder for photographing a receipt or QRIS slip.
 *
 * Live framing is the point: the earlier IMAGE_CAPTURE hand-off returned whatever
 * the system camera app decided, with no way to steady the shot or light it. Here
 * the user sees exactly what the vision model will see, taps to focus the close
 * subject autofocus tends to hunt on, and can light a receipt read across a dim
 * table. A blurry frame is the single most common reason a scan comes back empty.
 *
 * Capture stays in memory — the JPEG goes straight to a Bitmap, so there is no
 * temp file to clean up and no FileProvider to expose.
 *
 * Picking an existing photo lives here too, as a second button under the shutter:
 * this screen is the only entry point to scanning, so both sources have to be
 * reachable from it. [onPicked] hands the gallery `Uri` straight out rather than a
 * Bitmap — `ScanReceiptUseCase` already decodes it subsampled, and decoding a 12MP
 * photo whole here just to throw most of it away is how you OOM a cheap device.
 */
@Composable
fun ReceiptCameraScreen(
    onCaptured: (Bitmap) -> Unit,
    onPicked: (Uri) -> Unit,
    onCancel: () -> Unit,
    onFailure: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    // Denial no longer closes the screen. With the source dialog gone this is the
    // only route to the gallery, so bailing out here would leave someone who said
    // no to the camera once with no way to scan at all.
    var denied by remember { mutableStateOf(false) }
    val askPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { ok ->
        granted = ok
        denied = !ok
    }

    LaunchedEffect(Unit) {
        if (!granted) askPermission.launch(Manifest.permission.CAMERA)
    }

    // PickVisualMedia opens the photo picker, which needs no storage permission on
    // any API level — the system grants read access to the single chosen item.
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        // Null is a back-press out of the picker, not a failure.
        uri?.let(onPicked)
    }
    val pickImage = {
        galleryLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }

    // MAXIMIZE_QUALITY, not MINIMIZE_LATENCY: this frame is read as text, and a
    // receipt is a still subject, so the extra shutter time buys legibility.
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
    }

    LaunchedEffect(granted) {
        if (!granted) return@LaunchedEffect
        val provider = runCatching { ProcessCameraProvider.awaitInstance(context) }.getOrElse {
            // Cancellation is not a camera fault: leaving the screen while the
            // provider is still resolving lands here too.
            if (it is CancellationException) throw it
            onFailure("Kamera tidak tersedia: ${it.message ?: "tidak diketahui"}")
            return@LaunchedEffect
        }
        val preview = Preview.Builder().build().apply {
            setSurfaceProvider { request -> surfaceRequest = request }
        }
        try {
            camera = provider.bindToLifecycle(
                lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture
            )
            // Hold the binding for as long as this screen is composed; the finally
            // releases the camera when it leaves, including on a back gesture.
            awaitCancellation()
        } catch (e: CancellationException) {
            // The normal exit, and it must be rethrown before the catch below sees
            // it. Every successful scan ends here: handing the frame back sets
            // showCamera = false, the screen leaves the composition, Compose
            // cancels this effect, and awaitCancellation() throws. Kotlin's
            // CancellationException is an IllegalStateException, so `Exception`
            // swallows it — which reported "Gagal membuka kamera: The coroutine
            // scope left the composition" over a scan that had just worked, and
            // cleared isScanning while the request was still in flight.
            throw e
        } catch (e: Exception) {
            onFailure("Gagal membuka kamera: ${e.message ?: "tidak diketahui"}")
        } finally {
            camera = null
            provider.unbindAll()
        }
    }

    LaunchedEffect(torchOn, camera) { camera?.cameraControl?.enableTorch(torchOn) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        surfaceRequest?.let { request ->
            val transformer = remember { MutableCoordinateTransformer() }
            CameraXViewfinder(
                surfaceRequest = request,
                coordinateTransformer = transformer,
                modifier = Modifier.fillMaxSize().pointerInput(camera, request) {
                    detectTapGestures { tap ->
                        // Viewfinder coordinates are not sensor coordinates; the
                        // transformer is what makes "focus where I tapped" true
                        // regardless of crop, rotation, or letterboxing.
                        val point = with(transformer) { tap.transform() }
                        val factory = SurfaceOrientedMeteringPointFactory(
                            request.resolution.width.toFloat(),
                            request.resolution.height.toFloat()
                        )
                        camera?.cameraControl?.startFocusAndMetering(
                            FocusMeteringAction.Builder(
                                factory.createPoint(point.x, point.y)
                            ).build()
                        )
                    }
                }
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(16.dp).align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = "Tutup", tint = Color.White)
            }
            // Info, not FlashOn: the flash glyphs live in material-icons-extended,
            // which this app does not carry. Tint carries the on/off state.
            if (camera?.cameraInfo?.hasFlashUnit() == true) {
                IconButton(onClick = { torchOn = !torchOn }) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = if (torchOn) "Matikan lampu" else "Nyalakan lampu",
                        tint = if (torchOn) Color.Yellow else Color.White
                    )
                }
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                if (denied) "Izin kamera ditolak — pilih foto dari galeri, atau aktifkan izin di Setelan."
                else "Ketuk untuk fokus, lalu ambil foto seluruh struk.",
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
            // Shutter only makes sense with a live camera; the gallery route stays
            // available either way, which is what keeps a denial recoverable.
            if (!denied) {
                FilledIconButton(
                    onClick = {
                        if (capturing) return@FilledIconButton
                        capturing = true
                        imageCapture.takePicture(
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageCapturedCallback() {
                                override fun onCaptureSuccess(image: ImageProxy) {
                                    val bitmap = image.use { it.toUprightBitmap() }
                                    capturing = false
                                    if (bitmap == null) onFailure("Gagal membaca hasil foto.")
                                    else onCaptured(bitmap)
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    capturing = false
                                    onFailure(
                                        "Gagal mengambil foto: ${exception.message ?: "tidak diketahui"}"
                                    )
                                }
                            }
                        )
                    },
                    enabled = camera != null && !capturing,
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.size(72.dp)
                ) {
                    if (capturing) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.Info, contentDescription = "Ambil foto")
                }
            }
            TextButton(onClick = { pickImage() }, enabled = !capturing) {
                Text("Pilih dari galeri", color = Color.White)
            }
        }
    }
}
