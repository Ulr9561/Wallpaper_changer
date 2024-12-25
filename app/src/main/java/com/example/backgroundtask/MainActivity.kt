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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
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
import java.util.concurrent.TimeUnit

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
            handleFolderSelect(it)
        }
    }

    private fun handleFolderSelect(uri: Uri) {
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        selectedFolderUri = uri
        saveFolderUri(uri)
        loadWallpapers(uri)
    }

    private fun saveFolderUri(uri: Uri) {
        Log.d(TAG, "Sauvegarde du chemin du dossier: ${uri.path}")
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(FOLDER_URI_KEY, uri.toString())
            .apply()
    }

    private val imagePicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris?.let { selectedUris ->
            handleNewImages(selectedUris)
        }
    }

    private fun handleNewImages(uris: List<Uri>) {
        selectedFolderUri?.let { folderUri ->
            scope.launch(Dispatchers.IO) {
                for (imageUri in uris) {
                    try {
                        val destinationFolder = DocumentFile.fromTreeUri(this@MainActivity, folderUri)
                        val sourceFile = contentResolver.openInputStream(imageUri)?.use { input ->
                            val fileName = imageUri.lastPathSegment ?: "image_${System.currentTimeMillis()}.jpg"
                            val newFile = destinationFolder?.createFile("image/jpeg", fileName)
                            newFile?.let { file ->
                                contentResolver.openOutputStream(file.uri)?.use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Erreur lors de la copie de l'image: ${e.message}")
                    }
                }
                loadWallpapers(folderUri)
            }
        }
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
                        Row(
                            modifier = Modifier
                                .fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {


                            if (selectedFolderUri == null) {
                                // Afficher le bouton de sélection de dossier uniquement si aucun dossier n'est sélectionné
                                Button(
                                    onClick = { folderPicker.launch(null) },
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Text("Sélectionner le dossier des fonds d'écran")
                                }
                            } else {

                                // Afficher le bouton d'ajout d'images uniquement si un dossier est sélectionné
                                Button(
                                    onClick = { imagePicker.launch(arrayOf("image/*")) },
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Text("Ajouter des images")
                                }
                            }

                        }


                      Text(
                          "Fréquence: 15 minutes",
                          modifier = Modifier.padding(bottom = 8.dp)
                      )

                        // List of wallpapers
                        WallpaperGrid(
                            wallpapers = wallpaperFiles,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                        )


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

            val workRequest = PeriodicWorkRequestBuilder<WallpaperWorker>(
                15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setInputData(workDataOf("folder_uri" to uri.toString()))
                .build()

            WorkManager.getInstance(application).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )
        }
    }

    private fun cancelWallpaperChange() {
        Log.d(TAG, "Annulation de la tâche de changement de fond d'écran")
        WorkManager.getInstance(this).cancelUniqueWork(WORK_NAME)
    }

    @Composable
    fun WallpaperGrid(
        wallpapers: List<DocumentFile>,
        modifier: Modifier = Modifier
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = modifier
        ) {
            items(wallpapers) { file ->
                WallpaperGridItem(file = file)
            }
        }
    }

    @Composable
    fun WallpaperGridItem(file: DocumentFile) {
        Card(
            modifier = Modifier
                .aspectRatio(1f) // Garde un ratio carré
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = MaterialTheme.shapes.small
                ),
            shape = MaterialTheme.shapes.small
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Image
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    AsyncImage(
                        model = file.uri,
                        contentDescription = "Aperçu",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(MaterialTheme.shapes.small),
                        contentScale = ContentScale.Crop
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                ) {
                    Text(
                        text = file.name?.let {
                            if (it.length > 20) it.take(17) + "..." else it
                        } ?: "Sans nom",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatFileSize(file.length()),
                        style = MaterialTheme.typography.labelSmall
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

    @Composable
    fun FrequencyDialog(
        currentFrequency: Long,
        onDismiss: () -> Unit,
        onConfirm: (Long) -> Unit
    ) {
        var frequency by remember { mutableStateOf(currentFrequency.toString()) }
        var error by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Définir la fréquence") },
            text = {
                Column {
                    OutlinedTextField(
                        value = frequency,
                        onValueChange = {
                            frequency = it
                            error = null
                        },
                        label = { Text("Minutes") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = error != null
                    )
                    error?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Text(
                        "La fréquence doit être entre 5 minutes et 3 heures (180 minutes)",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val frequencyValue = frequency.toLongOrNull()
                        when {
                            frequencyValue == null -> {
                                error = "Veuillez entrer un nombre valide"
                            }
                            frequencyValue < 5 || frequencyValue > 180 -> {
                                error = "La fréquence doit être entre 5 et 180 minutes"
                            }
                            else -> {
                                onConfirm(frequencyValue)
                            }
                        }
                    }
                ) {
                    Text("Confirmer")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Annuler")
                }
            }
        )
    }
}

