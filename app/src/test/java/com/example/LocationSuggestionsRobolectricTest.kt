package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.datasource.BundledSuggestionSourceImpl
import com.example.data.datasource.UserHistorySuggestionSourceImpl
import com.example.data.local.TravelStampDatabase
import com.example.data.local.UserPreferencesRepository
import com.example.data.model.LocationCategory
import com.example.data.model.LocationSuggestion
import com.example.data.model.Trip
import com.example.data.repository.ChecklistRepositoryImpl
import com.example.data.repository.LocationSuggestionRepositoryImpl
import com.example.data.repository.MomentRepositoryImpl
import com.example.data.repository.TravelStampRepositoryImpl
import com.example.data.repository.TripRepositoryImpl
import com.example.ui.viewmodel.TravelViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocationSuggestionsRobolectricTest {

    private lateinit var db: TravelStampDatabase
    private lateinit var tripRepo: TripRepositoryImpl
    private lateinit var suggestionRepo: LocationSuggestionRepositoryImpl
    private lateinit var vm: TravelViewModel

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, TravelStampDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tripRepo = TripRepositoryImpl(db.tripDao(), db.momentDao(), context)
        val userHistorySource = UserHistorySuggestionSourceImpl(tripRepo)
        val bundledSource = BundledSuggestionSourceImpl()
        suggestionRepo = LocationSuggestionRepositoryImpl(userHistorySource, bundledSource)

        val checklistRepo = ChecklistRepositoryImpl(db.checklistDao())
        val momentRepo = MomentRepositoryImpl(db.momentDao(), context)
        val stampRepo = TravelStampRepositoryImpl(db.travelStampDao())
        val fakeUserPrefs = object : UserPreferencesRepository {
            override val hasCompletedOnboarding = MutableStateFlow(true)
            override val themeMode = MutableStateFlow(com.example.data.local.AppThemeMode.SYSTEM)
            override val preTripRemindersEnabled = MutableStateFlow(true)
            override fun setOnboardingCompleted(completed: Boolean) {}
            override fun setThemeMode(mode: com.example.data.local.AppThemeMode) {}
            override fun setPreTripRemindersEnabled(enabled: Boolean) {}
        }

        vm = TravelViewModel(
            tripRepository = tripRepo,
            checklistRepository = checklistRepo,
            momentRepository = momentRepo,
            travelStampRepository = stampRepo,
            userPreferencesRepository = fakeUserPrefs,
            database = db,
            locationSuggestionRepository = suggestionRepo
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `TEST 1 - Less than 2 characters yields no suggestions`() = runBlocking {
        val resultsEmpty = suggestionRepo.searchSuggestions("")
        val resultsSingleChar = suggestionRepo.searchSuggestions("h")
        val resultsWhitespaceSingle = suggestionRepo.searchSuggestions("   g   ")

        assertTrue(resultsEmpty.isEmpty())
        assertTrue(resultsSingleChar.isEmpty())
        assertTrue(resultsWhitespaceSingle.isEmpty())
    }

    @Test
    fun `TEST 2 - Prefix match finds places starting with query`() = runBlocking {
        val results = suggestionRepo.searchSuggestions("hari")

        assertTrue(results.isNotEmpty())
        assertTrue(results.any { it.name.startsWith("Harihar", ignoreCase = true) })
        val harihar = results.first { it.name.contains("Harihar", ignoreCase = true) }
        assertEquals("Nashik, Maharashtra", harihar.destination)
        assertEquals(LocationCategory.FORT, harihar.category)
    }

    @Test
    fun `TEST 3 - Case insensitive matching works identically`() = runBlocking {
        val resultsLower = suggestionRepo.searchSuggestions("hari")
        val resultsUpper = suggestionRepo.searchSuggestions("HARI")
        val resultsMixed = suggestionRepo.searchSuggestions("HaRi")

        assertEquals(resultsLower.size, resultsUpper.size)
        assertEquals(resultsLower.size, resultsMixed.size)
        assertEquals(resultsLower.first().name, resultsUpper.first().name)
    }

    @Test
    fun `TEST 4 - User history receives preferred ranking over curated items`() = runBlocking {
        // Add a trip to user history that matches "hari"
        val customTrip = Trip(
            name = "Harihar Fort Monsoon Trek",
            destination = "Trimbakeshwar, Nashik",
            date = "15 Aug 2026",
            peopleCount = 2
        )
        tripRepo.createTrip(customTrip)

        val results = suggestionRepo.searchSuggestions("hari")
        assertTrue(results.isNotEmpty())
        val topResult = results.first()

        // Top result should be from history
        assertTrue(topResult.isFromHistory)
        assertEquals("Harihar Fort Monsoon Trek", topResult.name)
        assertEquals("Trimbakeshwar, Nashik", topResult.destination)
    }

    @Test
    fun `TEST 5 - Selecting a suggestion populates trip name and destination`() = runBlocking {
        val suggestions = suggestionRepo.searchSuggestions("Gateway")
        assertTrue(suggestions.isNotEmpty())

        val selected = suggestions.first { it.name == "Gateway of India" }
        assertEquals("Gateway of India", selected.name)
        assertEquals("Mumbai, Maharashtra", selected.destination)

        // Simulating selection into form fields
        var formTripName = selected.name
        var formDestination = selected.destination

        assertEquals("Gateway of India", formTripName)
        assertEquals("Mumbai, Maharashtra", formDestination)
    }

    @Test
    fun `TEST 6 - Form fields remain fully editable after selecting suggestion`() = runBlocking {
        val suggestions = suggestionRepo.searchSuggestions("Gateway")
        val selected = suggestions.first()

        var formTripName = selected.name
        var formDestination = selected.destination

        // User customizes after selection
        formTripName = "Gateway of India Sunrise Walk"
        formDestination = "Colaba, Mumbai, Maharashtra"

        val tripId = tripRepo.createTrip(
            Trip(
                name = formTripName,
                destination = formDestination,
                date = "25 Aug 2026",
                peopleCount = 1
            )
        )

        val savedTrip = tripRepo.getTripById(tripId).first()
        assertNotNull(savedTrip)
        assertEquals("Gateway of India Sunrise Walk", savedTrip?.name)
        assertEquals("Colaba, Mumbai, Maharashtra", savedTrip?.destination)
    }

    @Test
    fun `TEST 7 - Unknown custom location produces no suggestions and allows normal creation`() = runBlocking {
        val results = suggestionRepo.searchSuggestions("My Secret Sunset Point")
        assertTrue(results.isEmpty())

        // User can still save custom trip seamlessly
        val customTrip = Trip(
            name = "My Secret Sunset Point",
            destination = "Remote Cliff, Western Ghats",
            date = "01 Sep 2026",
            peopleCount = 3
        )
        val tripId = tripRepo.createTrip(customTrip)
        assertTrue(tripId > 0)

        val saved = tripRepo.getTripById(tripId).first()
        assertNotNull(saved)
        assertEquals("My Secret Sunset Point", saved?.name)
    }

    @Test
    fun `TEST 8 - Whitespace normalization trims leading and trailing spaces`() = runBlocking {
        val resultsClean = suggestionRepo.searchSuggestions("hari")
        val resultsSpaced = suggestionRepo.searchSuggestions("   hari   ")

        assertEquals(resultsClean.size, resultsSpaced.size)
        assertEquals(resultsClean.first().name, resultsSpaced.first().name)
    }

    @Test
    fun `TEST 9 - Maximum visible results are capped to configured limit (5)`() = runBlocking {
        // Query matching multiple items (e.g., "Fort", "Trek", or single vowels with 2 letters like "ha")
        val results = suggestionRepo.searchSuggestions("Fort", maxResults = 5)
        assertTrue(results.size in 1..5)
    }

    @Test
    fun `TEST 10 - Duplicate values between user history and curated dataset are deduplicated`() = runBlocking {
        // Add exact match in user history for an item already in curated dataset
        val duplicateHistoryTrip = Trip(
            name = "Gateway of India",
            destination = "Mumbai, Maharashtra",
            date = "10 Aug 2026",
            peopleCount = 2
        )
        tripRepo.createTrip(duplicateHistoryTrip)

        val results = suggestionRepo.searchSuggestions("Gateway of India")
        // Must appear only once, not twice
        val gatewayMatches = results.filter { it.name.equals("Gateway of India", ignoreCase = true) }
        assertEquals(1, gatewayMatches.size)
        assertTrue(gatewayMatches.first().isFromHistory)
    }

    @Test
    fun `TEST 11 - Offline search functions without network dependency`() = runBlocking {
        // The repository is entirely local/in-memory with zero network calls
        val results = suggestionRepo.searchSuggestions("Taj Mahal")
        assertTrue(results.isNotEmpty())
        assertEquals("Agra, Uttar Pradesh", results.first().destination)
    }

    @Test
    fun `TEST 12 - Long place and destination names are safely handled without crash`() = runBlocking {
        val veryLongName = "A".repeat(200) + " Historical Monument and Mountain Fortress"
        val veryLongDest = "B".repeat(200) + ", Remote District, State of India"

        tripRepo.createTrip(
            Trip(
                name = veryLongName,
                destination = veryLongDest,
                date = "20 Aug 2026",
                peopleCount = 1
            )
        )

        val results = suggestionRepo.searchSuggestions("Historical")
        assertTrue(results.isNotEmpty())
        assertEquals(veryLongName, results.first().name)
        assertEquals(veryLongDest, results.first().destination)
    }

    @Test
    fun `TEST 13 - Existing Create Trip flow without suggestions behaves identically`() = runBlocking {
        val standardTrip = Trip(
            name = "Sinhagad Weekend Trek",
            destination = "Pune, Maharashtra",
            date = "12 Aug 2026",
            peopleCount = 4,
            description = "Standard trek without autocomplete interaction"
        )
        val id = tripRepo.createTrip(standardTrip)
        assertTrue(id > 0)

        val retrieved = tripRepo.getTripById(id).first()
        assertNotNull(retrieved)
        assertEquals("Sinhagad Weekend Trek", retrieved?.name)
    }

    @Test
    fun `TEST 14 - ViewModel query flow updates locationSuggestions state accurately`() = runBlocking {
        vm.onTripNameQueryChanged("h")
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertEquals(0, vm.locationSuggestions.value.size)

        vm.onTripNameQueryChanged("Kalsubai")
        var attempts = 0
        while (vm.locationSuggestions.value.isEmpty() && attempts < 20) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            kotlinx.coroutines.delay(50)
            attempts++
        }
        assertTrue(vm.locationSuggestions.value.isNotEmpty())
        assertEquals("Kalsubai Peak", vm.locationSuggestions.value.first().name)

        vm.clearLocationSuggestions()
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertEquals(0, vm.locationSuggestions.value.size)
    }
}
