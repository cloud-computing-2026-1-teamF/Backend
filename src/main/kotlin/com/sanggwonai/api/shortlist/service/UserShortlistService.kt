package com.sanggwonai.api.shortlist.service

import com.sanggwonai.api.auth.service.AuthContext
import com.sanggwonai.api.common.error.ApiException
import com.sanggwonai.api.common.error.ErrorType
import com.sanggwonai.api.common.util.IdGenerator
import com.sanggwonai.api.shortlist.dto.UserShortlistData
import com.sanggwonai.api.shortlist.entity.UserShortlistEntity
import com.sanggwonai.api.shortlist.repository.UserShortlistRepository
import com.sanggwonai.api.vacancy.repository.VacancyRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

@Service
class UserShortlistService(
    private val shortlistRepository: UserShortlistRepository,
    private val vacancyRepository: VacancyRepository,
    private val clock: Clock
) {
    @Transactional(readOnly = true)
    fun list(authContext: AuthContext): List<UserShortlistData> {
        return shortlistRepository.findByUserIdOrderByCreatedAtDesc(authContext.userId)
            .map { UserShortlistData(vacancyId = it.vacancyId, createdAt = it.createdAt) }
    }

    @Transactional
    fun add(authContext: AuthContext, vacancyId: String): UserShortlistData {
        if (!vacancyRepository.existsById(vacancyId)) {
            throw ApiException.of(ErrorType.VACANCY_NOT_FOUND, mapOf("vacancy_id" to vacancyId))
        }
        // Idempotent: re-adding an already-shortlisted vacancy returns the
        // existing row instead of failing with a uniqueness violation.
        val existing = shortlistRepository.findByUserIdAndVacancyId(authContext.userId, vacancyId)
        if (existing != null) {
            return UserShortlistData(vacancyId = existing.vacancyId, createdAt = existing.createdAt)
        }
        val saved = shortlistRepository.save(
            UserShortlistEntity(
                id = IdGenerator.next("sl"),
                userId = authContext.userId,
                vacancyId = vacancyId,
                createdAt = Instant.now(clock)
            )
        )
        return UserShortlistData(vacancyId = saved.vacancyId, createdAt = saved.createdAt)
    }

    @Transactional
    fun remove(authContext: AuthContext, vacancyId: String) {
        // Idempotent: removing a vacancy that isn't shortlisted is a no-op so
        // the UI can fire-and-forget when the user toggles repeatedly.
        shortlistRepository.deleteByUserIdAndVacancyId(authContext.userId, vacancyId)
    }
}
