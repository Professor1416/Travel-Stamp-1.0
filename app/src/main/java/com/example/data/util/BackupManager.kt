package com.example.data.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
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
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    suspend fun generateBackupJson(database: TravelStampDatabase): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("version", 1)
        root.put("appName", "Travel Stamp")
        root.put("exportedAt", System.currentTimeMillis())
        root.put("exportDateFormatted", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))

        // 1. Trips
        val trips = database.tripDao().getAllTripsListSync()
        val tripsArray = JSONArray()
        trips.forEach { trip ->
            val obj = JSONObject().apply {
                put("id", trip.id)
                put("name", trip.name)
                put("destination", trip.destination)
                put("date", trip.date)
                put("peopleCount", trip.peopleCount)
                put("description", trip.description)
                put("status", trip.status)
                put("stampEarned", trip.stampEarned)
                put("createdAt", trip.createdAt)
                put("completedAt", trip.completedAt ?: JSONObject.NULL)
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
                put("tripId", item.tripId)
                put("text", item.text)
                put("isCompleted", item.isCompleted)
                put("sortOrder", item.sortOrder)
            }
            checklistArray.put(obj)
        }
        root.put("checklistItems", checklistArray)

        // 3. Moments
        val moments = database.momentDao().getAllMomentsListSync()
        val momentsArray = JSONArray()
        moments.forEach { moment ->
            val obj = JSONObject().apply {
                put("id", moment.id)
                put("tripId", moment.tripId)
                put("category", moment.category)
                put("note", moment.note)
                put("imageUri", moment.imageUri ?: JSONObject.NULL)
                put("timestamp", moment.timestamp)
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
                put("completedAt", stamp.completedAt ?: JSONObject.NULL)
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

    suspend fun createExportFile(context: Context, database: TravelStampDatabase): BackupExportResult = withContext(Dispatchers.IO) {
        val json = generateBackupJson(database)
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val fileName = "TravelStamp_Backup_$dateStr.json"

        val backupDir = File(context.cacheDir, "backups")
        if (!backupDir.exists()) {
            backupDir.mkdirs()
        }

        val file = File(backupDir, fileName)
        FileOutputStream(file).use { out ->
            out.write(json.toByteArray(Charsets.UTF_8))
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val totalTrips = database.tripDao().getAllTripsListSync().size
        val totalStamps = database.travelStampDao().getAllStampsListSync().size

        BackupExportResult(
            fileUri = uri,
            fileName = fileName,
            totalTrips = totalTrips,
            totalStamps = totalStamps
        )
    }

    suspend fun importBackupFromJson(
        context: Context,
        uri: Uri,
        database: TravelStampDatabase
    ): Result<BackupImportResult> = withContext(Dispatchers.IO) {
        try {
            val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } ?: return@withContext Result.failure(IllegalArgumentException("Could not read backup file."))

            val root = JSONObject(jsonString)
            if (!root.has("trips")) {
                return@withContext Result.failure(IllegalArgumentException("Invalid Travel Stamp backup file format."))
            }

            var importedTrips = 0
            var importedStamps = 0
            var importedMoments = 0
            var importedChecklistItems = 0

            // Parse Trips
            val tripsArray = root.optJSONArray("trips") ?: JSONArray()
            val oldToNewTripIdMap = mutableMapOf<Long, Long>()

            for (i in 0 until tripsArray.length()) {
                val obj = tripsArray.getJSONObject(i)
                val oldId = obj.optLong("id", -1L)
                val tripDate = obj.getString("date")
                val isFuture = DateUtils.isFutureDate(tripDate)
                val rawStatus = obj.optString("status", "UPCOMING")
                val isCompleted = rawStatus == "COMPLETED" && !isFuture

                val tripEntity = TripEntity(
                    name = obj.getString("name"),
                    destination = obj.getString("destination"),
                    date = tripDate,
                    peopleCount = obj.optInt("peopleCount", 1),
                    description = obj.optString("description", ""),
                    status = if (isCompleted) "COMPLETED" else if (isFuture) "UPCOMING" else "IN_PROGRESS",
                    stampEarned = isCompleted && obj.optBoolean("stampEarned", true),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    completedAt = if (isCompleted && !obj.isNull("completedAt")) obj.optLong("completedAt") else null
                )

                val newTripId = database.tripDao().insertTrip(tripEntity)
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

                val item = ChecklistItemEntity(
                    tripId = newTripId,
                    text = obj.getString("text"),
                    isCompleted = obj.optBoolean("isCompleted", false),
                    sortOrder = obj.optInt("sortOrder", i)
                )
                database.checklistDao().insertItem(item)
                importedChecklistItems++
            }

            // Parse Moments
            val momentsArray = root.optJSONArray("moments") ?: JSONArray()
            for (i in 0 until momentsArray.length()) {
                val obj = momentsArray.getJSONObject(i)
                val oldTripId = obj.getLong("tripId")
                val newTripId = oldToNewTripIdMap[oldTripId] ?: continue

                val moment = MomentEntity(
                    tripId = newTripId,
                    category = obj.getString("category"),
                    note = obj.optString("note", ""),
                    imageUri = if (obj.isNull("imageUri")) null else obj.optString("imageUri"),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis())
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

                // Business logic: Do not import future stamps
                if (DateUtils.isFutureDate(dateText)) {
                    continue
                }

                val stampNum = obj.optLong("stampNumber", 1L)
                if (stampNum > maxImportedStampNumber) {
                    maxImportedStampNumber = stampNum
                }

                val stamp = TravelStampEntity(
                    uuid = obj.optString("uuid", java.util.UUID.randomUUID().toString()),
                    tripId = newTripId,
                    stampNumber = stampNum,
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
                    completedAt = if (obj.isNull("completedAt")) null else obj.optLong("completedAt"),
                    reflectionNote = if (obj.isNull("reflectionNote")) null else obj.optString("reflectionNote")
                )
                database.travelStampDao().insertStamp(stamp)
                importedStamps++
            }

            // Update stamp sequence counter so future stamps never collide
            val backupSequence = root.optJSONObject("stampSequence")?.optLong("lastAllocatedNumber", 0L) ?: 0L
            val finalMax = maxOf(backupSequence, maxImportedStampNumber, database.travelStampDao().getLastAllocatedSequence() ?: 0L)
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
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
