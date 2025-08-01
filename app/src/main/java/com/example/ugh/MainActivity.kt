package com.example.ugh

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha // For the alpha modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset // For IntOffset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bumptech.glide.Glide
import com.bumptech.glide.gifencoder.AnimatedGifEncoder
import com.example.ugh.ui.theme.UghTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.roundToInt
import android.media.MediaScannerConnection
import androidx.activity.viewModels
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.provider.MediaStore
import android.content.ContentValues
import android.os.Environment
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale

// ViewModel to manage the state and logic of the Ugh app
class UghViewModel : ViewModel() {

    // Enum to represent the different states of the application flow
    enum class AppState {
        SELECT_IMAGE,
        CROP_IMAGE,
        SELECT_ANCHOR_POINTS,
        COMPILING_GIF,
        PREVIEW_GIF
    }

    // MutableStateFlow for the current application state, observed by Composables
    private val _currentAppState = MutableStateFlow(AppState.SELECT_IMAGE)
    val currentAppState: StateFlow<AppState> = _currentAppState.asStateFlow()

    // MutableState for the original selected image URI
    var originalImageUri by mutableStateOf<Uri?>(null)
        private set

    // MutableState for the scaled bitmap of the original image
    var scaledBitmap by mutableStateOf<Bitmap?>(null)
        private set

    // MutableState for the number of vertical segments to crop (2, 3, or 4)
    var cropSegments by mutableIntStateOf(3)


    // MutableState for the list of cropped bitmaps
    var croppedBitmaps by mutableStateOf<List<Bitmap>>(emptyList())
        private set

    // MutableState for the list of anchor points (Offset) for each cropped image
    var anchorPoints by mutableStateOf<List<Offset>>(emptyList())
        private set

    // MutableState for the current index of the image being processed for anchor point selection
    var currentAnchorImageIndex by mutableIntStateOf(0)
        private set

    // MutableState for the generated GIF file URI
    var gifUri by mutableStateOf<Uri?>(null)
        private set

    // MutableState for the GIF compilation progress (0-100)
    var gifCompilationProgress by mutableIntStateOf(0)
        private set
    // State for the image that will be used as onion skin
    var onionSkinBitmap: MutableState<Bitmap?> = mutableStateOf(null)
        private set

    // State for the transparency of the onion skin
    var onionSkinTransparency: MutableState<Float> =
        mutableFloatStateOf(0.5f) // Default transparency
        private set

    // Function to update onion skin transparency
    fun setOnionSkinTransparency(value: Float) {
        onionSkinTransparency.value = value
    }

    // Function to set the onion skin bitmap (e.g., when moving to the next image)
    fun setNextOnionSkin(bitmap: Bitmap?) {
        onionSkinBitmap.value = bitmap
    }

    fun setSelectedImageUri(uri: Uri?, context: Context) {
        originalImageUri = uri
        if (uri != null) {
            viewModelScope.launch(Dispatchers.IO) {
                val bitmap = uriToBitmap(context, uri)
                if (bitmap != null) {
                    scaledBitmap = scaleBitmap(bitmap)
                    withContext(Dispatchers.Main) {
                        _currentAppState.value = AppState.CROP_IMAGE
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to load image.", Toast.LENGTH_SHORT).show()
                        _currentAppState.value = AppState.SELECT_IMAGE // Go back to selection
                    }
                }
            }
        } else {
            _currentAppState.value = AppState.SELECT_IMAGE
        }
    }

    fun performCrop() {
        val bitmap = scaledBitmap ?: return
        val segments = cropSegments
        val croppedList = mutableListOf<Bitmap>()

        // Calculate the width of each vertical segment
        val segmentWidth = bitmap.width / segments // <--- CHANGED: Dividing width

        for (i in 0 until segments) {
            val x = i * segmentWidth // <--- CHANGED: Calculating X offset for vertical cuts

            // Ensure the last segment takes any remaining pixels due to integer division
            // This is crucial for handling cases where bitmap.width is not perfectly divisible by segments
            val width = if (i == segments - 1) bitmap.width - x else segmentWidth

            val cropped = Bitmap.createBitmap(
                bitmap,
                x,              // <--- CHANGED: X-coordinate of the top-left corner
                0,              // Y-coordinate of the top-left corner (always 0 for full height)
                width,          // <--- CHANGED: Width of this segment
                bitmap.height   // <--- CHANGED: Height of this segment (full original height)
            )
            croppedList.add(cropped)
        }
        croppedBitmaps = croppedList
        // Initialize anchor points to the center of each cropped image
        anchorPoints = croppedList.map { Offset(it.width / 2f, it.height / 2f) }
        currentAnchorImageIndex = 0
        _currentAppState.value = AppState.SELECT_ANCHOR_POINTS
    }

