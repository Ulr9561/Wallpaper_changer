package com.example.backgroundtask

import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.work.Worker
import androidx.work.WorkerParameters
import kotlin.random.Random

class WallpaperWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {
    companion object {
        private const val TAG = "WallpaperWorker"
    }

    override fun doWork(): Result {
        Log.d(TAG, "Début de l'exécution du Worker")

        val folderUriString = inputData.getString("folder_uri")
        if (folderUriString == null) {
            Log.e(TAG, "URI du dossier non fourni")
            return Result.failure()
        }

        val folderUri = Uri.parse(folderUriString)

        return try {
            Log.d(TAG, "Chargement des images depuis: ${folderUri.path}")

            val documentFile = DocumentFile.fromTreeUri(appContext, folderUri)
            val wallpapers = documentFile?.listFiles()
                ?.filter { it.type?.startsWith("image/") == true }
                ?.toList()

            if (wallpapers.isNullOrEmpty()) {
                Log.d(TAG, "Aucune image trouvée dans le dossier.")
                return Result.failure()
            }

            val randomWallpaper = wallpapers[Random.nextInt(wallpapers.size)]
            Log.d(TAG, "Image sélectionnée: ${randomWallpaper.name}")

            appContext.contentResolver.openInputStream(randomWallpaper.uri)?.use { stream ->
                val bitmap = BitmapFactory.decodeStream(stream)
                val wallpaperManager = WallpaperManager.getInstance(appContext)
                wallpaperManager.setBitmap(bitmap)
                Log.d(TAG, "Fond d'écran changé avec succès.")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors du changement de fond d'écran : ${e.message}", e)
            Result.failure()
        }
    }
}