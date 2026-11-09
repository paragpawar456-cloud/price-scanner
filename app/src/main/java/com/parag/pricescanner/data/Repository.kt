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
            .map { KnownProduct(it.barcode!!, it.productName, it.unitPrice, it.nutrition, it.imageUrl, it.category, it.details) }
        if (known.isNotEmpty()) db.knownProducts().upsertAll(known)
    }

    suspend fun add(item: Purchase) = save(listOf(item))
    suspend fun update(item: Purchase) {
        db.purchases().update(item)
        item.barcode?.takeIf { it.isNotBlank() }?.let {
            db.knownProducts().upsert(KnownProduct(it, item.productName, item.unitPrice, item.nutrition, item.imageUrl, item.category, item.details))
        }
    }
    suspend fun delete(item: Purchase) = db.purchases().delete(item)
    suspend fun findKnown(barcode: String) = db.knownProducts().find(barcode)
    suspend fun rememberProduct(p: KnownProduct) = db.knownProducts().upsert(p)

    /** Adds online photos to saved purchases that don't have one yet (runs quietly; needs internet). */
    suspend fun backfillPhotos() {
        val dao = db.purchases()
        for (code in dao.barcodesWithoutPhoto()) {
            val url = db.knownProducts().find(code)?.imageUrl ?: ProductLookup.fetch(code)?.imageUrl ?: continue
            dao.setPhotoByBarcode(code, url)
        }
        for (name in dao.namesWithoutPhoto()) {
            val url = ProductLookup.searchImage(name) ?: continue
            dao.setPhotoByName(name, url)
        }
    }

    /** Looks up a photo for one manually added or renamed purchase. */
    suspend fun fillPhoto(name: String) {
        ProductLookup.searchImage(name)?.let { db.purchases().setPhotoByName(name, it) }
    }

    companion object {
        @Volatile private var instance: Repository? = null
        fun get(context: Context): Repository = instance ?: synchronized(this) {
            instance ?: Repository(AppDatabase.get(context)).also { instance = it }
        }
    }
}