    /**
     * Updates the anchor point for the currently selected image.
     * @param offset The new Offset for the anchor point.
     */
    fun updateCurrentAnchorPoint(offset: Offset) {
        val currentList = anchorPoints.toMutableList()
        if (currentAnchorImageIndex < currentList.size) {
            currentList[currentAnchorImageIndex] = offset
            anchorPoints = currentList
        }
    }

    /**
     * Confirms the anchor point for the current image.
     * If there are more images, moves to the next. Otherwise, starts GIF compilation.
     * @param context The application context.
     */
    fun confirmAnchorPoint(context: Context) {
        if (currentAnchorImageIndex < croppedBitmaps.size - 1) {
            currentAnchorImageIndex++
        } else {
            // All anchor points selected, proceed to compile GIF
            compileGif(context)
        }
    }

    fun compileGif(context: Context) {
        _currentAppState.value = AppState.COMPILING_GIF
        gifCompilationProgress = 0

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val outputStream = ByteArrayOutputStream()
                val gifEncoder = AnimatedGifEncoder()
                gifEncoder.start(outputStream)
                gifEncoder.setDelay(100) // 100ms frame delay
                gifEncoder.setRepeat(0) // 0 for infinite loop

                val frames = generateWiggleFrames()

                for (frame in frames) {
                    gifEncoder.addFrame(frame)
                }
                for (i in frames.size - 2 downTo 1) {
                    gifEncoder.addFrame(frames[i])
                }

                gifEncoder.finish()

                // Save the GIF to internal storage
                val gifFile = saveGifToFile(context, outputStream.toByteArray())
                if (gifFile != null) {
                    gifUri = Uri.fromFile(gifFile)
                    withContext(Dispatchers.Main) {
                        _currentAppState.value = AppState.PREVIEW_GIF
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to save GIF.", Toast.LENGTH_SHORT).show()
                        _currentAppState.value = AppState.SELECT_IMAGE // Go back to start
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error compiling GIF: ${e.message}", Toast.LENGTH_LONG).show()
                    _currentAppState.value = AppState.SELECT_IMAGE // Go back to start
                }
            } finally {
                withContext(Dispatchers.Main) {
                    gifCompilationProgress = 0 // Reset progress
                }
            }
        }
    }
    private fun generateWiggleFrames(): List<Bitmap> {
        if (croppedBitmaps.isEmpty() || anchorPoints.isEmpty() || croppedBitmaps.size != anchorPoints.size) {
            // Essential check: Ensure you have an anchor point for each cropped bitmap
            return emptyList()
        }

        // --- 1. Calculate the necessary dimensions for the final GIF frames ---
        // We need to determine the bounding box required to contain all *aligned* images.
        // The alignment target will be the center of this calculated bounding box.

        // First, let's find the min/max X and Y coordinates that *any* bitmap's edge would
        // reach if we align their anchor points to a hypothetical (0,0) reference.
        var minCalculatedX = Float.POSITIVE_INFINITY
        var maxCalculatedX = Float.NEGATIVE_INFINITY
        var minCalculatedY = Float.POSITIVE_INFINITY
        var maxCalculatedY = Float.NEGATIVE_INFINITY

        // Calculate the overall average anchor point from the original (cropped bitmap) coordinates.
        // This gives us a conceptual "center" for the anchor points across all slices.
        val avgAnchorXrelativeToBitmap = anchorPoints.map { it.x }.average().toFloat()
        val avgAnchorYrelativeToBitmap = anchorPoints.map { it.y }.average().toFloat()


        // Iterate through cropped bitmaps to find the bounds needed if aligned
        for (i in croppedBitmaps.indices) {
            val bitmap = croppedBitmaps[i]
            val anchor = anchorPoints[i]

            // Calculate the draw position for this bitmap if its anchor were at (avgAnchorX_relativeToBitmap, avgAnchorY_relativeToBitmap)
            val currentDrawX = avgAnchorXrelativeToBitmap - anchor.x
            val currentDrawY = avgAnchorYrelativeToBitmap - anchor.y

            // Update global min/max bounds based on where this bitmap's corners would land
            minCalculatedX = minOf(minCalculatedX, currentDrawX)
            maxCalculatedX = maxOf(maxCalculatedX, currentDrawX + bitmap.width)
            minCalculatedY = minOf(minCalculatedY, currentDrawY)
            maxCalculatedY = maxOf(maxCalculatedY, currentDrawY + bitmap.height)
        }

        // Calculate the final GIF frame dimensions based on the calculated bounds
        val gifFrameWidth = (maxCalculatedX - minCalculatedX).roundToInt()
        val gifFrameHeight = (maxCalculatedY - minCalculatedY).roundToInt()

        // Determine the offset needed to shift all content so that `minCalculatedX/Y` effectively becomes `0/0`
        // on the new frame. This centers the content within the derived frame dimensions.
        val globalShiftX = -minCalculatedX
        val globalShiftY = -minCalculatedY

        // --- 2. Define the overall wiggle effect (optional, applied to already aligned frames) ---
        val wiggleMaxOffsetPx = 10f // Max pixel offset for the overall wiggle effect

        // Sequence of offsets for the overall "wiggle" motion of the entire aligned frame.
        // This list determines the number of frames in your GIF and their overall shift.
        // Example for a simple back-and-forth horizontal wiggle:
        val overallWiggleOffsets = listOf(
            Offset(0f, 0f), // Center
            Offset(wiggleMaxOffsetPx, 0f), // Shift right
            Offset(0f, 0f), // Back to center
            Offset(-wiggleMaxOffsetPx, 0f), // Shift left
            Offset(0f, 0f) // Back to center
        )
        // If you want more frames, or a different pattern, adjust this list.
        // For a smoother wiggle, you might want more intermediate steps.

        val gifFrames = mutableListOf<Bitmap>()

        // --- 3. Generate each GIF frame by aligning and optionally wiggling ---
        // Each 'croppedBitmap' is a unique frame of the GIF, so we iterate through them.
        for (i in croppedBitmaps.indices) {
            val currentBitmap = croppedBitmaps[i]
            val currentAnchor = anchorPoints[i]

            // Get the specific overall wiggle offset for *this* frame index
            // Use modulo to cycle through overallWiggleOffsets if you have more croppedBitmaps than wiggle offsets
            val currentOverallWiggleOffset = overallWiggleOffsets[i % overallWiggleOffsets.size]

            // Create a new blank Bitmap for this single frame of the GIF.
            val frameBitmap = createBitmap(gifFrameWidth, gifFrameHeight) // Use ARGB_8888 for transparency
            val canvas = Canvas(frameBitmap)
            canvas.drawColor(android.graphics.Color.TRANSPARENT) // Transparent background

            // Calculate the draw position for the 'currentBitmap' on this 'frameBitmap':
            // a) Position the bitmap so its anchor point aligns with the 'avgAnchorX_relativeToBitmap' (our target alignment point).
            // b) Apply the `globalShiftX/Y` to center the whole aligned content within the `gifFrameWidth/Height`.
            // c) Apply the `currentOverallWiggleOffset` to introduce the overall wiggle effect.
            val drawX = (avgAnchorXrelativeToBitmap - currentAnchor.x) + globalShiftX + currentOverallWiggleOffset.x
            val drawY = (avgAnchorYrelativeToBitmap - currentAnchor.y) + globalShiftY + currentOverallWiggleOffset.y

            // Draw the current cropped bitmap (which is ONE vertical slice/frame) onto the new frame
            canvas.drawBitmap(currentBitmap, drawX, drawY, null)

            gifFrames.add(frameBitmap)

            // It's good practice to recycle bitmaps when you're done with them if you
            // create many temporary ones and they are not needed after being added to the list.
            // However, since `gifFrames` holds them, they shouldn't be recycled until the GIF is compiled.
        }

        return gifFrames
    }

    /**
     * Saves the generated GIF byte array to a file in the Pictures/wigglegrams directory.
     * Notifies the MediaScanner so the GIF appears in the gallery.
     * @param context The application context.
     * @param gifBytes The byte array of the GIF data.
     * @return The File object if successful, null otherwise.
     */
    private suspend fun saveGifToFile(context: Context, gifBytes: ByteArray): File? {
        return withContext(Dispatchers.IO) {
            val directory = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "wigglegrams"
            )
            if (!directory.exists()) {
                directory.mkdirs() // Create the directory if it doesn't exist
            }

            val fileName = "wigglegram_${System.currentTimeMillis()}.gif"
            val file = File(directory, fileName)

            try {
                FileOutputStream(file).use { fos ->
                    fos.write(gifBytes)
                }
                // Notify the media scanner about the new file so it appears in the gallery
                MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
                file
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    /**
     * Resets the app state to the initial image selection screen.
     */
    fun startOver() {
        _currentAppState.value = AppState.SELECT_IMAGE
        originalImageUri = null
        scaledBitmap = null
        cropSegments = 2
        croppedBitmaps = emptyList()
        anchorPoints = emptyList()
        currentAnchorImageIndex = 0
        gifUri = null
        gifCompilationProgress = 0
    }

    /**
     * Helper function to convert a content URI to a Bitmap.
     * @param context The application context.
     * @param uri The content URI of the image.
     * @return The decoded Bitmap, or null if an error occurs.
     */
    private fun uriToBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Helper function to scale a Bitmap to a maximum height of 2160 pixels,
     * maintaining its aspect ratio.
     * @param bitmap The original Bitmap to scale.
     * @return The scaled Bitmap, or the original if no scaling is needed.
     */
    private fun scaleBitmap(bitmap: Bitmap): Bitmap {
        val maxHeight = 2160
        if (bitmap.height <= maxHeight) {
            return bitmap // No scaling needed if already within limits
        }

        val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val newWidth = (maxHeight * aspectRatio).roundToInt()
        return bitmap.scale(newWidth, maxHeight)
    }
}

// Main Activity for the Ugh app
class MainActivity : ComponentActivity() {

    // Lazily initialize the ViewModel using the by viewModels() delegate
    private val viewModel: UghViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Apply the UghTheme (which includes MaterialTheme and dark theme support)
            UghTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // The main Composable for the application, passing the ViewModel
                    UghApp(viewModel = viewModel)
                }
            }
        }
    }
}

