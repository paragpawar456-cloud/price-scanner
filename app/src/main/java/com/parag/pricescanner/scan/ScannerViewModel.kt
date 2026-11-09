package com.parag.pricescanner.scan

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.parag.pricescanner.data.Purchase
import com.parag.pricescanner.data.Repository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class CartItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val price: Double,
    val qty: Int,
    val barcode: String? = null,
    val auto: Boolean = false,
) {
    val total get() = price * qty
    val needsPrice get() = price <= 0.0
}

/** What the scanner currently, steadily sees. */
data class Detection(
    val price: Double?,
    val alternatives: List<Double>,
    val name: String?,
    val barcode: String?,
    val knownName: String? = null,
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
    private val counter = AutoCounter(stableFrames = 3, clearMillis = 700)
    private val recentPrices = ArrayDeque<Double?>()
    private val recentNames = ArrayDeque<String>()
    private var eventSeq = 0L
    private val knownCache = mutableMapOf<String, Pair<String, Double>?>()

    private val _state = MutableStateFlow(ScannerUiState())
    val state: StateFlow<ScannerUiState> = _state.asStateFlow()

    val productNames = repo.productNames

    fun isPaused() = _state.value.paused

    fun setPaused(paused: Boolean) = _state.update { it.copy(paused = paused) }
    fun setAutoMode(auto: Boolean) {
        counter.reset()
        _state.update { it.copy(autoMode = auto) }
    }
    fun toggleTorch() = _state.update { it.copy(torchOn = !it.torchOn) }
    fun consumeSaved() = _state.update { it.copy(savedMessage = null) }

    /** Called for every analysed camera frame (main thread). */
    fun onFrame(frame: FrameResult) {
        if (_state.value.paused) return

        // Smooth OCR noise: a price must appear in 3 of the last 5 frames.
        recentPrices.addLast(frame.bestPrice)
        if (recentPrices.size > 5) recentPrices.removeFirst()
        frame.nameGuess?.let {
            recentNames.addLast(it)
            if (recentNames.size > 8) recentNames.removeFirst()
        }
        val stablePrice = recentPrices.filterNotNull().groupingBy { it }.eachCount()
            .filter { it.value >= 3 }.maxByOrNull { it.value }?.key
        val name = recentNames.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

        val barcode = frame.barcode
        val known = barcode?.let { knownCache[it] }
        if (barcode != null && !knownCache.containsKey(barcode)) lookupKnown(barcode)

        val price = stablePrice ?: known?.second
        val detection = if (price == null && barcode == null) null else Detection(
            price = price,
            alternatives = frame.prices.map { it.value }.filter { it != price }.take(3),
            name = name,
            barcode = barcode,
            knownName = known?.first,
        )
        // Keep the last steady result while OCR settles; clear it once the frame is empty.
        _state.update { it.copy(detection = detection ?: if (frame.isEmpty) null else it.detection) }

        if (_state.value.autoMode) {
            val key = barcode?.let { "b:$it" } ?: stablePrice?.let { "p:$it" }
            val counted = counter.onFrame(key, System.currentTimeMillis())
            if (counted != null && detection != null) addScanned(detection, auto = true)
        }
    }

    private fun lookupKnown(barcode: String) {
        knownCache[barcode] = null
        viewModelScope.launch {
            repo.findKnown(barcode)?.let { knownCache[barcode] = it.name to it.lastPrice }
        }
    }

    /** Adds one unit of the detected product, merging with an existing cart line. */
    private fun addScanned(d: Detection, auto: Boolean) {
        val price = d.price ?: 0.0
        _state.update { s ->
            val idx = s.cart.indexOfFirst { item ->
                if (d.barcode != null) item.barcode == d.barcode
                else item.barcode == null && item.price == price
            }
            val cart = s.cart.toMutableList()
            val line = if (idx >= 0) {
                cart[idx].copy(qty = cart[idx].qty + 1).also { cart[idx] = it }
            } else {
                val name = d.displayName ?: if (price > 0) "Item ₹${com.parag.pricescanner.util.Fmt.price(price)}" else "New item"
                CartItem(name = name, price = price, qty = 1, barcode = d.barcode, auto = auto).also { cart.add(0, it) }
            }
            s.copy(cart = cart, lastCount = CountEvent(++eventSeq, line.name, line.qty))
        }
    }

    /** Manual mode: add with explicit values. */
    fun addManual(name: String, price: Double, qty: Int, barcode: String?) {
        if (qty <= 0) return
        _state.update { s ->
            val idx = s.cart.indexOfFirst {
                it.name.equals(name, ignoreCase = true) && it.price == price && it.barcode == barcode
            }
            val cart = s.cart.toMutableList()
            if (idx >= 0) cart[idx] = cart[idx].copy(qty = cart[idx].qty + qty)
            else cart.add(0, CartItem(name = name.ifBlank { "Item" }, price = price, qty = qty, barcode = barcode))
            s.copy(cart = cart, lastCount = CountEvent(++eventSeq, name, qty))
        }
    }

    /** Count one more of whatever the camera currently sees (works in either mode). */
    fun countCurrent() {
        _state.value.detection?.let { addScanned(it, auto = _state.value.autoMode) }
    }

    fun updateItem(item: CartItem) = _state.update { s ->
        s.copy(cart = s.cart.map { if (it.id == item.id) item else it }.filter { it.qty > 0 })
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
                )
            })
            items.forEach { i -> i.barcode?.let { knownCache[it] = i.name to i.price } }
            counter.reset()
            _state.update {
                it.copy(
                    cart = emptyList(),
                    savedMessage = "Saved ${items.sumOf { i -> i.qty }} items · ${com.parag.pricescanner.util.Fmt.money(items.sumOf { i -> i.total })}",
                )
            }
        }
    }
}
