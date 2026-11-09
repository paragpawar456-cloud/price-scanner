package com.parag.pricescanner.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Purchase::class, KnownProduct::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun purchases(): PurchaseDao
    abstract fun knownProducts(): KnownProductDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE purchases ADD COLUMN mfgDate INTEGER")
                db.execSQL("ALTER TABLE purchases ADD COLUMN expiryDate INTEGER")
            }
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "pricescan.db")
                .addMigrations(MIGRATION_1_2)
                .build()
                .also { instance = it }
        }
    }
}
