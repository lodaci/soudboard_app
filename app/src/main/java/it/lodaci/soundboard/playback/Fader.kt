package it.lodaci.soundboard.playback

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Curve di dissolvenza "equal power": suonano più naturali di una rampa lineare. */
object Fader {
    /** Guadagno al tempo normalizzato [t] (0..1) passando da [start] a [end]. */
    fun value(start: Float, end: Float, t: Float): Float {
        val x = t.coerceIn(0f, 1f).toDouble()
        val k = if (end >= start) sin(x * PI / 2) else 1 - cos(x * PI / 2)
        return start + (end - start) * k.toFloat()
    }

    /** Durata residua proporzionale a quanto manca: un fade out partito a metà volume dura la metà. */
    fun scaledDuration(fullMs: Long, start: Float, end: Float): Long =
        (fullMs * kotlin.math.abs(end - start).coerceIn(0f, 1f)).toLong()
}
