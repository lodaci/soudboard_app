package it.lodaci.soundboard.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<Track>>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun get(id: Long): Track?

    @Insert
    suspend fun insert(track: Track): Long

    @Update
    suspend fun update(track: Track)

    @Delete
    suspend fun delete(track: Track)
}

@Dao
interface BoardDao {
    @Query("SELECT * FROM boards ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<Board>>

    @Query("SELECT COUNT(*) FROM boards")
    suspend fun count(): Int

    @Insert
    suspend fun insert(board: Board): Long

    @Update
    suspend fun update(board: Board)

    @Delete
    suspend fun delete(board: Board)
}

@Dao
interface PadDao {
    @Query("SELECT * FROM pads WHERE boardId = :boardId")
    fun observeForBoard(boardId: Long): Flow<List<Pad>>

    @Upsert
    suspend fun upsert(pad: Pad)

    @Query("DELETE FROM pads WHERE boardId = :boardId AND position = :position")
    suspend fun clear(boardId: Long, position: Int)
}

@Database(entities = [Track::class, Board::class, Pad::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun boardDao(): BoardDao
    abstract fun padDao(): PadDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "soundboard.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        /** v2: crediti (autore, licenza, pagina d'origine) per i suoni degli archivi online */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tracks ADD COLUMN author TEXT")
                db.execSQL("ALTER TABLE tracks ADD COLUMN license TEXT")
                db.execSQL("ALTER TABLE tracks ADD COLUMN sourceUrl TEXT")
            }
        }
    }
}
