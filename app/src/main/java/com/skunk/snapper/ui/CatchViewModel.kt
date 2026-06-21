package com.skunk.snapper.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.skunk.snapper.ai.AreaFish
import com.skunk.snapper.ai.FishGuess
import com.skunk.snapper.ai.FishIdentifier
import com.skunk.snapper.data.Catch
import com.skunk.snapper.data.SnapperDatabase
import com.skunk.snapper.data.Spot
import com.skunk.snapper.util.AreaFishStore
import com.skunk.snapper.util.FishFacts
import com.skunk.snapper.util.FishPhotos
import com.skunk.snapper.util.FishRegs
import com.skunk.snapper.util.StateRegs
import com.skunk.snapper.util.ImageStorage
import com.skunk.snapper.util.LocationProvider
import com.skunk.snapper.util.PlaceLookup
import com.skunk.snapper.util.WikiImages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

private val MONTHS = arrayOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
)

/** State of the "add a catch" flow. */
data class AddUiState(
    val photoPath: String? = null,
    val identifying: Boolean = false,
    val species: String = "",
    val confidence: String = "",
    val details: String = "",
    val lengthEstimate: String = "",
    val weightLb: String = "",
    val weightOz: String = "",
    val notes: String = "",
    /** When the catch happened, epoch millis (defaults to now). */
    val caughtAt: Long = 0L,
    /** Where the catch happened (defaults to current location; null until known). */
    val lat: Double? = null,
    val lng: Double? = null,
    val message: String? = null,
    val saved: Boolean = false
)

/** State of the "What's around" tab. */
data class WhatsAroundState(
    val loading: Boolean = false,
    val area: String? = null,
    val fish: List<AreaFish> = emptyList(),
    val message: String? = null
)

/** Gemini-fetched field-guide info for a selected species. */
data class FishInfoState(
    val loading: Boolean = false,
    val bagLimit: String = "",
    val minSize: String = "",
    val body: String = "",
    val message: String? = null
)

/** State of the standalone "identify a fish" flow (nothing is saved). */
data class IdUiState(
    val photoUri: Uri? = null,
    val identifying: Boolean = false,
    val result: FishGuess? = null,
    val message: String? = null
)

class CatchViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = SnapperDatabase.get(app).catchDao()
    private val spotDao = SnapperDatabase.get(app).spotDao()

    val catches: StateFlow<List<Catch>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** All saved fishing spots — shown both on the Map (⭐) and in the Favorites tab. */
    val spots: StateFlow<List<Spot>> = spotDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** A spot the Favorites tab asked the Map to fly to; the Map consumes and clears it. */
    private val _focusSpot = MutableStateFlow<Spot?>(null)
    val focusSpot: StateFlow<Spot?> = _focusSpot.asStateFlow()

    fun focusOnSpot(spot: Spot) { _focusSpot.value = spot }
    fun consumeFocusSpot() { _focusSpot.value = null }

    /**
     * Save a fishing spot at [lat]/[lng]. If [name] is blank, derive a fallback name from the
     * nearest named water/place so the pin still reads as something meaningful.
     */
    fun saveSpot(lat: Double, lng: Double, name: String = "") {
        viewModelScope.launch {
            val auto = if (name.isBlank()) withContext(Dispatchers.IO) { deriveSpotName(lat, lng) } else ""
            spotDao.insert(
                Spot(
                    name = name.trim(), autoName = auto,
                    lat = lat, lng = lng, createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun renameSpot(spot: Spot, name: String) {
        viewModelScope.launch { spotDao.update(spot.copy(name = name.trim())) }
    }

    fun deleteSpot(spot: Spot) {
        viewModelScope.launch { spotDao.delete(spot) }
    }

    /** Reverse-geocode to a human label for an unnamed spot (feature/water, then locale). */
    private fun deriveSpotName(lat: Double, lng: Double): String {
        val addr = runCatching {
            @Suppress("DEPRECATION")
            android.location.Geocoder(getApplication(), java.util.Locale.US)
                .getFromLocation(lat, lng, 1)?.firstOrNull()
        }.getOrNull() ?: return ""
        val feature = addr.featureName?.takeIf { it.isNotBlank() && !it.all(Char::isDigit) }
        return feature
            ?: addr.locality
            ?: addr.subAdminArea
            ?: listOfNotNull(addr.subAdminArea, addr.adminArea).joinToString(", ").ifBlank { "" }
    }

    private val _add = MutableStateFlow(AddUiState())
    val add: StateFlow<AddUiState> = _add.asStateFlow()

    private val _idState = MutableStateFlow(IdUiState())
    val idState: StateFlow<IdUiState> = _idState.asStateFlow()

    private val _whatsAround = MutableStateFlow(WhatsAroundState())
    val whatsAround: StateFlow<WhatsAroundState> = _whatsAround.asStateFlow()

    /** Last area/state a fish list was loaded for — context for [openFish]'s lookups. */
    private var lastFishArea: String? = null

    private val _selectedFish = MutableStateFlow<AreaFish?>(null)
    val selectedFish: StateFlow<AreaFish?> = _selectedFish.asStateFlow()
    private val _fishInfo = MutableStateFlow(FishInfoState())
    val fishInfo: StateFlow<FishInfoState> = _fishInfo.asStateFlow()

    /**
     * Open the detail page for a fish from a regulations list (Regs tab or a map bubble).
     * Everything — bag/size limits, the field guide, the size — comes from bundled assets,
     * so the detail renders instantly with no network call.
     */
    fun openFish(fish: AreaFish) {
        _selectedFish.value = fish
        _fishInfo.value = FishInfoState(loading = true)
        val ctx = getApplication<Application>()
        val area = _whatsAround.value.area ?: lastFishArea ?: fish.name
        viewModelScope.launch {
            val facts = withContext(Dispatchers.IO) { FishFacts.lookup(ctx, fish.name) }
            val reg = withContext(Dispatchers.IO) { FishRegs.lookup(ctx, area, fish.name) }
            val bag = fish.bagLimit ?: reg?.dailyBag ?: ""
            val min = fish.minSize ?: reg?.minLength ?: ""
            val body = buildString {
                facts?.let {
                    appendLine("About: ${it.about}")
                    appendLine("Identification: ${it.identification}")
                    appendLine("Habitat: ${it.habitat}")
                    appendLine("Diet & bait: ${it.diet}")
                    append("Best time: ${it.bestTime}")
                }
                reg?.notes?.takeIf { it.isNotBlank() }?.let {
                    if (isNotEmpty()) append("\n")
                    append("Regulations: $it")
                }
            }
            _fishInfo.value = FishInfoState(
                bagLimit = bag, minSize = min, body = body,
                message = if (facts == null && reg == null) "No info available for this fish." else null
            )
        }
    }

    /**
     * Open the Regs detail screen for a species by name (e.g. from the Identify result's
     * "View regs" button). Resolves the user's state for the bag/size limits.
     */
    fun openFishByName(name: String) {
        val ctx = getApplication<Application>()
        // Seed selection immediately so FishDetailScreen doesn't see a null and pop itself.
        _selectedFish.value = AreaFish(
            name = name, scientificName = "", lengthRange = "", weightRange = "",
            inSeason = true, seasonNote = ""
        )
        _fishInfo.value = FishInfoState(loading = true)
        viewModelScope.launch {
            FishFacts.ensureLoaded(ctx); FishRegs.ensureLoaded(ctx)
            val facts = withContext(Dispatchers.IO) { FishFacts.lookup(ctx, name) }
            val fix = withContext(Dispatchers.IO) { LocationProvider.lastKnown(ctx) }
            val state = fix?.let { withContext(Dispatchers.IO) { PlaceLookup.stateFor(ctx, it.first, it.second) } }
            val reg = state?.let { withContext(Dispatchers.IO) { FishRegs.lookup(ctx, it, name) } }
            if (state != null) lastFishArea = state
            openFish(
                AreaFish(
                    name = name,
                    scientificName = facts?.scientific ?: "",
                    lengthRange = facts?.length ?: "",
                    weightRange = facts?.weight ?: "",
                    inSeason = reg?.let { FishRegs.isHarvestable(it) } ?: true,
                    seasonNote = "",
                    imageUrl = FishPhotos.assetUri(ctx, name),
                    bagLimit = reg?.dailyBag,
                    minSize = reg?.minLength
                )
            )
        }
    }

    /** Re-attach bag/size limits and static facts (size/weight/scientific) to a cached list. */
    private suspend fun attachRegs(area: String, list: List<AreaFish>): List<AreaFish> =
        withContext(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            FishRegs.ensureLoaded(ctx)
            FishFacts.ensureLoaded(ctx)
            list.map { f ->
                val reg = FishRegs.lookup(ctx, area, f.name)
                val facts = FishFacts.lookup(ctx, f.name)
                f.copy(
                    bagLimit = reg?.dailyBag ?: f.bagLimit,
                    minSize = reg?.minLength ?: f.minSize,
                    inSeason = reg?.let { FishRegs.isHarvestable(it) } ?: f.inSeason,
                    scientificName = f.scientificName.ifBlank { facts?.scientific ?: "" },
                    lengthRange = f.lengthRange.ifBlank { facts?.length ?: "" },
                    weightRange = f.weightRange.ifBlank { facts?.weight ?: "" },
                    // Prefer the bundled photo even for older cached entries.
                    imageUrl = FishPhotos.assetUri(ctx, f.name) ?: f.imageUrl
                )
            }
        }

    /**
     * One photo card per regulated species in [regs], with bag/size from the dataset.
     * Disk-cached per state (key "regs|<State>") and shared by the Regs tab and map bubbles.
     */
    private suspend fun fishCardsFor(regs: StateRegs, force: Boolean): List<AreaFish> {
        val ctx = getApplication<Application>()
        val cacheKey = "regs|${regs.state}"
        if (!force) {
            val cached = withContext(Dispatchers.IO) { AreaFishStore.get(ctx, cacheKey) }
            if (cached != null) return attachRegs(regs.state, cached)
        }
        FishFacts.ensureLoaded(ctx)
        val base = regs.species.map { sp ->
            val facts = FishFacts.lookup(ctx, sp.name)
            AreaFish(
                name = sp.name,
                scientificName = facts?.scientific ?: "",
                lengthRange = facts?.length ?: "",
                weightRange = facts?.weight ?: "",
                inSeason = FishRegs.isHarvestable(sp), seasonNote = "",
                bagLimit = sp.dailyBag, minSize = sp.minLength,
                // Prefer the bundled photo (instant, no network).
                imageUrl = FishPhotos.assetUri(ctx, sp.name)
            )
        }
        val withImages = coroutineScope {
            base.map { fish ->
                async(Dispatchers.IO) {
                    if (fish.imageUrl != null) fish
                    else fish.copy(imageUrl = WikiImages.thumbnail(fish.name))
                }
            }.awaitAll()
        }
        withContext(Dispatchers.IO) { AreaFishStore.put(ctx, cacheKey, withImages) }
        return withImages
    }

    /**
     * Build Regs-style cards for a list of stocked species (iFishIllinois → Illinois regs),
     * with bundled photos + bag/size attached. Offline & instant — no network fetch.
     */
    suspend fun stockedFishCards(species: List<String>, state: String?): List<AreaFish> {
        val ctx = getApplication<Application>()
        return withContext(Dispatchers.IO) {
            FishFacts.ensureLoaded(ctx); FishRegs.ensureLoaded(ctx)
            species.map { name ->
                val facts = FishFacts.lookup(ctx, name)
                val reg = state?.let { FishRegs.lookup(ctx, it, name) }
                AreaFish(
                    name = name,
                    scientificName = facts?.scientific ?: "",
                    lengthRange = facts?.length ?: "",
                    weightRange = facts?.weight ?: "",
                    inSeason = reg?.let { FishRegs.isHarvestable(it) } ?: true,
                    seasonNote = "",
                    bagLimit = reg?.dailyBag, minSize = reg?.minLength,
                    imageUrl = FishPhotos.assetUri(ctx, name)
                )
            }
        }
    }

    /**
     * Load the full statewide regulation list for the user's location (every species in the
     * bundled dataset for their state, with photos). Resolved once per state and disk-cached —
     * no per-area re-fetching.
     */
    fun loadWhatsAround(force: Boolean = false) {
        if (_whatsAround.value.loading) return
        if (!force && _whatsAround.value.fish.isNotEmpty()) return  // keep what we have
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val fix = withContext(Dispatchers.IO) { LocationProvider.lastKnown(ctx) }
            if (fix == null) {
                _whatsAround.value = WhatsAroundState(message = "Location unknown — enable location to see your state's regulations.")
                return@launch
            }
            val state = withContext(Dispatchers.IO) { PlaceLookup.stateFor(ctx, fix.first, fix.second) }
            val regs = withContext(Dispatchers.IO) { state?.let { FishRegs.regsFor(ctx, it) } }
            if (regs == null) {
                _whatsAround.value = WhatsAroundState(
                    area = state,
                    message = "No statewide regulations on file for your location."
                )
                return@launch
            }
            lastFishArea = regs.state
            _whatsAround.update { it.copy(loading = true, area = regs.state, message = null) }
            _whatsAround.value = WhatsAroundState(area = regs.state, fish = fishCardsFor(regs, force))
        }
    }


    /** Back to the live camera (clear the last identification). */
    fun clearIdentify() {
        _idState.value = IdUiState()
    }

    /**
     * The user tapped one of the result's alternatives — make that the chosen species and
     * re-offer the rest (plus the previous pick) as alternatives, refreshing size estimates.
     */
    fun chooseIdSpecies(name: String) {
        val current = _idState.value.result ?: return
        val others = (listOf(current.species) + current.alternatives)
            .distinct()
            .filter { it != name }
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            val guess = withContext(Dispatchers.IO) { FishIdentifier.guessFor(ctx, name, others) }
            _idState.update { it.copy(result = guess) }
        }
    }

    /**
     * Seed the Add-Catch draft from the current identification (photo + species + size),
     * so the angler can review and save it. Navigate to the add screen afterwards.
     */
    fun prefillCatchFromIdentify() {
        val id = _idState.value
        val uri = id.photoUri ?: return
        val guess = id.result
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) {
                ImageStorage.copyToInternal(getApplication(), uri)
            }
            _add.value = AddUiState(
                photoPath = path,
                caughtAt = System.currentTimeMillis(),
                species = guess?.species.orEmpty(),
                confidence = guess?.confidence.orEmpty(),
                details = guess?.details.orEmpty(),
                lengthEstimate = guess?.lengthEstimate.orEmpty(),
                weightLb = guess?.weightLb.orEmpty(),
                weightOz = guess?.weightOz.orEmpty()
            )
            useCurrentLocation()
        }
    }

    /** Identify a fish from a photo without saving anything to the catch list. */
    fun identifyPhoto(uri: Uri) {
        if (!FishIdentifier.isAvailable(getApplication())) {
            _idState.value = IdUiState(
                photoUri = uri,
                message = "On-device fish recognition isn't installed yet."
            )
            return
        }
        _idState.value = IdUiState(photoUri = uri, identifying = true)
        viewModelScope.launch {
            runCatching {
                val bitmap = withContext(Dispatchers.IO) {
                    ImageStorage.decodeForUpload(getApplication(), uri)
                }
                FishIdentifier.identify(getApplication(), bitmap).getOrThrow()
            }.onSuccess { guess ->
                _idState.update {
                    if (guess.isFish) it.copy(identifying = false, result = guess, message = null)
                    else it.copy(identifying = false, result = null, message = "No fish detected.")
                }
            }.onFailure { e ->
                _idState.update { it.copy(identifying = false, message = "Couldn't identify: ${e.message}") }
            }
        }
    }

    /** Reset the draft when opening the add screen — defaults to now + current location. */
    fun startNewCatch() {
        _add.value = AddUiState(caughtAt = System.currentTimeMillis())
        useCurrentLocation()
    }

    /** Set the draft's location to the latest known GPS fix, if available/permitted. */
    fun useCurrentLocation() {
        LocationProvider.lastKnown(getApplication())?.let { (lat, lng) ->
            _add.update { it.copy(lat = lat, lng = lng) }
        }
    }

    /** Called once a photo has been captured or picked. */
    fun onPhotoChosen(source: Uri) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) {
                ImageStorage.copyToInternal(getApplication(), source)
            }
            _add.update {
                it.copy(
                    photoPath = path,
                    message = null,
                    // Re-establish the "now" default if the draft was reset (e.g. the
                    // process was killed while the camera app was foreground).
                    caughtAt = if (it.caughtAt > 0L) it.caughtAt else System.currentTimeMillis()
                )
            }
            if (_add.value.lat == null) useCurrentLocation()
            identifyCurrent()
        }
    }

    fun identifyCurrent() {
        val path = _add.value.photoPath ?: return
        if (!FishIdentifier.isAvailable(getApplication())) {
            _add.update {
                it.copy(message = "Auto-ID isn't installed yet — name your catch below.")
            }
            return
        }
        viewModelScope.launch {
            _add.update { it.copy(identifying = true, message = null) }
            val bitmap = withContext(Dispatchers.IO) { ImageStorage.decodeForUpload(path) }
            FishIdentifier.identify(getApplication(), bitmap)
                .onSuccess { guess ->
                    if (!guess.isFish) {
                        // No fish in frame — don't auto-fill anything, just say so.
                        _add.update {
                            it.copy(identifying = false, message = "No fish detected — you can still name and save it manually.")
                        }
                        return@onSuccess
                    }
                    _add.update {
                        it.copy(
                            identifying = false,
                            message = null,
                            species = guess.species,
                            confidence = guess.confidence,
                            details = guess.details,
                            lengthEstimate = guess.lengthEstimate,
                            weightLb = guess.weightLb,
                            weightOz = guess.weightOz
                        )
                    }
                }
                .onFailure { e ->
                    _add.update {
                        it.copy(identifying = false, message = "Couldn't identify: ${e.message}")
                    }
                }
        }
    }

    fun updateSpecies(value: String) = _add.update { it.copy(species = value) }
    fun updateLength(value: String) = _add.update { it.copy(lengthEstimate = value) }
    fun updateWeightLb(value: String) = _add.update { it.copy(weightLb = value.filter { c -> c.isDigit() }) }
    fun updateWeightOz(value: String) = _add.update { it.copy(weightOz = value.filter { c -> c.isDigit() }) }
    fun updateNotes(value: String) = _add.update { it.copy(notes = value) }
    fun updateTime(millis: Long) = _add.update { it.copy(caughtAt = millis) }
    fun updateLocation(lat: Double, lng: Double) = _add.update { it.copy(lat = lat, lng = lng) }

    fun save() {
        val draft = _add.value
        val path = draft.photoPath ?: return
        viewModelScope.launch {
            dao.insert(
                Catch(
                    species = draft.species.ifBlank { "Unknown" },
                    confidence = draft.confidence,
                    details = draft.details,
                    lengthEstimate = draft.lengthEstimate,
                    weightLb = draft.weightLb,
                    weightOz = draft.weightOz,
                    notes = draft.notes,
                    photoPath = path,
                    caughtAt = if (draft.caughtAt > 0L) draft.caughtAt else System.currentTimeMillis(),
                    lat = draft.lat,
                    lng = draft.lng
                )
            )
            _add.update { it.copy(saved = true) }
        }
    }

    fun delete(catch: Catch) {
        viewModelScope.launch {
            dao.delete(catch)
            withContext(Dispatchers.IO) { runCatching { File(catch.photoPath).delete() } }
        }
    }
}
