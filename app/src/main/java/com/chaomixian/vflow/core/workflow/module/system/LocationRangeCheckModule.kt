package com.chaomixian.vflow.core.workflow.module.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.execution.ExecutionContext
import com.chaomixian.vflow.core.module.ActionMetadata
import com.chaomixian.vflow.core.module.BaseModule
import com.chaomixian.vflow.core.module.ExecutionResult
import com.chaomixian.vflow.core.module.InputDefinition
import com.chaomixian.vflow.core.module.ModuleUIProvider
import com.chaomixian.vflow.core.module.OutputDefinition
import com.chaomixian.vflow.core.module.ParameterType
import com.chaomixian.vflow.core.module.ProgressUpdate
import com.chaomixian.vflow.core.module.ValidationResult
import com.chaomixian.vflow.core.types.VTypeRegistry
import com.chaomixian.vflow.core.types.basic.VBoolean
import com.chaomixian.vflow.core.types.basic.VNumber
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.ui.workflow_editor.PillUtil
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal object LocationRangeMath {
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    fun distanceMeters(
        startLatitude: Double,
        startLongitude: Double,
        endLatitude: Double,
        endLongitude: Double
    ): Double {
        val startLatRad = Math.toRadians(startLatitude)
        val endLatRad = Math.toRadians(endLatitude)
        val latitudeDelta = Math.toRadians(endLatitude - startLatitude)
        val longitudeDelta = Math.toRadians(endLongitude - startLongitude)

        val a = sin(latitudeDelta / 2) * sin(latitudeDelta / 2) +
            cos(startLatRad) * cos(endLatRad) *
            sin(longitudeDelta / 2) * sin(longitudeDelta / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }
}

class LocationRangeCheckModule : BaseModule() {
    companion object {
        private const val DEFAULT_LATITUDE = 39.9042
        private const val DEFAULT_LONGITUDE = 116.4074
        private const val DEFAULT_RADIUS_METERS = 500.0
        private const val LOCATION_TIMEOUT_MS = 4_000L
        private const val RECENT_LAST_KNOWN_MAX_AGE_MS = 2 * 60 * 1000L
        private val ACTIVE_PROVIDER_PRIORITY = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER
        )
        private val LAST_KNOWN_PROVIDER_PRIORITY = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
    }

    override val id = "vflow.system.location_range_check"
    override val metadata = ActionMetadata(
        nameStringRes = R.string.module_vflow_system_location_range_check_name,
        descriptionStringRes = R.string.module_vflow_system_location_range_check_desc,
        name = "位置范围判断",
        description = "获取当前位置并判断是否在设定坐标半径内",
        iconRes = R.drawable.rounded_alt_route_24,
        category = "应用与系统",
        categoryId = "device"
    )

    override val requiredPermissions = listOf(PermissionManager.LOCATION)
    override val uiProvider: ModuleUIProvider = LocationRangeCheckModuleUIProvider()

    override fun getInputs(): List<InputDefinition> = listOf(
        InputDefinition(
            id = "latitude",
            name = "纬度",
            staticType = ParameterType.NUMBER,
            defaultValue = DEFAULT_LATITUDE,
            nameStringRes = R.string.param_vflow_system_location_range_check_latitude_name
        ),
        InputDefinition(
            id = "longitude",
            name = "经度",
            staticType = ParameterType.NUMBER,
            defaultValue = DEFAULT_LONGITUDE,
            nameStringRes = R.string.param_vflow_system_location_range_check_longitude_name
        ),
        InputDefinition(
            id = "radius",
            name = "半径",
            staticType = ParameterType.NUMBER,
            defaultValue = DEFAULT_RADIUS_METERS,
            nameStringRes = R.string.param_vflow_system_location_range_check_radius_name
        ),
        InputDefinition(
            id = "location_name",
            name = "位置名称",
            staticType = ParameterType.STRING,
            defaultValue = "",
            nameStringRes = R.string.param_vflow_system_location_range_check_location_name_name
        )
    )

    override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
        OutputDefinition(
            id = "inside",
            name = "是否在范围内",
            typeName = VTypeRegistry.BOOLEAN.id,
            nameStringRes = R.string.output_vflow_system_location_range_check_inside_name
        ),
        OutputDefinition(
            id = "latitude",
            name = "当前纬度",
            typeName = VTypeRegistry.NUMBER.id,
            nameStringRes = R.string.output_vflow_system_location_range_check_latitude_name
        ),
        OutputDefinition(
            id = "longitude",
            name = "当前经度",
            typeName = VTypeRegistry.NUMBER.id,
            nameStringRes = R.string.output_vflow_system_location_range_check_longitude_name
        ),
        OutputDefinition(
            id = "accuracy",
            name = "定位精度",
            typeName = VTypeRegistry.NUMBER.id,
            nameStringRes = R.string.output_vflow_system_location_range_check_accuracy_name
        ),
        OutputDefinition(
            id = "distance",
            name = "距离目标点",
            typeName = VTypeRegistry.NUMBER.id,
            nameStringRes = R.string.output_vflow_system_location_range_check_distance_name
        )
    )

    override fun validate(step: ActionStep, allSteps: List<ActionStep>): ValidationResult {
        val latitude = (step.parameters["latitude"] as? Number)?.toDouble()
            ?: return ValidationResult(false, "纬度必须是数字")
        val longitude = (step.parameters["longitude"] as? Number)?.toDouble()
            ?: return ValidationResult(false, "经度必须是数字")
        val radius = (step.parameters["radius"] as? Number)?.toDouble()
            ?: return ValidationResult(false, "半径必须是数字")

        return when {
            latitude !in -90.0..90.0 -> ValidationResult(false, "纬度必须在 -90 到 90 之间")
            longitude !in -180.0..180.0 -> ValidationResult(false, "经度必须在 -180 到 180 之间")
            radius < 0.0 -> ValidationResult(false, "半径不能小于 0")
            else -> ValidationResult(true)
        }
    }

    override fun getSummary(context: Context, step: ActionStep): CharSequence {
        val latitude = (step.parameters["latitude"] as? Number)?.toDouble() ?: DEFAULT_LATITUDE
        val longitude = (step.parameters["longitude"] as? Number)?.toDouble() ?: DEFAULT_LONGITUDE
        val radius = (step.parameters["radius"] as? Number)?.toDouble() ?: DEFAULT_RADIUS_METERS
        val locationName = step.parameters["location_name"] as? String ?: ""

        val locationText = if (locationName.isNotBlank()) {
            locationName
        } else {
            context.getString(
                R.string.summary_vflow_system_location_range_check_coordinates,
                latitude,
                longitude,
                radius.toInt()
            )
        }
        val locationPill = PillUtil.Pill(locationText, "location")

        return PillUtil.buildSpannable(
            context,
            context.getString(R.string.summary_vflow_system_location_range_check_prefix),
            " ",
            locationPill
        )
    }

    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        val targetLatitude = context.getVariableAsNumber("latitude") ?: DEFAULT_LATITUDE
        val targetLongitude = context.getVariableAsNumber("longitude") ?: DEFAULT_LONGITUDE
        val radiusMeters = context.getVariableAsNumber("radius") ?: DEFAULT_RADIUS_METERS

        if (targetLatitude !in -90.0..90.0) {
            return ExecutionResult.Failure("参数错误", "纬度必须在 -90 到 90 之间")
        }
        if (targetLongitude !in -180.0..180.0) {
            return ExecutionResult.Failure("参数错误", "经度必须在 -180 到 180 之间")
        }
        if (radiusMeters < 0.0) {
            return ExecutionResult.Failure("参数错误", "半径不能小于 0")
        }

        if (!hasLocationPermission(appContext)) {
            return ExecutionResult.Failure("权限不足", "未授予定位权限")
        }

        onProgress(ProgressUpdate("正在获取当前位置"))

        val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return ExecutionResult.Failure("定位失败", "无法获取定位服务")

        val currentLocation = getBestLocation(locationManager)
            ?: return ExecutionResult.Failure("定位失败", "无法获取当前位置，请确认定位服务已开启")

        val distanceMeters = LocationRangeMath.distanceMeters(
            startLatitude = currentLocation.latitude,
            startLongitude = currentLocation.longitude,
            endLatitude = targetLatitude,
            endLongitude = targetLongitude
        )
        val inside = distanceMeters <= radiusMeters

        onProgress(
            ProgressUpdate(
                if (inside) "当前位置在设定范围内" else "当前位置不在设定范围内"
            )
        )

        return ExecutionResult.Success(
            outputs = mapOf(
                "inside" to VBoolean(inside),
                "latitude" to VNumber(currentLocation.latitude),
                "longitude" to VNumber(currentLocation.longitude),
                "accuracy" to VNumber(currentLocation.accuracy.toDouble()),
                "distance" to VNumber(distanceMeters)
            )
        )
    }

    private fun hasLocationPermission(context: Context): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fineGranted || coarseGranted
    }

    private suspend fun getBestLocation(locationManager: LocationManager): Location? {
        val lastKnownLocation = selectBestLastKnownLocation(locationManager)
        if (lastKnownLocation != null && isRecent(lastKnownLocation)) {
            return lastKnownLocation
        }

        val enabledProviders = ACTIVE_PROVIDER_PRIORITY.filter { provider ->
            runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)
        }

        val freshLocation = requestCurrentLocationFromAnyProvider(locationManager, enabledProviders)
        if (freshLocation != null) {
            return freshLocation
        }

        return lastKnownLocation
    }

    private suspend fun requestCurrentLocationFromAnyProvider(
        locationManager: LocationManager,
        providers: List<String>
    ): Location? {
        if (providers.isEmpty()) return null

        return coroutineScope {
            val firstLocation = CompletableDeferred<Location?>()
            val remainingProviders = AtomicInteger(providers.size)

            val jobs = providers.map { provider ->
                launch {
                    val location = withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
                        requestSingleLocation(locationManager, provider)
                    }
                    if (location != null) {
                        firstLocation.complete(location)
                    } else {
                        if (remainingProviders.decrementAndGet() == 0) {
                            firstLocation.complete(null)
                        }
                    }
                }
            }

            val result = firstLocation.await()
            jobs.forEach { job -> job.cancel() }
            result
        }
    }

    private fun selectBestLastKnownLocation(locationManager: LocationManager): Location? {
        val lastKnownLocations = LAST_KNOWN_PROVIDER_PRIORITY.mapNotNull { provider ->
            runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
        }
        val recentLocations = lastKnownLocations.filter(::isRecent)
        return when {
            recentLocations.isNotEmpty() -> recentLocations.minByOrNull { it.accuracy }
            else -> lastKnownLocations.maxByOrNull { it.time }
        }
    }

    private fun isRecent(location: Location): Boolean {
        return System.currentTimeMillis() - location.time <= RECENT_LAST_KNOWN_MAX_AGE_MS
    }

    private suspend fun requestSingleLocation(
        locationManager: LocationManager,
        provider: String
    ): Location? = suspendCancellableCoroutine { continuation ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val cancellationSignal = CancellationSignal()
            continuation.invokeOnCancellation { cancellationSignal.cancel() }
            try {
                locationManager.getCurrentLocation(
                    provider,
                    cancellationSignal,
                    appContext.mainExecutor
                ) { location ->
                    if (continuation.isActive) {
                        continuation.resume(location)
                    }
                }
            } catch (_: SecurityException) {
                if (continuation.isActive) {
                    continuation.resume(null)
                }
            }
            return@suspendCancellableCoroutine
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                locationManager.removeUpdates(this)
                if (continuation.isActive) {
                    continuation.resume(location)
                }
            }

            override fun onProviderEnabled(provider: String) = Unit

            override fun onProviderDisabled(provider: String) = Unit

            @Deprecated("Deprecated in Android API")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }

        continuation.invokeOnCancellation {
            runCatching { locationManager.removeUpdates(listener) }
        }

        Handler(Looper.getMainLooper()).post {
            try {
                locationManager.requestLocationUpdates(
                    provider,
                    0L,
                    0f,
                    listener,
                    Looper.getMainLooper()
                )
            } catch (_: SecurityException) {
                runCatching { locationManager.removeUpdates(listener) }
                if (continuation.isActive) {
                    continuation.resume(null)
                }
            }
        }
    }
}
