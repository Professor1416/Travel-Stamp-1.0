package com.example.data.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import com.example.data.local.TravelStampDatabase
import com.example.data.local.entity.ChecklistItemEntity
import com.example.data.local.entity.MomentEntity
import com.example.data.local.entity.StampSequenceEntity
import com.example.data.local.entity.TravelStampEntity
import com.example.data.local.entity.TripEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupExportResult(
    val fileUri: Uri,
    val fileName: String,
    val totalTrips: Int,
    val totalStamps: Int
)

data class BackupImportResult(
    val importedTrips: Int,
    val importedStamps: Int,
    val importedMoments: Int,
    val importedChecklistItems: Int
)

object BackupManager {

    private const val MANIFEST_VERSION = "2.0"
    private const val APP_IDENTIFIER = "TravelStamp"

    /**
     * Generates a complete JSON payload representing the current database state.
     */
    suspend fun generateBackupJson(
        database: TravelStampDatabase,
        mediaFileNameMap: Map<String, String> = emptyMap()
    ): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("version", 2)
        root.put("appName", APP_IDENTIFIER)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("exportDateFormatted", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))

        // 1. Trips
        val trips = database.tripDao().getAllTripsListSync()
        val tripsArray = JSONArray()
        trips.forEach { trip ->
            val obj = JSONObject().apply {
                put("id", trip.id)
                put("uuid", trip.uuid)
                put("name", trip.name)
                put("destination", trip.destination)
                put("date", trip.date)
                put("peopleCount", trip.peopleCount)
                put("description", trip.description)
                put("status", trip.status)
                put("stampEarned", trip.stampEarned)
                put("createdAt", trip.createdAt)
                put("updatedAt", trip.updatedAt)
                put("completedAt", trip.completedAt ?: JSONObject.NULL)
                put("deletedAt", trip.deletedAt ?: JSONObject.NULL)
            }
            tripsArray.put(obj)
        }
        root.put("trips", tripsArray)

        // 2. Checklist Items
        val checklistItems = database.checklistDao().getAllItemsListSync()
        val checklistArray = JSONArray()
        checklistItems.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("uuid", item.uuid)
                put("tripId", item.tripId)
                put("text", item.text)
                put("isCompleted", item.isCompleted)
                put("sortOrder", item.sortOrder)
                put("createdAt", item.createdAt)
                put("updatedAt", item.updatedAt)
                put("deletedAt", item.deletedAt ?: JSONObject.NULL)
            }
            checklistArray.put(obj)
        }
        root.put("checklistItems", checklistArray)

        // 3. Moments
        val moments = database.momentDao().getAllMomentsListSync()
        val momentsArray = JSONArray()
        moments.forEach { moment ->
            val mappedImageName = if (!moment.imageUri.isNullOrBlank()) {
                mediaFileNameMap[moment.imageUri] ?: moment.imageUri
            } else null

            val obj = JSONObject().apply {
                put("id", moment.id)
                put("uuid", moment.uuid)
                put("tripId", moment.tripId)
                put("category", moment.category)
                put("note", moment.note)
                put("imageUri", moment.imageUri ?: JSONObject.NULL)
                put("imageFileName", mappedImageName ?: JSONObject.NULL)
                put("timestamp", moment.timestamp)
                put("createdAt", moment.createdAt)
                put("updatedAt", moment.updatedAt)
                put("deletedAt", moment.deletedAt ?: JSONObject.NULL)
            }
            momentsArray.put(obj)
        }
        root.put("moments", momentsArray)

        // 4. Travel Stamps
        val stamps = database.travelStampDao().getAllStampsListSync()
        val stampsArray = JSONArray()
        stamps.forEach { stamp ->
            val obj = JSONObject().apply {
                put("id", stamp.id)
                put("uuid", stamp.uuid)
                put("tripId", stamp.tripId)
                put("stampNumber", stamp.stampNumber)
                put("stampCode", stamp.stampCode)
                put("title", stamp.title)
                put("destination", stamp.destination)
                put("dateText", stamp.dateText)
                put("peopleCount", stamp.peopleCount)
                put("momentsCount", stamp.momentsCount)
                put("inkColorHex", stamp.inkColorHex)
                put("stampStyle", stamp.stampStyle)
                put("inspectionText", stamp.inspectionText)
                put("issuedAt", stamp.issuedAt)
                put("createdAt", stamp.createdAt)
                put("updatedAt", stamp.updatedAt)
                put("completedAt", stamp.completedAt ?: JSONObject.NULL)
                put("deletedAt", stamp.deletedAt ?: JSONObject.NULL)
                put("reflectionNote", stamp.reflectionNote ?: JSONObject.NULL)
            }
            stampsArray.put(obj)
        }
        root.put("travelStamps", stampsArray)

        // 5. Stamp Sequence Counter
        val lastSequence = database.travelStampDao().getLastAllocatedSequence() ?: 0L
        val seqObj = JSONObject().apply {
            put("lastAllocatedNumber", lastSequence)
        }
        root.put("stampSequence", seqObj)

        root.toString(2)
    }

    /**
     * Exports a complete, portable .tsbackup archive containing manifest, database JSON, and media files.
     */
    suspend fun createExportFile(context: Context, database: TravelStampDatabase): BackupExportResult = withContext(Dispatchers.IO) {
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val fileName = "TravelStamp_Backup_$dateStr.tsbackup"

        val backupDir = File(context.cacheDir, "backups")
        if (!backupDir.exists()) {
            backupDir.mkdirs()
        }

        val backupZipFile = File(backupDir, fileName)
        if (backupZipFile.exists()) {
            backupZipFile.delete()
        }

        val allMoments = database.momentDao().getAllMomentsListSync()
        val mediaMap = mutableMapOf<String, String>() // originalPath -> archiveRelativePath

        // 1. Identify local media files to bundle
        val mediaFilesToBundle = mutableListOf<Pair<File, String>>()
        for (moment in allMoments) {
            val uriStr = moment.imageUri ?: continue
            val candidateFile = if (uriStr.startsWith("/")) {
                File(uriStr)
            } else if (uriStr.startsWith("file://")) {
                File(Uri.parse(uriStr).path ?: "")
            } else {
                null
            }

            if (candidateFile != null && candidateFile.exists() && candidateFile.isFile) {
                val archiveName = "media/${moment.uuid}_${candidateFile.name}"
                mediaMap[uriStr] = archiveName
                mediaFilesToBundle.add(candidateFile to archiveName)
            }
        }

        val trips = database.tripDao().getAllTripsListSync()
        val stamps = database.travelStampDao().getAllStampsListSync()
        val checklistItems = database.checklistDao().getAllItemsListSync()

        // 2. Build Manifest
        val manifestObj = JSONObject().apply {
            put("app", APP_IDENTIFIER)
            put("manifestVersion", MANIFEST_VERSION)
            put("schemaVersion", 3)
            put("exportedAt", System.currentTimeMillis())
            put("counts", JSONObject().apply {
                put("trips", trips.size)
                put("stamps", stamps.size)
                put("moments", allMoments.size)
                put("checklistItems", checklistItems.size)
                put("mediaFiles", mediaFilesToBundle.size)
            })
        }

        // 3. Build data.json
        val databaseJson = generateBackupJson(database, mediaMap)

        // 4. Write ZIP Archive
        ZipOutputStream(BufferedOutputStream(FileOutputStream(backupZipFile))).use { zos ->
            // Entry 1: manifest.json
            val manifestBytes = manifestObj.toString(2).toByteArray(Charsets.UTF_8)
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(manifestBytes)
            zos.closeEntry()

            // Entry 2: data.json
            val dataBytes = databaseJson.toByteArray(Charsets.UTF_8)
            zos.putNextEntry(ZipEntry("data.json"))
            zos.write(dataBytes)
            zos.closeEntry()

            // Entry 3..N: Media files
            val buffer = ByteArray(8192)
            for ((file, entryPath) in mediaFilesToBundle) {
                zos.putNextEntry(ZipEntry(entryPath))
                FileInputStream(file).use { fis ->
                    var len: Int
                    while (fis.read(buffer).also { len = it } > 0) {
                        zos.write(buffer, 0, len)
                    }
                }
                zos.closeEntry()
            }
        }

        val uri = try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                backupZipFile
            )
        } catch (_: Exception) {
            Uri.fromFile(backupZipFile)
        }

        BackupExportResult(
            fileUri = uri,
            fileName = fileName,
            totalTrips = trips.size,
            totalStamps = stamps.size
        )
    }

    /**
     * Imports from either a .tsbackup (ZIP) archive or a legacy .json backup file.
     * Prevents Zip Slip vulnerability and merges data safely.
     */
    suspend fun importBackup(
        context: Context,
        uri: Uri,
        database: TravelStampDatabase
    ): Result<BackupImportResult> = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val tempDir = File(context.cacheDir, "import_temp_${System.currentTimeMillis()}").apply { mkdirs() }

            var jsonString: String? = null
            val restoredMediaMap = mutableMapOf<String, String>() // zipRelativePath -> newLocalAbsolutePath

            // Check if file is ZIP or JSON by inspecting header bytes
            val isZip = isZipStream(context, uri)

            if (isZip) {
                // Extract ZIP contents securely with Zip Slip check
                contentResolver.openInputStream(uri)?.use { rawIn ->
                    ZipInputStream(BufferedInputStream(rawIn)).use { zis ->
                        var entry: ZipEntry? = zis.nextEntry
                        val buffer = ByteArray(8192)
                        val momentsDir = File(context.filesDir, "moments").apply { if (!exists()) mkdirs() }

                        while (entry != null) {
                            val entryName = entry.name
                            val safeOutputFile = File(tempDir, entryName)

                            // Security check: Zip Slip prevention
                            val canonicalDest = safeOutputFile.canonicalPath
                            if (!canonicalDest.startsWith(tempDir.canonicalPath)) {
                                throw SecurityException("Zip Slip vulnerability detected in entry: $entryName")
                            }

                            if (entry.isDirectory) {
                                safeOutputFile.mkdirs()
                            } else {
                                safeOutputFile.parentFile?.mkdirs()
                                FileOutputStream(safeOutputFile).use { out ->
                                    var len: Int
                                    while (zis.read(buffer).also { len = it } > 0) {
                                        out.write(buffer, 0, len)
                                    }
                                }

                                if (entryName == "data.json") {
                                    jsonString = safeOutputFile.readText(Charsets.UTF_8)
                                } else if (entryName.startsWith("media/")) {
                                    // Move media directly to persistent moments/ dir
                                    val targetMediaFile = File(momentsDir, safeOutputFile.name)
                                    safeOutputFile.copyTo(targetMediaFile, overwrite = true)
                                    restoredMediaMap[entryName] = targetMediaFile.absolutePath
                                }
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                }
            } else {
                // Read as plain JSON
                jsonString = contentResolver.openInputStream(uri)?.use { inStream ->
                    inStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                }
            }

            // Cleanup temp directory
            tempDir.deleteRecursively()

            if (jsonString.isNullOrBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Could not read backup payload."))
            }

            // Parse and import data into Room
            parseAndInsertBackupData(database, jsonString!!, restoredMediaMap)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    // Backwards-compatible alias for existing call sites
    suspend fun importBackupFromJson(
        context: Context,
        uri: Uri,
        database: TravelStampDatabase
    ): Result<BackupImportResult> = importBackup(context, uri, database)

    suspend fun importBackupJson(
        database: TravelStampDatabase,
        jsonString: String
    ): Result<BackupImportResult> = parseAndInsertBackupData(database, jsonString, emptyMap())

    private fun isZipStream(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val header = ByteArray(4)
                val read = input.read(header)
                // ZIP magic bytes: 0x50, 0x4B, 0x03, 0x04 (PK..)
                read == 4 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() &&
                        (header[2] == 0x03.toByte() || header[2] == 0x05.toByte() || header[2] == 0x07.toByte())
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun parseAndInsertBackupData(
        database: TravelStampDatabase,
        jsonString: String,
        restoredMediaMap: Map<String, String>
    ): Result<BackupImportResult> = database.withTransaction {
        val root = JSONObject(jsonString)
        if (!root.has("trips")) {
            return@withTransaction Result.failure(IllegalArgumentException("Invalid Travel Stamp backup file format: missing 'trips' section."))
        }

        var importedTrips = 0
        var importedStamps = 0
        var importedMoments = 0
        var importedChecklistItems = 0

        // Parse Trips
        val tripsArray = root.optJSONArray("trips") ?: JSONArray()
        val oldToNewTripIdMap = mutableMapOf<Long, Long>()
        val existingTrips = database.tripDao().getAllTripsListSync()

        for (i in 0 until tripsArray.length()) {
            val obj = tripsArray.getJSONObject(i)
            val oldId = obj.optLong("id", -1L)
            val tripUuid = obj.optString("uuid", UUID.randomUUID().toString())
            val tripDate = obj.getString("date")
            val isFuture = DateUtils.isFutureDate(tripDate)
            val rawStatus = obj.optString("status", "UPCOMING")
            val isCompleted = rawStatus == "COMPLETED"

            val existingTrip = existingTrips.firstOrNull { it.uuid == tripUuid }

            val tripEntity = TripEntity(
                id = existingTrip?.id ?: 0,
                uuid = tripUuid,
                name = obj.getString("name"),
                destination = obj.getString("destination"),
                date = tripDate,
                peopleCount = obj.optInt("peopleCount", 1),
                description = obj.optString("description", ""),
                status = if (isCompleted) "COMPLETED" else if (isFuture) "UPCOMING" else "IN_PROGRESS",
                stampEarned = isCompleted && obj.optBoolean("stampEarned", true),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                completedAt = if (isCompleted && !obj.isNull("completedAt")) obj.optLong("completedAt") else null,
                deletedAt = if (!obj.isNull("deletedAt")) obj.optLong("deletedAt") else null
            )

            val newTripId = if (existingTrip != null) {
                database.tripDao().updateTrip(tripEntity)
                existingTrip.id
            } else {
                database.tripDao().insertTrip(tripEntity)
            }

            if (oldId != -1L) {
                oldToNewTripIdMap[oldId] = newTripId
            }
            importedTrips++
        }

        // Parse Checklist Items
        val checklistArray = root.optJSONArray("checklistItems") ?: JSONArray()
        for (i in 0 until checklistArray.length()) {
            val obj = checklistArray.getJSONObject(i)
            val oldTripId = obj.getLong("tripId")
            val newTripId = oldToNewTripIdMap[oldTripId] ?: continue
            val itemUuid = obj.optString("uuid", UUID.randomUUID().toString())

            val existingItems = database.checklistDao().getItemsForTripSync(newTripId)
            val existingItem = existingItems.firstOrNull { it.uuid == itemUuid }

            val item = ChecklistItemEntity(
                id = existingItem?.id ?: 0,
                uuid = itemUuid,
                tripId = newTripId,
                text = obj.getString("text"),
                isCompleted = obj.optBoolean("isCompleted", false),
                sortOrder = obj.optInt("sortOrder", i),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                deletedAt = if (!obj.isNull("deletedAt")) obj.optLong("deletedAt") else null
            )

            if (existingItem != null) {
                database.checklistDao().updateItem(item)
            } else {
                database.checklistDao().insertItem(item)
            }
            importedChecklistItems++
        }

        // Parse Moments
        val momentsArray = root.optJSONArray("moments") ?: JSONArray()
        for (i in 0 until momentsArray.length()) {
            val obj = momentsArray.getJSONObject(i)
            val oldTripId = obj.getLong("tripId")
            val newTripId = oldToNewTripIdMap[oldTripId] ?: continue
            val momentUuid = obj.optString("uuid", UUID.randomUUID().toString())

            val imageFileName = if (!obj.isNull("imageFileName")) obj.optString("imageFileName") else null
            val rawImageUri = if (!obj.isNull("imageUri")) obj.optString("imageUri") else null

            // Map extracted image if available
            val finalImageUri = (imageFileName?.let { restoredMediaMap[it] }
                ?: rawImageUri?.let { restoredMediaMap[it] }
                ?: rawImageUri)

            val existingMoments = database.momentDao().getMomentsForTripSync(newTripId)
            val existingMoment = existingMoments.firstOrNull { it.uuid == momentUuid }

            val moment = MomentEntity(
                id = existingMoment?.id ?: 0,
                uuid = momentUuid,
                tripId = newTripId,
                category = obj.getString("category"),
                note = obj.optString("note", ""),
                imageUri = finalImageUri,
                timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                deletedAt = if (!obj.isNull("deletedAt")) obj.optLong("deletedAt") else null
            )
            database.momentDao().insertMoment(moment)
            importedMoments++
        }

        // Parse Stamps (Only import stamps if trip is not in the future)
        var maxImportedStampNumber = 0L
        val stampsArray = root.optJSONArray("travelStamps") ?: JSONArray()
        for (i in 0 until stampsArray.length()) {
            val obj = stampsArray.getJSONObject(i)
            val oldTripId = obj.getLong("tripId")
            val newTripId = oldToNewTripIdMap[oldTripId] ?: continue
            val dateText = obj.getString("dateText")

            val targetTrip = database.tripDao().getTripByIdSync(newTripId)
            // Safety check: Only skip if trip is not completed and date is strictly in the future
            if (targetTrip?.status != "COMPLETED" && DateUtils.isFutureDate(dateText)) {
                continue
            }

            val stampNum = obj.optLong("stampNumber", 1L)
            if (stampNum > maxImportedStampNumber) {
                maxImportedStampNumber = stampNum
            }

            val existingStampForTrip = database.travelStampDao().getStampForTripSync(newTripId)
            val existingStampByNumber = database.travelStampDao().getStampByNumberSync(stampNum)

            val finalStampNumber = if (existingStampForTrip != null) {
                existingStampForTrip.stampNumber
            } else if (existingStampByNumber != null && existingStampByNumber.tripId != newTripId) {
                database.travelStampDao().allocateNextStampNumber()
            } else {
                stampNum
            }

            val stamp = TravelStampEntity(
                id = existingStampForTrip?.id ?: 0,
                uuid = obj.optString("uuid", UUID.randomUUID().toString()),
                tripId = newTripId,
                stampNumber = finalStampNumber,
                stampCode = obj.getString("stampCode"),
                title = obj.getString("title"),
                destination = obj.getString("destination"),
                dateText = dateText,
                peopleCount = obj.optInt("peopleCount", 1),
                momentsCount = obj.optInt("momentsCount", 0),
                inkColorHex = obj.optString("inkColorHex", "#C85A32"),
                stampStyle = obj.optString("stampStyle", "MOUNTAIN"),
                inspectionText = obj.optString("inspectionText", "OFFICIALLY LOGGED • CERTIFIED JOURNEY"),
                issuedAt = obj.optLong("issuedAt", System.currentTimeMillis()),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                completedAt = if (obj.isNull("completedAt")) null else obj.optLong("completedAt"),
                deletedAt = if (obj.isNull("deletedAt")) null else obj.optLong("deletedAt"),
                reflectionNote = if (obj.isNull("reflectionNote")) null else obj.optString("reflectionNote")
            )
            database.travelStampDao().insertStamp(stamp)
            importedStamps++
        }

        // Update stamp sequence counter so future stamps never collide
        val backupSequence = root.optJSONObject("stampSequence")?.optLong("lastAllocatedNumber", 0L) ?: 0L
        val currentMaxDbSeq = database.travelStampDao().getLastAllocatedSequence() ?: 0L
        val finalMax = maxOf(backupSequence, maxImportedStampNumber, currentMaxDbSeq)
        if (finalMax > 0L) {
            database.travelStampDao().setLastAllocatedSequence(StampSequenceEntity(id = "STAMP_COUNTER", lastAllocatedNumber = finalMax))
        }

        Result.success(
            BackupImportResult(
                importedTrips = importedTrips,
                importedStamps = importedStamps,
                importedMoments = importedMoments,
                importedChecklistItems = importedChecklistItems
            )
        )
    }
}
