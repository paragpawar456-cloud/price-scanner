package com.parag.pricescanner.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PurchaseDao {
    @Query("SELECT * FROM purchases ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<Purchase>>

    @Query("SELECT * FROM purchases WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp ASC")
    suspend fun between(from: Long, to: Long): List<Purchase>

    @Query("SELECT DISTINCT productName FROM purchases ORDER BY productName")
    fun observeProductNames(): Flow<List<String>>

    @Insert
    suspend fun insertAll(items: List<Purchase>)

    @Insert
    suspend fun insert(item: Purchase): Long

    @Update
    suspend fun update(item: Purchase)

    @Delete
    suspend fun delete(item: Purchase)
}

@Dao
interface KnownProductDao {
    @Query("SELECT * FROM known_products WHERE barcode = :barcode LIMIT 1")
    suspend fun find(barcode: String): KnownProduct?

    @Upsert
    suspend fun upsert(product: KnownProduct)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(products: List<KnownProduct>)
}
