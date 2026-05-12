package com.sanggwonai.api.shortlist.controller

import com.sanggwonai.api.common.api.ApiResponse
import com.sanggwonai.api.shortlist.controller.response.UserShortlistListResponse
import com.sanggwonai.api.shortlist.controller.response.UserShortlistResponse
import com.sanggwonai.api.shortlist.dto.UserShortlistData
import com.sanggwonai.api.shortlist.facade.UserShortlistFacade
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/users/me/shortlist")
@Tag(name = "찜 목록", description = "로그인 사용자의 공실 찜 목록 관리 API")
@SecurityRequirement(name = "bearerAuth")
class UserShortlistController(
    private val shortlistFacade: UserShortlistFacade
) {
    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(summary = "찜 목록 조회", description = "현재 로그인 사용자가 찜한 공실 ID 목록을 최신순으로 반환함.")
    fun list(
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) authorization: String?
    ): ResponseEntity<ApiResponse<UserShortlistListResponse>> {
        val items = shortlistFacade.list(authorization).map(::toResponse)
        return ResponseEntity.ok(ApiResponse(UserShortlistListResponse(items = items)))
    }

    @PostMapping("/{vacancyId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(summary = "찜 추가", description = "지정한 공실을 사용자의 찜 목록에 추가함. 이미 있으면 기존 row 를 그대로 반환.")
    fun add(
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @PathVariable("vacancyId") vacancyId: String
    ): ResponseEntity<ApiResponse<UserShortlistResponse>> {
        val data = shortlistFacade.add(authorization, vacancyId)
        return ResponseEntity.ok(ApiResponse(toResponse(data)))
    }

    @DeleteMapping("/{vacancyId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(summary = "찜 제거", description = "지정한 공실을 사용자의 찜 목록에서 제거함. 멱등.")
    fun remove(
        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @PathVariable("vacancyId") vacancyId: String
    ): ResponseEntity<ApiResponse<RemoveShortlistResponse>> {
        shortlistFacade.remove(authorization, vacancyId)
        return ResponseEntity.ok(ApiResponse(RemoveShortlistResponse(ok = true)))
    }

    private fun toResponse(data: UserShortlistData): UserShortlistResponse =
        UserShortlistResponse(vacancyId = data.vacancyId, createdAt = data.createdAt)
}

data class RemoveShortlistResponse(val ok: Boolean)
