package com.example.googleAttractionsGpx.data.repository

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import androidx.core.content.FileProvider
import net.osmand.aidlapi.IOsmAndAidlInterface
import net.osmand.aidlapi.gpx.ASelectedGpxFile
import net.osmand.aidlapi.gpx.AGpxFile
import net.osmand.aidlapi.gpx.ImportGpxParams
import java.io.File

class OsmAndConnection(private val context: Context) {

    companion object {
        private const val TAG = "OsmAndConnection"
    }

    private var osmAndInterface: IOsmAndAidlInterface? = null
    private var boundPackage: String? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            osmAndInterface = IOsmAndAidlInterface.Stub.asInterface(service)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            osmAndInterface = null
            boundPackage = null
        }
    }

    fun isOsmAndInstalled(): Boolean {
        val pkg = getOsmAndPackage()
        Log.d(TAG, "isOsmAndInstalled: package=$pkg")
        return pkg != null
    }

    fun bind(onConnected: () -> Unit, onFailed: () -> Unit) {
        val pkg = getOsmAndPackage()
        Log.d(TAG, "bind: resolved package=$pkg")
        if (pkg == null) {
            Log.w(TAG, "bind: no OsmAnd package found")
            onFailed()
            return
        }
        val intent = Intent("net.osmand.aidl.OsmandAidlServiceV2")
        intent.setPackage(pkg)
        Log.d(TAG, "bind: binding to action=net.osmand.aidl.OsmandAidlServiceV2 package=$pkg")
        val bound = context.bindService(intent, object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                Log.d(TAG, "onServiceConnected: name=$name")
                osmAndInterface = IOsmAndAidlInterface.Stub.asInterface(service)
                boundPackage = pkg
                onConnected()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                Log.d(TAG, "onServiceDisconnected")
                osmAndInterface = null
                boundPackage = null
            }
        }, Context.BIND_AUTO_CREATE)
        Log.d(TAG, "bind: bindService returned $bound")
        if (!bound) {
            Log.w(TAG, "bind: bindService failed")
            onFailed()
        }
    }

    data class TrackResult(val tracks: List<String>, val diagnostics: String)

    fun getActiveTracks(): TrackResult {
        val iface = osmAndInterface
        if (iface == null) {
            return TrackResult(emptyList(), "AIDL interface is null (not bound)")
        }
        val files = mutableListOf<ASelectedGpxFile>()
        return try {
            val result = iface.getActiveGpx(files)
            Log.d(TAG, "getActiveGpx returned $result, files.size=${files.size}")
            val diag = "getActiveGpx: result=$result, count=${files.size}" +
                files.joinToString { "\n  - ${it.fileName}" }
            TrackResult(files.mapNotNull { it.fileName }, diag)
        } catch (e: Exception) {
            Log.e(TAG, "getActiveGpx failed", e)
            TrackResult(emptyList(), "getActiveGpx exception: ${e.message}")
        }
    }

    fun getImportedTracks(): TrackResult {
        val iface = osmAndInterface
        if (iface == null) {
            return TrackResult(emptyList(), "AIDL interface is null (not bound)")
        }
        val files = mutableListOf<AGpxFile>()
        return try {
            val result = iface.getImportedGpx(files)
            Log.d(TAG, "getImportedGpx returned $result, files.size=${files.size}")
            val diag = "getImportedGpx: result=$result, count=${files.size}" +
                files.joinToString { "\n  - ${it.fileName} (active=${it.isActive})" }
            TrackResult(files.mapNotNull { it.fileName }, diag)
        } catch (e: Exception) {
            Log.e(TAG, "getImportedGpx failed", e)
            TrackResult(emptyList(), "getImportedGpx exception: ${e.message}")
        }
    }

    fun importGpx(file: File, fileName: String, show: Boolean = true): Boolean {
        val iface = osmAndInterface ?: return false
        val uri = FileProvider.getUriForFile(
            context, "com.example.googleAttractionsGpx.fileProvider", file
        )
        val pkg = boundPackage ?: return false
        context.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return try {
            val params = ImportGpxParams(uri, fileName, "", show)
            iface.importGpx(params)
        } catch (e: Exception) {
            false
        }
    }

    fun unbind() {
        try {
            context.unbindService(serviceConnection)
        } catch (_: Exception) {}
        osmAndInterface = null
        boundPackage = null
    }

    private fun getOsmAndPackage(): String? {
        val pm = context.packageManager
        return when {
            isPackageInstalled(pm, "net.osmand.plus") -> "net.osmand.plus"
            isPackageInstalled(pm, "net.osmand") -> "net.osmand"
            else -> null
        }
    }

    private fun isPackageInstalled(pm: PackageManager, pkg: String): Boolean {
        return try {
            pm.getPackageInfo(pkg, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }
}
