package nl.bluecard.engine.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** A single playing card. Serialized compactly as rank code + suit code, e.g. "10H", "QS", "2C". */
@Serializable(with = CardSerializer::class)
data class Card(val rank: Rank, val suit: Suit) : Comparable<Card> {

    val code: String get() = rank.code + suit.code

    /** Orders by rank first, then by suit (clubs < diamonds < hearts < spades). */
    override fun compareTo(other: Card): Int =
        compareValuesBy(this, other, { it.rank.value }, { it.suit.ordinal })

    override fun toString(): String = code

    companion object {
        /** Parses the compact notation; returns null for invalid input. */
        fun parse(code: String): Card? {
            val trimmed = code.trim()
            if (trimmed.length < 2) return null
            val suit = Suit.fromCode(trimmed.last()) ?: return null
            val rank = Rank.fromCode(trimmed.dropLast(1)) ?: return null
            return Card(rank, suit)
        }

        /** Convenience for tests and fixtures; throws on invalid input. */
        fun of(code: String): Card = requireNotNull(parse(code)) { "Invalid card code: $code" }
    }
}

object CardSerializer : KSerializer<Card> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("nl.bluecard.engine.model.Card", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Card) = encoder.encodeString(value.code)

    override fun deserialize(decoder: Decoder): Card {
        val raw = decoder.decodeString()
        return Card.parse(raw) ?: throw SerializationException("Invalid card code: $raw")
    }
}