/**
 * The main Composable function that orchestrates the entire Ugh application flow.
 * It observes the ViewModel's state and displays the appropriate screen.
 * @param viewModel The UghViewModel instance.
 */
@Composable
fun UghApp(viewModel: UghViewModel) {
    val context = LocalContext.current
    // Observe the current application state from the ViewModel's StateFlow
    val appState by viewModel.currentAppState.collectAsState()

    // Launcher for requesting runtime permissions
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(context, "Permission denied. Cannot pick images or save GIFs.", Toast.LENGTH_LONG).show()
        }
    }

    // Effect to check and request necessary permissions when the app starts
    LaunchedEffect(Unit) {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // For Android 13 (API 33) and above, use READ_MEDIA_IMAGES
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            // For Android 12 (API 31) and below, use READ_EXTERNAL_STORAGE
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        // Check if the permission is already granted
        if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(permission) // Request the permission
        }
    }

    // New LaunchedEffect to handle the onion skin logic
    LaunchedEffect(viewModel.currentAnchorImageIndex) {
        val currentImageIndex = viewModel.currentAnchorImageIndex
        if (currentImageIndex > 0 && currentImageIndex < viewModel.croppedBitmaps.size) {
            // If it's not the first image, set the previous image as the onion skin
            viewModel.setNextOnionSkin(viewModel.croppedBitmaps[currentImageIndex - 1])
        } else {
            // If it's the first image, there is no onion skin
            viewModel.setNextOnionSkin(null)
        }
    }

    // Display different screens based on the current application state
    when (appState) {
        UghViewModel.AppState.SELECT_IMAGE -> {
            ImageSelectionScreen(onImageSelected = { uri ->
                viewModel.setSelectedImageUri(uri, context)
            })
        }
        UghViewModel.AppState.CROP_IMAGE -> {
            // Ensure scaledBitmap is not null before displaying the crop screen
            viewModel.scaledBitmap?.let { bitmap ->
                ImageCropScreen(
                    bitmap = bitmap,
                    selectedSegments = viewModel.cropSegments,
                    onSegmentsSelected = { segments -> viewModel.cropSegments = segments },
                    onConfirmCrop = { viewModel.performCrop() }
                )
            } ?: run {
                // If bitmap is unexpectedly null, show a toast and restart the flow
                LaunchedEffect(Unit) {
                    Toast.makeText(context, "Image not loaded, please try again.", Toast.LENGTH_SHORT).show()
                    viewModel.startOver()
                }
            }
        }
        UghViewModel.AppState.SELECT_ANCHOR_POINTS -> {
            val currentImageIndex = viewModel.currentAnchorImageIndex
            // Ensure there's a cropped image at the current index
            if (currentImageIndex < viewModel.croppedBitmaps.size) {
                AnchorPointSelectionScreen(
                    // Pass the current cropped bitmap from the ViewModel's list
                    bitmap = viewModel.croppedBitmaps[currentImageIndex],
                    // Pass the current anchor point from the ViewModel's list
                    onAnchorPointChanged = { offset -> viewModel.updateCurrentAnchorPoint(offset) },
                    onConfirmAnchorPoint = { viewModel.confirmAnchorPoint(context) },
                    imageIndex = currentImageIndex + 1, // Display 1-based index
                    totalImages = viewModel.croppedBitmaps.size,
                    onionSkinBitmap = viewModel.onionSkinBitmap.value,
                    onionSkinAnchorPoint = viewModel.anchorPoints.getOrNull(currentImageIndex - 1),
                    onTransparencyChanged = { value -> viewModel.setOnionSkinTransparency(value) },
                    transparency = viewModel.onionSkinTransparency.value
                )
            } else {
                // Fallback for unexpected state
                LaunchedEffect(Unit) {
                    Toast.makeText(context, "Error: No image to select anchor point.", Toast.LENGTH_SHORT).show()
                    viewModel.startOver()
                }
            }
        }
        UghViewModel.AppState.COMPILING_GIF -> {
            GifCompilationScreen(progress = viewModel.gifCompilationProgress)
        }
        UghViewModel.AppState.PREVIEW_GIF -> {
            // Ensure gifUri is not null before displaying the preview screen
            viewModel.gifUri?.let { uri ->
                GifPreviewScreen(
                    gifUri = uri,
                    onStartOver = viewModel::startOver
                )
            } ?: run {
                // If GIF URI is unexpectedly null, show a toast and restart the flow
                LaunchedEffect(Unit) {
                    Toast.makeText(context, "GIF not generated, please try again.", Toast.LENGTH_SHORT).show()
                    viewModel.startOver()
                }
            }
        }
    }
}

