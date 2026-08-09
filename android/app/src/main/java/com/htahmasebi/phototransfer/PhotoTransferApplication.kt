package com.htahmasebi.phototransfer

import android.app.Application
import android.content.Context
import com.htahmasebi.phototransfer.discovery.ReceiverDiscovery
import com.htahmasebi.phototransfer.picker.SelectedFileResolver
import com.htahmasebi.phototransfer.transfer.ContentUriRequestBody
import com.htahmasebi.phototransfer.transfer.HttpTransferClient
import com.htahmasebi.phototransfer.transfer.TransferCoordinator
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient

class PhotoTransferApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/**
 * Process-scoped dependencies. The transfer coordinator lives here so an
 * in-flight transfer survives configuration changes.
 */
class AppContainer(context: Context) {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(0, TimeUnit.SECONDS)
        .build()

    val selectedFileResolver = SelectedFileResolver(context.contentResolver)

    val receiverDiscovery = ReceiverDiscovery(context)

    val transferCoordinator = TransferCoordinator(
        client = HttpTransferClient(
            httpClient = httpClient,
            bodyFactory = { file ->
                ContentUriRequestBody(
                    contentResolver = context.contentResolver,
                    uri = file.uri,
                    contentType = file.mediaType.toMediaTypeOrNull(),
                    declaredLength = file.size,
                )
            },
            ioDispatcher = Dispatchers.IO,
        ),
        scope = applicationScope,
    )
}
