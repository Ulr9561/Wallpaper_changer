package com.example.backgroundtask

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.work.*
import coil3.compose.AsyncImage
import com.example.backgroundtask.ui.theme.BackgroundTaskTheme
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    companion object {
        private const val PERMISSION_REQUEST_CODE = 123
        private const val WORK_NAME = "wallpaper_work"
        private const val TAG = "MainActivity"
        private const val PREFS_NAME = "WallpaperPrefs"
        private const val FOLDER_URI_KEY = "folderUri"
    }

    private var selectedFolderUri by mutableStateOf<Uri?>(null)
    private var wallpaperFiles by mutableStateOf<List<DocumentFile>>(emptyList())
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            Log.d(TAG, "Nouveau dossier sélectionné: ${it.path}")
            // Persist permission
            contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            selectedFolderUri = it
            saveFolderUri(it)
            loadWallpapers(it)
        }
    }

    private fun saveFolderUri(uri: Uri) {
        Log.d(TAG, "Sauvegarde du chemin du dossier: ${uri.path}")
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(FOLDER_URI_KEY, uri.toString())
            .apply()
    }

    private fun loadSavedFolderUri() {
        Log.d(TAG, "Chargement du chemin sauvegardé")
        val savedUri = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(FOLDER_URI_KEY, null)
        savedUri?.let {
            selectedFolderUri = Uri.parse(it)
            loadWallpapers(Uri.parse(it))
        }
    }

    private fun loadWallpapers(uri: Uri) {
        Log.d(TAG, "Chargement des fonds d'écran depuis: ${uri.path}")
        scope.launch(Dispatchers.IO) {
            val documentFile = DocumentFile.fromTreeUri(this@MainActivity, uri)
            val files = documentFile?.listFiles()
                ?.filter { it.type?.startsWith("image/") == true }
                ?.toList() ?: emptyList()

            withContext(Dispatchers.Main) {
                wallpaperFiles = files
                Log.d(TAG, "${files.size} images trouvées")
            } }
    }

    private fun checkPermission() {
        Log.d(TAG, "Vérification des permissions")
        val readPermission = Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(
                this,
                readPermission
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(readPermission),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkPermission()
        loadSavedFolderUri()

        setContent {
            BackgroundTaskTheme {
                val context = LocalContext.current
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Folder selection button
                        Button(
                            onClick = { folderPicker.launch(null) },
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text("Sélectionner le dossier des fonds d'écran")
                        }

                        // Display selected folder path
                        selectedFolderUri?.let {
                            Text(
                                "Dossier sélectionné: ${it.path}",
                                modifier = Modifier.padding(8.dp)
                            )
                        }

                        // List of wallpapers
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            items(wallpaperFiles) { file ->
                                WallpaperItem(file = file)
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Button(
                                onClick = {
                                    scheduleWallpaperChange()
                                    Toast.makeText(context, "Tâche de changement de fond d'écran démarrée", Toast.LENGTH_SHORT).show() },
                                enabled = selectedFolderUri != null && wallpaperFiles.isNotEmpty()
                            ) {
                                Text("Démarrer")
                            }

                            Button(
                                onClick = {
                                    cancelWallpaperChange()
                                    Toast.makeText(context, "Tâche arrêtée", Toast.LENGTH_SHORT).show()},
                                enabled = selectedFolderUri != null
                            ) {
                                Text("Arrêter")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun scheduleWallpaperChange() {
        Log.d(TAG, "Programmation du changement de fond d'écran")
        selectedFolderUri?.let { uri ->
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<WallpaperWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf("folder_uri" to uri.toString()))
                .build()

            WorkManager.getInstance(application).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
            Log.d(TAG, "Tâche programmée avec succès")
        }
    }

    private fun cancelWallpaperChange() {
        Log.d(TAG, "Annulation de la tâche de changement de fond d'écran")
        WorkManager.getInstance(this).cancelUniqueWork(WORK_NAME)
    }
    @Composable
    fun WallpaperItem(file: DocumentFile) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, shape = MaterialTheme.shapes.medium)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = file.uri,
                    contentDescription = "Aperçu",
                    modifier = Modifier
                        .width(100.dp)
                        .height(100.dp)
                        .clip(MaterialTheme.shapes.medium),
                    contentScale = ContentScale.Crop
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp)
                ) {
                    Text(
                        text = file.name ?: "Sans nom",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Taille: ${formatFileSize(file.length())}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Modifié le: ${formatDate(file.lastModified())}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }

    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            else -> "${size / (1024 * 1024)} MB"
        }
    }

    private fun formatDate(timestamp: Long): String {
        return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
}