package com.sanggwonai.api.vacancy.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.stereotype.Component
import kotlin.system.measureTimeMillis

@Component
class VacancyDatasetWarmup(
    private val vacancyDataset: VacancyDataset
) : SmartInitializingSingleton {
    private val log = LoggerFactory.getLogger(VacancyDatasetWarmup::class.java)

    override fun afterSingletonsInstantiated() {
        var snapshot: VacancyDatasetSnapshot? = null
        val elapsedMs = measureTimeMillis {
            snapshot = vacancyDataset.snapshot()
        }
        val loaded = snapshot ?: return
        log.info(
            "Prewarmed vacancy dataset: vacancies={}, commonFeatures={}, accessibility={}, scores={}, spatials={}, categories={}, elapsedMs={}",
            loaded.vacancies.size,
            loaded.commonByProperty.size,
            loaded.accessibilityByProperty.size,
            loaded.scoreByKey.size,
            loaded.spatialByKey.size,
            loaded.categoryNameById.size,
            elapsedMs
        )
    }
}
