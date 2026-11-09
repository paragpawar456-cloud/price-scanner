package com.parag.pricescanner.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class Repository private constructor(private val db: AppDatabase) {

    val purchases: Flow<List<Purchase>> = db.purchases().observeAll()
    val productNames: Flow<List<String>> = db.purchases().observeProductNames()

    suspend fun between(from: Long, to: Long) = db.purchases().between(from, to)

    suspend fun save(items: List<Purchase>) {
        if (items.isEmpty()) return
        db.purchases().insertAll(items)
        val known = items.filter { !it.barcode.isNullOrBlank() }
            .map { KnownProduct(it.barcode!!, it.productName, it.unitPrice) }
        if (known.isNotEmpty()) db.knownProducts().upsertAll(known)
    }

    suspend fun add(item: Purchase) = save(listOf(item))
    suspend fun update(item: Purchase) {
        db.purchases().update(item)
        item.barcode?.takeIf { it.isNotBlank() }?.let {
            db.knownProducts().upsert(KnownProduct(it, item.productName, item.unitPrice))
        }
    }
    suspend fun delete(item: Purchase) = db.purchases().delete(item)
    suspend fun findKnown(barcode: String) = db.knownProducts().find(barcode)

    companion object {
        @Volatile private var instance: Repository? = null
        fun get(context: Context): Repository = instance ?: synchronized(this) {
            instance ?: Repository(AppDatabase.get(context)).also { instance = it }
        }
    }
}
