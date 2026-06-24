package com.sanggwonai.api.vacancy.dto

import com.sanggwonai.api.vacancy.entity.VacancyCategoryScoreExplanationEntity

fun toScoreExplanationDto(
    entities: List<VacancyCategoryScoreExplanationEntity>
): VacancyScoreExplanationDto? {
    if (entities.isEmpty()) return null

    val positive = entities.toContributions("positive")
    val negative = entities.toContributions("negative")
    if (positive.isEmpty() && negative.isEmpty()) return null

    val source = entities
        .map { it.source }
        .distinct()
        .let { sources -> if (sources.size == 1) sources.first() else "mixed" }

    return VacancyScoreExplanationDto(
        positive = positive,
        negative = negative,
        source = source
    )
}

private fun List<VacancyCategoryScoreExplanationEntity>.toContributions(
    direction: String
): List<VacancyScoreFeatureContributionDto> {
    return asSequence()
        .filter { it.id.contributionDirection == direction }
        .sortedBy { it.id.contributionRank }
        .map { entity ->
            VacancyScoreFeatureContributionDto(
                direction = entity.id.contributionDirection,
                rank = entity.id.contributionRank.toInt(),
                featureKey = entity.featureKey,
                featureLabel = entity.featureLabel,
                featureDisplayValue = entity.featureDisplayValue,
                impactValue = entity.impactValue,
                impactPercent = entity.impactPercent
            )
        }
        .toList()
}
