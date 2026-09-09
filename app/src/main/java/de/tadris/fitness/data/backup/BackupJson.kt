/* Added in FitRegimen. Licensed under GPLv3 or later. */
package de.tadris.fitness.data.backup

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.StreamReadFeature
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule

/** Use Kotlin property names on both sides of the full-backup boundary (isActive/isRest). */
internal fun backupJsonMapper() = jsonMapper {
    addModule(kotlinModule())
    configure(StreamReadFeature.IGNORE_UNDEFINED, true)
    configure(StreamReadFeature.AUTO_CLOSE_SOURCE, false)
    disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET)
    disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
}