/**
 * Composable screen for selecting an image from the gallery.
 * @param onImageSelected Callback function when an image URI is selected.
 */
@OptIn(ExperimentalMaterial3Api::class) // Required for PickVisualMediaRequest
@Composable
fun ImageSelectionScreen(onImageSelected: (Uri?) -> Unit) {
    val pickMediaLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        onImageSelected(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Wigglegram Creator",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        Button(
            onClick = {
                // Launch the media picker to select only images
                pickMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            modifier = Modifier
                .fillMaxWidth(0.6f) // 60% of screen width
                .height(80.dp)
                .clip(RoundedCornerShape(16.dp)), // Rounded corners for the button
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(
                imageVector = Icons.Default.Add, // Plus icon
                contentDescription = "Upload Image",
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("Upload Image", fontSize = 20.sp)
        }
    }
}

/**
 * Composable screen for cropping the selected image vertically.
 * @param bitmap The Bitmap of the image to be cropped.
 * @param selectedSegments The currently selected number of segments (2, 3, or 4).
 * @param onSegmentsSelected Callback when a new number of segments is selected.
 * @param onConfirmCrop Callback when the crop is confirmed.
 */
@Composable
fun ImageCropScreen(
    bitmap: Bitmap,
    selectedSegments: Int,
    onSegmentsSelected: (Int) -> Unit,
    onConfirmCrop: () -> Unit
) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    var imageSize by remember { mutableStateOf(IntSize.Zero) }

    Scaffold(
        bottomBar = {
            // This content will be pinned to the bottom of the screen
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp) // Apply padding to the bottom bar itself
            ) {
                Button(
                    onClick = onConfirmCrop,
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(50.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Confirm Crop", fontSize = 18.sp)
                }
            }
        }
    ) { innerPadding ->
        // This is the main content area of the screen
        Column(
            modifier = Modifier
                .fillMaxSize() // Fill the available space, considering Scaffold's padding
                .padding(innerPadding) // Crucial: Apply the padding from Scaffold
                .padding(horizontal = 16.dp), // Add horizontal padding for a clean look
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top // Align content to the top
        ) {
            Text(
                text = "Crop Image",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f) // This is the key: make the image box take up available space
                    .aspectRatio(imageBitmap.width.toFloat() / imageBitmap.height.toFloat())
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.DarkGray)
                    .onGloballyPositioned { coordinates ->
                        imageSize = coordinates.size
                    }
            ) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = "Selected Image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )

                // Overlay lines for cropping preview
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    val numLines = selectedSegments - 1
                    if (numLines > 0) {
                        val segmentWidthPx = imageSize.width.toFloat() / selectedSegments

                        for (i in 1..numLines) {
                            val x = i * segmentWidthPx
                            drawLine(
                                color = Color.Red,
                                start = Offset(x, 0f),
                                end = Offset(x, size.height),
                                strokeWidth = 2.dp.toPx(),
                                alpha = 0.7f
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Crop into vertical segments:",
                style = MaterialTheme.typography.titleMedium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(2, 3, 4).forEach { segments ->
                    Button(
                        onClick = { onSegmentsSelected(segments) },
                        colors = if (selectedSegments == segments) {
                            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        } else {
                            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        },
                        modifier = Modifier.padding(4.dp)
                    ) {
                        Text("$segments segments")
                    }
                }
            }
        }
    }
}

