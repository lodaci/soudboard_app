package it.lodaci.soundboard.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class TrackKind { MUSIC, SFX }

/** LOCAL = file del telefono (content://), STREAM = link riprodotto online, DOWNLOADED = scaricato nella memoria dell'app */
enum class SourceType { LOCAL, STREAM, DOWNLOADED }

@Entity(tableName = "tracks")
data class Track(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val kind: TrackKind,
    val source: SourceType,
    /** content:// per LOCAL, percorso file per DOWNLOADED, URL diretto (se il link è già un file audio) per STREAM */
    val uri: String?,
    /** link originale (YouTube ecc.) */
    val remoteUrl: String? = null,
    val durationMs: Long = 0,
    val volume: Float = 1f,
    val loop: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    /** crediti per i suoni presi da archivi online (licenze CC‑BY ecc.) */
    val author: String? = null,
    val license: String? = null,
    val sourceUrl: String? = null,
)

@Entity(tableName = "boards")
data class Board(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val columns: Int = 4,
    val slots: Int = 24,
    val sortOrder: Int = 0,
)

@Entity(
    tableName = "pads",
    primaryKeys = ["boardId", "position"],
    foreignKeys = [
        ForeignKey(entity = Board::class, parentColumns = ["id"], childColumns = ["boardId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Track::class, parentColumns = ["id"], childColumns = ["trackId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("trackId")],
)
data class Pad(
    val boardId: Long,
    val position: Int,
    val trackId: Long?,
    val emoji: String = "",
    val label: String = "",
    val color: Int = DEFAULT_PAD_COLOR,
    val fade: Boolean = true,
) {
    companion object {
        const val DEFAULT_PAD_COLOR: Int = 0xFF3A3150.toInt()
    }
}
