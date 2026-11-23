package io.github.majorbolt.dockinggrid.geometry

/**
 * Sizes fixed-size elements that share [available] space with a flexible remainder (the center).
 *
 * 1. If the desired sizes and [remainderMinimum] fit, the desired sizes are used and the remainder takes the rest.
 * 2. Otherwise elements shrink towards their minimum, proportionally to how far each is above it.
 * 3. If even the minimums do not fit, elements and remainder shrink proportionally to their minimums.
 *
 * Sizes are never negative.
 */
internal fun distributeWithRemainder(
    desired: List<Float>,
    minimum: List<Float>,
    remainderMinimum: Float,
    available: Float,
): List<Float> {
    val space = available.coerceAtLeast(0f)
    val clampedMinimum = minimum.map { value -> value.coerceAtLeast(0f) }
    val clampedDesired = desired.zip(clampedMinimum) { desiredValue, minimumValue -> desiredValue.coerceAtLeast(minimumValue) }
    val desiredTotal = clampedDesired.sum()
    val minimumTotal = clampedMinimum.sum()
    val remainderNeed = remainderMinimum.coerceAtLeast(0f)

    if (desiredTotal + remainderNeed <= space) return clampedDesired

    if (minimumTotal + remainderNeed <= space) {
        val excess = desiredTotal + remainderNeed - space
        val slackTotal = desiredTotal - minimumTotal
        return clampedDesired.zip(clampedMinimum) { desiredValue, minimumValue ->
            val slack = desiredValue - minimumValue
            if (slackTotal > 0f) desiredValue - excess * slack / slackTotal else minimumValue
        }
    }

    val totalNeed = minimumTotal + remainderNeed
    if (totalNeed <= 0f) return clampedMinimum.map { 0f }
    val scale = space / totalNeed
    return clampedMinimum.map { minimumValue -> minimumValue * scale }
}

/** Sizes elements that together fill exactly [available], honouring minimums as far as possible. */
internal fun distributeToFill(
    desired: List<Float>,
    minimum: List<Float>,
    available: Float,
): List<Float> {
    if (desired.isEmpty()) return emptyList()
    val space = available.coerceAtLeast(0f)
    val clampedMinimum = minimum.map { value -> value.coerceAtLeast(0f) }
    val minimumTotal = clampedMinimum.sum()
    if (minimumTotal >= space) {
        if (minimumTotal <= 0f) return desired.map { space / desired.size }
        return clampedMinimum.map { minimumValue -> minimumValue * space / minimumTotal }
    }
    val desiredTotal = desired.sum()
    val scaledDesired = if (desiredTotal > 0f) desired.map { value -> value * space / desiredTotal } else desired.map { space / desired.size }

    // Raise elements below their minimum and take the difference from the others, proportionally to their slack.
    val deficit = scaledDesired.zip(clampedMinimum) { value, minimumValue -> (minimumValue - value).coerceAtLeast(0f) }.sum()
    if (deficit <= 0f) return scaledDesired
    val slackTotal = scaledDesired.zip(clampedMinimum) { value, minimumValue -> (value - minimumValue).coerceAtLeast(0f) }.sum()
    return scaledDesired.zip(clampedMinimum) { value, minimumValue ->
        if (value <= minimumValue) {
            minimumValue
        } else {
            value - deficit * (value - minimumValue) / slackTotal
        }
    }
}