@Composable
fun AnchorPointSelectionScreen(
    bitmap: Bitmap,
    onAnchorPointChanged: (Offset) -> Unit,
    onConfirmAnchorPoint: () -> Unit,
    imageIndex: Int,
    totalImages: Int,
    onionSkinBitmap: Bitmap?,
    onionSkinAnchorPoint: Offset?,
    onTransparencyChanged: (Float) -> Unit,
    transparency: Float,
) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val onionSkinImageBitmap = remember(onionSkinBitmap) { onionSkinBitmap?.asImageBitmap() }

    var currentImageOffset by remember { mutableStateOf(Offset.Zero) }
    var imageSize by remember { mutableStateOf(IntSize.Zero) }

    // This is the fixed center of the display box where the crosshair is.
    val displayCrosshairCenter = remember(imageSize) {
        if (imageSize != IntSize.Zero) {
            Offset(imageSize.width / 2f, imageSize.height / 2f)
        } else {
            Offset.Zero
        }
    }

    Scaffold(
        bottomBar = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                if (onionSkinBitmap != null) {
                    Text(
                        text = "Onion Skin Transparency",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Slider(
                        value = transparency,
                        onValueChange = onTransparencyChanged,
                        valueRange = 0f..1f,
                        modifier = Modifier.fillMaxWidth(0.8f)
                    )
                    Spacer(Modifier.height(16.dp))
                }

                Button(
                    onClick = onConfirmAnchorPoint,
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(50.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Confirm Anchor Point", fontSize = 18.sp)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Align Anchor Point for Image $imageIndex of $totalImages",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(imageBitmap.width.toFloat() / imageBitmap.height.toFloat())
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.DarkGray)
                    .onGloballyPositioned { coordinates ->
                        imageSize = coordinates.size
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            currentImageOffset += dragAmount

                            // Calculate the anchor point relative to the TOP-LEFT of the image.
                            // The crosshair is at displayCrosshairCenter.
                            // The image's current top-left is at currentImageOffset.
                            // So, the anchor point *on the image itself* is:
                            val anchorPointOnImage = displayCrosshairCenter - currentImageOffset
                            onAnchorPointChanged(anchorPointOnImage)
                        }
                    }
            ) {

                if (onionSkinImageBitmap != null && onionSkinAnchorPoint != null) {
                    Image(
                        bitmap = onionSkinImageBitmap,
                        contentDescription = "Previous Image (Onion Skin)",
                        modifier = Modifier
                            .fillMaxSize()
                            .offset {
                                // To align the onion skin's anchor point with the crosshair:
                                // The onion skin image needs to be shifted so that its
                                // onionSkinAnchorPoint aligns with displayCrosshairCenter.
                                val xOffset = displayCrosshairCenter.x - onionSkinAnchorPoint.x
                                val yOffset = displayCrosshairCenter.y - onionSkinAnchorPoint.y
                                IntOffset(xOffset.roundToInt(), yOffset.roundToInt())
                            },
                        contentScale = ContentScale.Fit
                    )
                }

                Image(
                    bitmap = imageBitmap,
                    contentDescription = "Current Image $imageIndex",
                    modifier = Modifier
                        .fillMaxSize()
                        .offset {
                            IntOffset(
                                currentImageOffset.x.roundToInt(),
                                currentImageOffset.y.roundToInt()
                            )
                        }
                        .alpha(transparency),
                    contentScale = ContentScale.Fit
                )

                // The fixed Red Crosshair is drawn on top of everything.
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    val crosshairSize = 30.dp.toPx()
                    val strokeWidth = 2.dp.toPx()
                    val crosshairColor = Color.Red
                    drawLine(
                        color = crosshairColor,
                        start = Offset(displayCrosshairCenter.x - crosshairSize / 2, displayCrosshairCenter.y),
                        end = Offset(displayCrosshairCenter.x + crosshairSize / 2, displayCrosshairCenter.y),
                        strokeWidth = strokeWidth
                    )
                    drawLine(
                        color = crosshairColor,
                        start = Offset(displayCrosshairCenter.x, displayCrosshairCenter.y - crosshairSize / 2),
                        end = Offset(displayCrosshairCenter.x, displayCrosshairCenter.y + crosshairSize / 2),
                        strokeWidth = strokeWidth
                    )
                }
            }
        }
    }
}
@Composable
fun GifCompilationScreen(progress: Int) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Compiling Wigglegram...",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        // Linear progress indicator
        LinearProgressIndicator(
            progress = { progress / 100f }, // Correct syntax for Material3
            modifier = Modifier
                .fillMaxWidth(0.8f) // 80% of screen width
                .height(12.dp)
                .clip(RoundedCornerShape(8.dp)),
            color = MaterialTheme.colorScheme.primary, // Primary color for the progress bar
            trackColor = MaterialTheme.colorScheme.primaryContainer // Background track color
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "$progress%", // Display percentage
            style = MaterialTheme.typography.titleLarge
        )
    }
}

