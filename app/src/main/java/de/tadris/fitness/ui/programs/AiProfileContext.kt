/*
 * Copyright (c) 2026 Jannis Scheibe <jannis@tadris.de>
 *
 * This file is part of FitoTrack.
 *
 * This file was added in FitRegimen, a fork of FitoTrack.
 */
package de.tadris.fitness.ui.programs

import de.tadris.fitness.data.FitnessProfile
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Deliberately anonymous, prompt-only projection of a local fitness profile.
 * Direct identity columns (name, uuid and birth date) never leave the profile picker.
 */
internal data class AiProfileContext(
    val ageYears: Int? = null,
    val heightCm: Double? = null,
    val weightKg: Double? = null,
    val stepLengthCm: Double? = null,
    val gender: String? = null,
    val fitnessLevel: String? = null,
    val notes: String? = null,
    val customParameters: String? = null,
)

internal fun FitnessProfile.toAiProfileContext(
    today: LocalDate = LocalDate.now(),
): AiProfileContext {
    val identityTokens = buildList {
        val normalizedName = name.trim()
        normalizedName
            .takeIf { it.length >= 2 && it.lowercase() !in GENERIC_PROFILE_NAMES }
            ?.let(::add)
        normalizedName
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 2 && it.lowercase() !in GENERIC_PROFILE_NAMES }
            .forEach(::add)
        uuid.trim().takeIf { it.isNotEmpty() }?.let(::add)
        birthDate()?.let { date ->
            add(date.toString())
            add(date.format(DateTimeFormatter.ofPattern("dd.MM.uuuu")))
            add(date.format(DateTimeFormatter.ofPattern("dd/MM/uuuu")))
            add(date.format(DateTimeFormatter.ofPattern("MM/dd/uuuu")))
        }
    }.distinct()
    return AiProfileContext(
        ageYears = ageOn(today),
        heightCm = heightCm,
        weightKg = weightKg,
        stepLengthCm = stepLengthCm,
        gender = gender,
        fitnessLevel = fitnessLevel,
        notes = notes.redactProfileIdentity(identityTokens),
        customParameters = customAttributesJson.toReadableProfileParameters(identityTokens),
    )
}

internal fun AiProfileContext.toPromptLine(russian: Boolean): String {
    val values = buildList {
        ageYears?.takeIf { it in 1..120 }?.let {
            add(if (russian) "возраст: $it" else "age: $it")
        }
        heightCm?.takeIf { it > 0.0 }?.let {
            add(if (russian) "рост: ${it.promptNumber()} см" else "height: ${it.promptNumber()} cm")
        }
        weightKg?.takeIf { it > 0.0 }?.let {
            add(if (russian) "вес: ${it.promptNumber()} кг" else "weight: ${it.promptNumber()} kg")
        }
        stepLengthCm?.takeIf { it > 0.0 }?.let {
            add(
                if (russian) "длина шага: ${it.promptNumber()} см"
                else "step length: ${it.promptNumber()} cm"
            )
        }
        gender.cleanPromptValue()?.let {
            add(if (russian) "пол/гендер: $it" else "sex/gender: $it")
        }
        fitnessLevel.cleanPromptValue()?.let {
            add(if (russian) "уровень подготовки: $it" else "fitness level: $it")
        }
        notes.cleanPromptValue()?.let {
            add(if (russian) "важные особенности: $it" else "relevant notes: $it")
        }
        customParameters.cleanPromptValue()?.let {
            add(if (russian) "дополнительные параметры: $it" else "additional parameters: $it")
        }
    }
    return values.joinToString("; ").ifBlank {
        if (russian) "параметры не указаны" else "parameters not specified"
    }
}

private fun String?.cleanPromptValue(): String? = this
    ?.trim()
    ?.replace(Regex("[\\r\\n\\t]+"), " ")
    ?.replace(Regex(" {2,}"), " ")
    ?.take(MAX_PROMPT_PROFILE_TEXT_LENGTH)
    ?.takeIf { it.isNotBlank() }

private fun Double.promptNumber(): String = BigDecimal.valueOf(this)
    .stripTrailingZeros()
    .toPlainString()

private fun String?.toReadableProfileParameters(identityTokens: List<String>): String? {
    val raw = this?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching {
        val objectValue = Json.parseToJsonElement(raw) as? JsonObject ?: return@runCatching null
        objectValue.entries
            .asSequence()
            .filter { (key, _) ->
                val normalizedKey = key.lowercase().replace(Regex("[^a-zа-я0-9]+"), "_").trim('_')
                normalizedKey !in PRIVATE_PROFILE_ATTRIBUTE_KEYS &&
                    !key.containsProfileIdentity(identityTokens)
            }
            .mapNotNull { (key, value) ->
                val primitive = value as? JsonPrimitive ?: return@mapNotNull null
                val displayKey = key.cleanPromptValue()?.take(80) ?: return@mapNotNull null
                val displayValue = primitive.contentOrNull
                    ?.redactProfileIdentity(identityTokens)
                    ?: return@mapNotNull null
                "$displayKey: $displayValue"
            }
            .take(MAX_CUSTOM_PROFILE_ATTRIBUTES)
            .joinToString("; ")
            .ifBlank { null }
    }.getOrNull()
}

private fun String?.redactProfileIdentity(identityTokens: List<String>): String? {
    var value = this?.cleanPromptValue() ?: return null
    identityTokens.sortedByDescending(String::length).forEach { token ->
        value = value.replace(identityTokenPattern(token), PROFILE_REDACTION)
    }
    value = value
        .replace(EMAIL_PATTERN, PROFILE_REDACTION)
        .replace(UUID_PATTERN, PROFILE_REDACTION)
        .replace(ISO_DATE_PATTERN, PROFILE_REDACTION)
        .replace(PHONE_PATTERN, PROFILE_REDACTION)
        .replace(Regex("(?:${Regex.escape(PROFILE_REDACTION)}[ ,;:]*){2,}"), "$PROFILE_REDACTION ")
    return value.cleanPromptValue()
}

private fun String.containsProfileIdentity(identityTokens: List<String>): Boolean =
    identityTokens.any { identityTokenPattern(it).containsMatchIn(this) }

/** Exact Unicode word/phrase matching avoids replacing a short name inside unrelated words. */
private fun identityTokenPattern(token: String): Regex = Regex(
    pattern = "(?iu)(?<![\\p{L}\\p{N}])${Regex.escape(token)}(?![\\p{L}\\p{N}])",
)

private const val MAX_PROMPT_PROFILE_TEXT_LENGTH = 1_500
private const val MAX_CUSTOM_PROFILE_ATTRIBUTES = 24
private const val PROFILE_REDACTION = "[private]"

private val GENERIC_PROFILE_NAMES = setOf("user", "profile", "пользователь", "профиль")
private val PRIVATE_PROFILE_ATTRIBUTE_KEYS = setOf(
    "name", "full_name", "first_name", "last_name", "email", "e_mail", "phone", "telephone",
    "birth_date", "birthdate", "birthday", "dob", "address", "uuid", "id", "passport",
    "document", "social_security", "snils", "инн", "снилс", "имя", "фамилия", "адрес",
    "телефон", "почта", "дата_рождения",
)
private val EMAIL_PATTERN = Regex("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b")
private val UUID_PATTERN = Regex("(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\b")
private val ISO_DATE_PATTERN = Regex("\\b(?:19|20)\\d{2}-[01]\\d-[0-3]\\d\\b")
private val PHONE_PATTERN = Regex("(?<!\\w)(?:\\+?\\d[ ()-]*){8,15}(?!\\w)")
