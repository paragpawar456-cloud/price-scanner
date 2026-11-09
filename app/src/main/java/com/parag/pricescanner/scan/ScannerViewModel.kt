package com.parag.pricescanner.scan

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.parag.pricescanner.data.KnownProduct
import com.parag.pricescanner.data.ProductLookup
import com.parag.pricescanner.data.Purchase
import com.parag.pricescanner.data.Repository
import com.parag.pricescanner.util.Fmt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

data class CartItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val price: Double,
    val qty: Int,
    val barcode: String? = null,
    val auto: Boolean = false,
    val mfgDate: LocalDate? = null,
    val expiryDate: LocalDate? = null,
    val nutrition: Nutrition? = null,
    /** True once the name came from the user or a trusted source, so scanning stops changing it. */
    val nameLocked: Boolean = false,
) {
    val total get() = price * qty
    val needsPrice get() = price <= 0.0
}

/** What the scanner currently sees (one pack). */
data class Detection(
    val price: Double?,
    val alternatives: List<Double>,
    val name: String?,
    val barcode: String?,
    val knownName: String? = null,
    val mfgDate: LocalDate? = null,
    val expiryDate: LocalDate? = null,
    val nutrition: Nutrition? = null,
) {
    val displayName: String? get() = knownName ?: name
}

data class CountEvent(val id: Long, val itemName: String, val qty: Int)

data class ScannerUiState(
    val autoMode: Boolean = true,
    val detection: Detection? = null,
    val cart: List<CartItem> = emptyList(),
    val torchOn: Boolean = false,
    val paused: Boolean = false,
    val lastCount: CountEvent? = null,
    val savedMessage: String? = null,
) {
    val cartTotal get() = cart.sumOf { it.total }
    val cartQty get() = cart.sumOf { it.qty }
}

class ScannerViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repository.get(app)
    private val tracker = SessionTracker()
    private var eventSeq = 0L

    /** Cart line that the current session counted into, and whether that line was created by it. */
    private var sessionLine: Pair<Long, String>? = null
    private var sessionCreatedLine = false

    /** barcode -> known product (null while loading / not found). */
    private val known = mutableMapOf<String, KnownProduct?>()

    private val _state = MutableStateFlow(ScannerUiState())
    val state: StateFlow<ScannerUiState> = _state.asStateFlow()

    val productNames = repo.productNames

    fun isPaused() = _state.value.paused
    fun setPaused(paused: Boolean) = _state.update { it.copy(paused = paused) }
    fun setAutoMode(auto: Boolean) {
        tracker.reset()
        sessionLine = null
        _state.update { it.copy(autoMode = auto) }
    }
    fun toggleTorch() = _state.update { it.copy(torchOn = !it.torchOn) }
    fun consumeSaved() = _state.update { it.copy(savedMessage = null) }

    /** Called for every analysed camera frame (main thread). */
    fun onFrame(frame: FrameResult) {
        if (_state.value.paused) return
        val snap = tracker.onFrame(frame, System.currentTimeMillis())
        if (snap == null) {
            sessionLine = null
            _state.update { it.copy(detection = null) }
            return
        }

        snap.barcode?.let { if (!known.containsKey(it)) lookup(it) }
        val kp = snap.barcode?.let { known[it] }
        val detection = Detection(
            price = snap.price ?: kp?.lastPrice?.takeIf { it > 0 },
            alternatives = snap.alternatives,
            name = snap.name,
            barcode = snap.barcode,
            knownName = kp?.name,
            mfgDate = snap.mfgDate,
            expiryDate = snap.expiryDate,
            nutrition = (snap.nutrition ?: Nutrition()).merge(Nutrition.decode(kp?.nutrition)).takeIf { !it.isEmpty },
        )
        val showable = snap.identified || detection.nutrition != null || detection.mfgDate != null || detection.expiryDate != null
        _state.update { s -> s.copy(detection = detection.takeIf { showable }) }

        if (!_state.value.autoMode) return
        val line = sessionLine
        if (snap.countNow) {
            val id = addScanned(detection, auto = true)
            sessionLine = snap.sessionId to id.first
            sessionCreatedLine = id.second
        } else if (line != null && line.first == snap.sessionId) {
            enrich(line.second, detection, sessionCreatedLine)
        }
    }

    /** Fills in details read later in the same session, without changing the count. */
    private fun enrich(lineId: String, d: Detection, createdBySession: Boolean) = _state.update { s ->
        s.copy(cart = s.cart.map { item ->
            if (item.id != lineId) item else item.copy(
                barcode = item.barcode ?: d.barcode,
                price = if (createdBySession && item.qty == 1 && d.price != null) d.price else item.price.takeIf { it > 0 } ?: d.price ?: 0.0,
                name = if (!item.nameLocked && createdBySession && d.displayName != null) d.displayName!! else item.name,
                nameLocked = item.nameLocked || (d.knownName != null && createdBySession),
                mfgDate = item.mfgDate ?: d.mfgDate,
                expiryDate = item.expiryDate ?: d.expiryDate,
                nutrition = (d.nutrition ?: Nutrition()).merge(item.nutrition).takeIf { !it.isEmpty },
            )
        })
    }

    private fun lookup(barcode: String) {
        known[barcode] = null
        viewModelScope.launch {
            val local = repo.findKnown(barcode)
            if (local != null) known[barcode] = local
            if (local == null || local.nutrition == null) {
                ProductLookup.fetch(barcode)?.let { online ->
                    val merged = KnownProduct(
                        barcode = barcode,
                        name = local?.name ?: online.name,
                        lastPrice = local?.lastPrice ?: 0.0,
                        nutrition = online.nutrition?.encode() ?: local?.nutrition,
                    )
                    known[barcode] = merged
                    repo.rememberProduct(merged)
                }
            }
            known[barcode]?.let { kp -> applyKnown(kp) }
        }
    }

    /** Updates cart lines for this barcode with the looked-up name and nutrition. */
    private fun applyKnown(kp: KnownProduct) = _state.update { s ->
        s.copy(cart = s.cart.map { item ->
            if (item.barcode != kp.barcode) item else item.copy(
                name = if (item.nameLocked) item.name else kp.name,
                nameLocked = true,
                price = if (item.needsPrice && kp.lastPrice > 0) kp.lastPrice else item.price,
                nutrition = (item.nutrition ?: Nutrition()).merge(Nutrition.decode(kp.nutrition)).takeIf { !it.isEmpty },
            )
        })
    }

    /** Adds one unit of the detected product. @return the cart line id and whether it was newly created. */
    private fun addScanned(d: Detection, auto: Boolean): Pair<String, Boolean> {
        val price = d.price ?: 0.0
        var result = "" to false
        _state.update { s ->
            val idx = s.cart.indexOfFirst { item ->
                when {
                    d.barcode != null && item.barcode != null -> item.barcode == d.barcode
                    d.barcode != null -> price > 0 && item.price == price
                    else -> price > 0 && item.price == price
                }
            }
            val cart = s.cart.toMutableList()
            val line = if (idx >= 0) {
                cart[idx].copy(
                    qty = cart[idx].qty + 1,
                    barcode = cart[idx].barcode ?: d.barcode,
                    mfgDate = cart[idx].mfgDate ?: d.mfgDate,
                    expiryDate = cart[idx].expiryDate ?: d.expiryDate,
                    nutrition = (cart[idx].nutrition ?: Nutrition()).merge(d.nutrition).takeIf { !it.isEmpty },
                ).also { cart[idx] = it; result = it.id to false }
            } else {
                val name = d.displayName ?: if (price > 0) "Item ₹${Fmt.price(price)}" else "New item"
                CartItem(
                    name = name, price = price, qty = 1, barcode = d.barcode, auto = auto,
                    mfgDate = d.mfgDate, expiryDate = d.expiryDate, nutrition = d.nutrition,
                    nameLocked = d.knownName != null,
                ).also { cart.add(0, it); result = it.id to true }
            }
            s.copy(cart = cart, lastCount = CountEvent(++eventSeq, line.name, line.qty))
        }
        return result
    }

    /** Manual add with explicit values. */
    fun addManual(
        name: String, price: Double, qty: Int, barcode: String?,
        mfgDate: LocalDate? = null, expiryDate: LocalDate? = null, nutrition: Nutrition? = null,
    ) {
        if (qty <= 0) return
        _state.update { s ->
            val idx = s.cart.indexOfFirst {
                it.name.equals(name, ignoreCase = true) && it.price == price && (barcode == null || it.barcode == null || it.barcode == barcode)
            }
            val cart = s.cart.toMutableList()
            if (idx >= 0) cart[idx] = cart[idx].copy(
                qty = cart[idx].qty + qty,
                barcode = cart[idx].barcode ?: barcode,
                mfgDate = mfgDate ?: cart[idx].mfgDate,
                expiryDate = expiryDate ?: cart[idx].expiryDate,
                nutrition = (nutrition ?: Nutrition()).merge(cart[idx].nutrition).takeIf { !it.isEmpty },
            )
            else cart.add(
                0,
                CartItem(
                    name = name.ifBlank { "Item" }, price = price, qty = qty, barcode = barcode,
                    mfgDate = mfgDate, expiryDate = expiryDate, nutrition = nutrition, nameLocked = true,
                ),
            )
            s.copy(cart = cart, lastCount = CountEvent(++eventSeq, name, qty))
        }
    }

    /** Count one more of whatever the camera currently sees (works in either mode). */
    fun countCurrent() {
        _state.value.detection?.let { addScanned(it, auto = _state.value.autoMode) }
    }

    fun updateItem(item: CartItem) = _state.update { s ->
        s.copy(cart = s.cart.map { if (it.id == item.id) item.copy(nameLocked = true) else it }.filter { it.qty > 0 })
    }

    fun changeQty(id: String, delta: Int) = _state.update { s ->
        s.copy(cart = s.cart.map { if (it.id == id) it.copy(qty = it.qty + delta) else it }.filter { it.qty > 0 })
    }

    fun removeItem(id: String) = _state.update { s -> s.copy(cart = s.cart.filterNot { it.id == id }) }
    fun clearCart() = _state.update { it.copy(cart = emptyList()) }

    fun saveCart() {
        val items = _state.value.cart
        if (items.isEmpty()) return
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            repo.save(items.map {
                Purchase(
                    productName = it.name,
                    barcode = it.barcode,
                    unitPrice = it.price,
                    quantity = it.qty,
                    timestamp = now,
                    source = if (it.auto) Purchase.SOURCE_AUTO else Purchase.SOURCE_MANUAL,
                    mfgDate = it.mfgDate?.toEpochDay(),
                    expiryDate = it.expiryDate?.toEpochDay(),
                    nutrition = it.nutrition?.encode(),
                )
            })
            items.forEach { i ->
                i.barcode?.let { known[it] = KnownProduct(it, i.name, i.price, i.nutrition?.encode()) }
            }
            tracker.reset()
            sessionLine = null
            _state.update {
                it.copy(
                    cart = emptyList(),
                    savedMessage = "Saved ${items.sumOf { i -> i.qty }} items · ${Fmt.money(items.sumOf { i -> i.total })}",
                )
            }
        }
    }
}
