package com.example.tcgtracker

import android.content.Context
import androidx.room.*

@Entity(tableName = "saved_cards")
data class SavedCard(
    @PrimaryKey val id: String,
    val name: String,
    val publicCode: String,
    val imageUrl: String,
    val usdPrice: Double,
    val cadPrice: Double,
    val quantity: Int
)

@Dao
interface CardDao {
    @Query("SELECT * FROM saved_cards")
    suspend fun getAllCards(): List<SavedCard>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCard(card: SavedCard)

    @Delete
    suspend fun deleteCard(card: SavedCard)
}

@Database(entities = [SavedCard::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tcg_tracker_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}