@Composable
fun GifPreviewScreen(
    gifUri: Uri,
    onStartOver: () -> Unit
) {
    val context = LocalContext.current
    val contentResolver = context.contentResolver // Get ContentResolver

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Wigglegram Ready!",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        AndroidView(
            factory = { ctx ->
                android.widget.ImageView(ctx).apply {
                    setBackgroundColor(android.graphics.Color.BLACK)
                    scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                }
            },
            update = { imageView ->
                Glide.with(context)
                    .asGif()
                    .load(gifUri)
                    .into(imageView)
            },
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.DarkGray)
        )

        Spacer(Modifier.height(24.dp))

        // MODIFIED BUTTON: Now a "Save" button
        Button(
            onClick = {
                // Logic to save the GIF to the public gallery
                try {
                    val fileName = "Wigglegram_${System.currentTimeMillis()}.gif"
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/gif")
                        // For Android 10 (API 29) and above, use MediaStore.Images.Media.RELATIVE_PATH
                        // For older Android, consider using Environment.getExternalStoragePublicDirectory
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + "Wigglegrams")
                        }
                        put(MediaStore.MediaColumns.IS_PENDING, 1) // Mark as pending
                    }

                    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    } else {
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    }

                    val newUri = contentResolver.insert(collection, contentValues)

                    newUri?.let { uri ->
                        contentResolver.openOutputStream(uri)?.use { outputStream ->
                            context.contentResolver.openInputStream(gifUri)?.use { inputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            contentValues.clear()
                            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                            contentResolver.update(uri, contentValues, null, null)
                        }

                        Toast.makeText(context, "Wigglegram saved to Gallery!", Toast.LENGTH_LONG).show()
                    } ?: run {
                        Toast.makeText(context, "Failed to create new GIF file in Gallery.", Toast.LENGTH_LONG).show()
                    }

                } catch (e: Exception) {
                    Log.e("GifPreviewScreen", "Error saving GIF: ${e.message}", e)
                    Toast.makeText(context, "Error saving Wigglegram.", Toast.LENGTH_LONG).show()
                }
            },
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(50.dp)
                .clip(RoundedCornerShape(12.dp)),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("Save Wigglegram", fontSize = 18.sp) // Changed button text
        }

        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = onStartOver,
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(50.dp)
                .clip(RoundedCornerShape(12.dp)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
            border = ButtonDefaults.outlinedButtonBorder(enabled = true)
        ) {
            Text("Start Over", fontSize = 18.sp)
        }
    }
}

/**
 * Preview Composable for the main app's initial screen.
 */
@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    UghTheme {
        // Show the ImageSelectionScreen in the preview
        ImageSelectionScreen(onImageSelected = {})
    }
}