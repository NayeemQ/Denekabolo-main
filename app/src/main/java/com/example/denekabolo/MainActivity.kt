package com.example.denekabolo

import android.Manifest
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.denekabolo.databinding.ActivityMainBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.mlkit.nl.entityextraction.Entity
import com.google.mlkit.nl.entityextraction.EntityExtraction
import com.google.mlkit.nl.entityextraction.EntityExtractionParams
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions
import com.google.mlkit.nl.entityextraction.MoneyEntity
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.IOException
import java.math.RoundingMode
import java.text.DecimalFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

typealias LumaListener = (luma: String) -> Unit

class MainActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityMainBinding

    private lateinit var cameraExecutor: ExecutorService

    private var defaultDetectedPriceText: String = "Looking for Price..."
    private var defaultFinalPriceText: String = ""
    private var defaultZipCodeText: String = ""
    private var defaultTaxRateText: String = ""

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var loca: String = ""





    // MLKit Object detection
    // Analyzer

    private inner class PriceAnalyzer(private val listener: LumaListener) : ImageAnalysis.Analyzer {

        val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        @OptIn(ExperimentalGetImage::class)
        override fun analyze(imageproxy : ImageProxy)
        {
            val mediaImage = imageproxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(
                    mediaImage,
                    imageproxy.imageInfo.rotationDegrees
                )
                textRecognizer.process(image)
                    .addOnSuccessListener { detectedText ->

                        if(detectedText.text.isNotEmpty()) {

                            // Entity Detector
                            val entityExtractor =
                                EntityExtraction.getClient(
                                    EntityExtractorOptions.Builder(EntityExtractorOptions.ENGLISH)
                                        .build())


                            entityExtractor
                                .downloadModelIfNeeded()
                                .addOnSuccessListener { _ ->

                                    val params =
                                        EntityExtractionParams.Builder(detectedText.text)
                                                .build()
                                                    entityExtractor
                                                    .annotate(params)
                                                .addOnSuccessListener {entityAnnotations ->
                                                    // Annotation process was successful, you can parse the EntityAnnotations list here.
                                                    for (entityAnnotation in entityAnnotations) {
                                                        val entities: List<Entity> = entityAnnotation.entities

                                                        Log.d(TAG, "Range: ${entityAnnotation.start} - ${entityAnnotation.end}")
                                                        for (entity in entities) {
                                                            when (entity) {

                                                                is MoneyEntity -> {

                                                                    var moneyValue : Float = entity.integerPart.toFloat()
                                                                    moneyValue += (entity.fractionalPart.toFloat()/100.0f)

                                                                    viewBinding.detectedPrice.text = "Scanned Price: " + entity.unnormalizedCurrency + moneyValue.toString()

                                                                    var taxPercent = 8.875f // TODO Get this value from API (Ex: Avalara)
                                                                    //var zipCode = 10001 // TODO Get this value from API (Location Services)
                                                                    var zipCode = loca

                                                                    var finalValue = moneyValue
                                                                    finalValue += (moneyValue*taxPercent/100.0f)

                                                                    var outputString = "Post-tax Price "

                                                                    // Tip %
                                                                    if(viewBinding.tipCB.isChecked && viewBinding.tipET.text.isNotBlank())
                                                                    {
                                                                        var tipPercent = viewBinding.tipET.text.toString().toFloat()
                                                                        finalValue += (moneyValue*tipPercent/100.0f)

                                                                        outputString += " (+Tip) "
                                                                    }

                                                                    // CCFees %
                                                                    if(viewBinding.CCFeesCB.isChecked && viewBinding.CCFeesET.text.isNotBlank())
                                                                    {
                                                                        var CCFeesPercent = viewBinding.CCFeesET.text.toString().toFloat()
                                                                        finalValue += (moneyValue*CCFeesPercent/100.0f)

                                                                        outputString += " (+CC) "
                                                                    }

                                                                    outputString += " : "

                                                                    val df = DecimalFormat("#.##")
                                                                    df.roundingMode = RoundingMode.CEILING


                                                                    viewBinding.finalPrice.text = outputString + entity.unnormalizedCurrency + df.format(finalValue).toString()

                                                                    viewBinding.zipCode.text = "Area Zip Code: " + zipCode.toString()
                                                                    viewBinding.taxRate.text = "Sales tax rate: " + taxPercent.toString() + "%"


                                                                    Log.d(TAG, "Currency: ${entity.unnormalizedCurrency}")
                                                                    Log.d(TAG, "Integer part: ${entity.integerPart}")
                                                                    Log.d(TAG, "Fractional Part: ${entity.fractionalPart}")
                                                                }
                                                                else -> {
                                                                    Log.d(TAG, "  $entity")
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                                .addOnFailureListener {
                                                    // Check failure message here.
                                                }

                                }
                                .addOnFailureListener { _ -> /* Model downloading failed. */ }
                        }
                        else
                        {
                            // Reset the values
                            viewBinding.detectedPrice.text = defaultDetectedPriceText
                            viewBinding.finalPrice.text = defaultFinalPriceText
                            viewBinding.zipCode.text = defaultZipCodeText
                            viewBinding.taxRate.text = defaultTaxRateText
                        }

                        imageproxy.close()
                    }

                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to detect!")
                        imageproxy.close()
                    }

            }
        }
    }

    val activityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions())
        { permissions ->
            // Handle Permission granted/rejected
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && it.value == false)
                    permissionGranted = false
            }
            if (!permissionGranted) {
                Toast.makeText(baseContext,
                    "Permission request denied",
                    Toast.LENGTH_SHORT).show()
            } else {
                startCamera()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)


        // Request camera permissions
        if (allPermissionsGranted()) {

            // Display this to notify user camera is ready to start scanning price tags
            viewBinding.detectedPrice.text = defaultDetectedPriceText

            startCamera()
            getLocation()
        } else {
            requestPermissions()
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    fun extractCurrencyAndAmount(input: String): Triple<String, String, Boolean> {
        val regex = Regex("""^([0-9]+)\s*([^0-9]*)|([^0-9]*?)\s*([0-9]+)$""")
        val matchResult = regex.find(input)

        return if (matchResult != null) {
            val (amountStart, afterStart, afterEnd, amountEnd) = matchResult.destructured

            if (amountStart.isNotEmpty()) Triple(amountStart, afterStart, true)
            else Triple(afterEnd, amountEnd, false)
        } else {
            //throw IllegalArgumentException("Invalid input format")
            Log.e(TAG, "Invalid input format")
            return Triple("0","0",false)
        }
    }

    private fun startCamera() {

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                }

            val imageAnalyzer = ImageAnalysis.Builder()
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, PriceAnalyzer { luma ->
                        //Toast.makeText(this@MainActivity, "$luma", Toast.LENGTH_SHORT).show()
                        //Log.d(TAG, "Average luminosity: $luma")
                    })
                }

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun getLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val geocoder = Geocoder(this, Locale.getDefault())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    geocoder.getFromLocation(location.latitude, location.longitude, 1, @RequiresApi(
                        Build.VERSION_CODES.TIRAMISU
                    )
                    object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            if (addresses.isNotEmpty()) {
                                val zipCode = addresses[0].postalCode

                                loca = zipCode
                            } else {
                                Toast.makeText(this@MainActivity, "Zip Code not found", Toast.LENGTH_SHORT).show()
                            }
                        }
                        override fun onError(errorMessage: String?) {
                            Toast.makeText(this@MainActivity, "Error retrieving zip code", Toast.LENGTH_SHORT).show()
                        }
                    })
                }
            } else {
                Toast.makeText(this, "Location not found", Toast.LENGTH_SHORT).show()
            }
        }
        }
    }



    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "Denekabolo"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                //locations
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }
}
