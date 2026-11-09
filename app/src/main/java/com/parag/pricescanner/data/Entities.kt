package com.parag.pricescanner.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "purchases", indices = [Index("timestamp"), Index("productName")])
data class Purchase(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productName: String,
    val barcode: String? = null,
    val unitPrice: Double,
    val quantity: Int,
    /** Epoch millis of the purchase. */
    val timestamp: Long,
    /** "AUTO" when counted by the scanner, "MANUAL" when entered or edited by hand. */
    val source: String = SOURCE_MANUAL,
) {
    val total: Double get() = unitPrice * quantity

    companion object {
        const val SOURCE_AUTO = "AUTO"
        const val SOURCE_MANUAL = "MANUAL"
    }
}

/** Remembers the name and last price for a barcode, so the next scan fills them in. */
@Entity(tableName = "known_products")
data class KnownProduct(
    @PrimaryKey val barcode: String,
    val name: String,
    val lastPrice: Double,
)
