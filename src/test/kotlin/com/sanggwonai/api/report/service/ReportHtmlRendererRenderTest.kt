package com.sanggwonai.api.report.service

import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 렌더러 시각 검증용 — 서교동 일식 샘플(output_schema + ReportContextAssembler input 구조)로
 * 실제 HTML을 생성한다. 레이아웃과 새 리포트 섹션 마크업이 런타임에 정상인지 확인.
 * 실행: ./gradlew test --tests "*ReportHtmlRendererRenderTest*"
 */
class ReportHtmlRendererRenderTest {

    private val mapper = JsonMapper.builder().build()
    private val tr = object : TypeReference<Map<String, Any?>>() {}

    @Test
    fun `샘플 보고서 HTML 렌더`() {
        val report = mapper.readValue(REPORT_JSON, tr)
        val input = mapper.readValue(INPUT_JSON, tr)

        val bytes = ReportHtmlRenderer().render(report, input)
        val html = bytes.toString(Charsets.UTF_8)

        assertTrue(bytes.size > 10_000, "HTML이 너무 작음: ${bytes.size}")
        assertTrue(html.startsWith("<!DOCTYPE html>"), "HTML 헤더 아님")
        assertTrue(html.contains("공실 운영 조건"), "공실 운영 조건 섹션이 렌더되지 않음")
        assertTrue(html.contains("상권 수요·경쟁 시그널"), "상권 수요·경쟁 시그널 섹션이 렌더되지 않음")
        assertTrue(html.contains("주변 리뷰 인사이트"), "리뷰 인사이트 섹션이 렌더되지 않음")

        val out = File("build/report_sample.html")
        out.parentFile.mkdirs()
        out.writeBytes(bytes)
        println("[RENDER] wrote ${out.absolutePath} (${bytes.size} bytes)")
    }

    companion object {
        private val INPUT_JSON = """
        {
          "saved_analysis": {
            "id": "an_demo", "date": "2026.06.01", "region": "서교동", "radius": 500,
            "category": "일식", "categoryEmoji": "🍣", "topScore": 64,
            "top3": [
              {"rank":1,"vacancyId":"v1","addr":"서울 마포구 동교로 194","recommended":false,"score":64,
               "rent":1400,"deposit":10000,"mgmt":50,"floor":"1층","foot":126607,"comp":134,"rev":1467,"growth":-1.2,
               "footHourly":[12,9,6,4,3,4,9,22,33,30,28,40,58,46,38,44,55,62,72,80,76,52,30,16],
               "nearby":{"subway":"홍대입구(2호선)·합정(2,6호선)","bus":"성수동차고지(2014, 2224, 2412, 2413); 성수동진주타운(2014); 뚝도시장구길(2014, 2224); 서울숲지구대앞(2014, 2224, 2412); 성수두산위브아파트(2014); 성수1가새마을금고(2014, 2224)","parking":"인근 공영주차장"}},
              {"rank":2,"vacancyId":"v2","addr":"서울 마포구 동교로 195","recommended":true,"score":64,
               "rent":190,"deposit":2000,"mgmt":20,"floor":"지하1층","foot":126607,"comp":134,"rev":1467,
               "footHourly":[12,9,6,4,3,4,9,22,33,30,28,40,58,46,38,44,55,62,72,80,76,52,30,16],
               "nearby":{"subway":"홍대입구(2호선)","bus":"인근 정류장","parking":"없음"}},
              {"rank":3,"vacancyId":"v3","addr":"서울 마포구 동교로25길 34","recommended":false,"score":64,
               "rent":550,"deposit":5000,"mgmt":30,"floor":"2층","foot":110000,"comp":120,"rev":1467,
               "footHourly":[10,8,6,4,3,4,8,20,30,28,26,38,55,44,36,42,52,60,70,78,74,50,28,14],
               "nearby":{"subway":"홍대입구(2호선)","bus":"인근 정류장","parking":"인근 공영"}}
            ]
          },
          "vacancy_metric_reference": {
            "peerCount": 40,
            "footTrafficDaily": {"selected":126607,"average":95000,"median":92000,"min":40000,"max":180000,"percentile":70},
            "competition500m": {"selected":134,"average":34,"median":30,"min":5,"max":150,"percentile":95},
            "averageSalesMonthly": {"selected":1467,"average":1180,"median":1180,"min":620,"max":1950,"percentile":70}
          },
          "selected_vacancy_extra": {
            "vacancy_id":"v1","selected_category":"일식","selected_score_percent":64,
            "nine_category_scores": {"한식":57,"중식":48,"일식":64,"서양식":61,"기타":45,"구내식당및뷔페":39,"패스트푸드":52,"주점업":66,"카페디저트":71},
            "ranking_in_9_categories":3,
            "neighborhood_signals": {"동네_폐업률":0.13,"동네_개업율":0.2,"유동인구_2030_비율":0.34,"동네_저녁매출_비율":0.41}
          },
          "section_06_investment_payback": {
            "vacancy_id":"v1","matched_category":"일식",
            "초기투자비_만원":26000,"점포당평균매출_만원":1467,"월순이익_만원":-652,"투자회수기간_개월":null,
            "투자회수평가":"적자","기존시설_활용가능":false,"기존시설_문구":null,"매출매칭기준":"상권",
            "bep_action": {"객단가원":12000,"피크_손익분기_잔수":38,"피크_목표매출_잔수":30},
            "properties": [
              {"rank":1,"vacancy_id":"v1","주소_간략":"동교로 194","초기투자비_만원":26000,"월순이익_만원":-652,"투자회수기간_개월":null,"투자회수평가":"적자"},
              {"rank":2,"vacancy_id":"v2","주소_간략":"동교로 195","초기투자비_만원":6000,"월순이익_만원":558,"투자회수기간_개월":11,"투자회수평가":"3년 이상"},
              {"rank":3,"vacancy_id":"v3","주소_간략":"동교로25길","초기투자비_만원":12000,"월순이익_만원":210,"투자회수기간_개월":57,"투자회수평가":"3년 이상"}
            ]
          },
          "section_05_review_insight": {
            "collected": true,
            "scope": {"radius_m":50,"store_count":8,"tagged_store_count":6},
            "properties": [
              {"rank":1,"vacancy_id":"v1","주소_간략":"서울 마포구 동교로 194","demand_tags":[
                {"tag":"재료가 신선해요","score":18.4},{"tag":"혼밥하기 좋아요","score":14.2},
                {"tag":"가성비가 좋아요","score":11.0},{"tag":"양이 많아요","score":8.3},{"tag":"친절해요","score":6.1}]},
              {"rank":2,"vacancy_id":"v2","주소_간략":"서울 마포구 동교로 195","demand_tags":[
                {"tag":"분위기가 좋아요","score":16.7},{"tag":"데이트하기 좋아요","score":12.9},
                {"tag":"술이 다양해요","score":9.4},{"tag":"친절해요","score":7.0},{"tag":"매장이 청결해요","score":5.2}]},
              {"rank":3,"vacancy_id":"v3","주소_간략":"서울 마포구 동교로25길 34","demand_tags":[
                {"tag":"음식이 맛있어요","score":15.1},{"tag":"재료가 신선해요","score":10.8},
                {"tag":"웨이팅 짧아요","score":7.7},{"tag":"좌석이 편해요","score":5.5},{"tag":"양이 많아요","score":4.0}]}
            ]
          }
        }
        """.trimIndent()

        private val REPORT_JSON = """
        {
          "report_metadata": {
            "보고서_제목": {"값":"[서교동] 일식 입지 분석 보고서"},
            "보고서_부제": {"값":"유동·매출은 강하나, 투자수익성은 점검이 필요합니다"},
            "보고서_등급": {"값":"B"},
            "추천_도장": {"값":"보류 권장"}
          },
          "chapter_1_executive_summary": {
            "한_줄_결론": {"값":"서교동은 수요가 있으나, 임대조건 대비 수익성 점검이 핵심입니다."},
            "요약_본문": {"값":"유동인구가 두텁고(동일 업종 상위 30%) 매출 기반도 형성돼 있습니다. 다만 동일 업종 경쟁이 평균 대비 약 4배로 과밀하고, 1순위 매물은 월 순이익이 적자로 추정되어 임대조건·객단가·피크 운영 설계의 점검이 권장됩니다. 단순 일식집보다 신선도·혼밥 수요를 흡수하는 대표 메뉴 중심의 1인 일식 콘셉트가 차별화 방향입니다."},
            "리스크_요인": [
              {"리스크":"동일 업종 경쟁 과밀","심각도":"높음","발생_확률":"높음","대응_방안":"500m 내 134곳(평균 34). 메뉴·가격·회전 차별화로 대체 가능성을 낮춰야 합니다."},
              {"리스크":"투자수익성 불리 가능","심각도":"높음","발생_확률":"중간","대응_방안":"1순위 매물 월순이익 적자. 월세·층 조건별로 회수기간이 상이합니다."}
            ],
            "액션_아이템": [
              {"우선순위":1,"할_일":"피크 시간대 운영 가능성 현장 확인","이유":"유동은 강하나 경쟁도 높아 피크가 매출 성패와 직결.","예상_소요시간":"~1주"},
              {"우선순위":2,"할_일":"월세·관리비 조건별 회수 재계산","이유":"1순위 적자, 2순위 3년 이상 등 상이.","예상_소요시간":"~1주"},
              {"우선순위":3,"할_일":"객단가 기준 메뉴/프로모션 설계","이유":"손익분기·피크 목표를 실행 수치로.","예상_소요시간":"~2주"}
            ]
          },
          "chapter_3_top3_property_analysis": {
            "chapter_intro": {"값":"세 매물 모두 생존 점수 64로 기본 수요는 동일합니다. 최종 선택은 월세·층·투자회수 차이로 좁힙니다."},
            "매물_상세_분석": [
              {"rank":1,"한_줄_평가":"1층·유동 노출 높으나 임대료 높음","강점":{"값":"1층으로 유동 노출이 좋아 인지도 확보에 유리합니다."},"약점":{"값":"고정비가 과다해 손익분기가 높습니다."}},
              {"rank":2,"한_줄_평가":"월세 낮아 흑자 여력 큼","강점":{"값":"월세 190만원으로 고정비 부담이 가장 낮아 흑자 전환 여력이 큽니다."},"약점":{"값":"지하층 체감(환기·간판) 확인이 필요합니다."}},
              {"rank":3,"한_줄_평가":"2층·중간 임대료 균형형","강점":{"값":"임대료가 중간 수준이라 균형이 좋습니다."},"약점":{"값":"2층 접근성·간판 노출 점검이 필요합니다."}}
            ],
            "최종_권장_매물": {"rank":2,"권장_근거":{"값":"동교로 195 — 고정비 부담이 가장 낮아 흑자 전환 여력이 큽니다. 단, 지하층 체감(환기·간판) 실사가 필요합니다."}}
          },
          "chapter_4_location_characteristics": {
            "section_4_1_floating_population": {"시간대_패턴_해석": {"값":"점심(12시)·저녁(20~21시) 더블 피크입니다. 저녁 피크가 더 높아 저녁 객단가 설계가 매출의 핵심입니다."}},
            "section_4_2_competition": {"포지셔닝_제안": {"값":"평균 대비 과밀하므로 대표 메뉴와 회전 전략으로 차별화가 필요합니다."}},
            "section_4_3_estimated_revenue": {"매출_환경_해석": {"값":"내 매물 추정 1,467만원으로 동일 업종 상위 30% 수준입니다."}},
            "section_4_4_accessibility": {"접근성_종합_평가": {"값":"2호선 홍대입구역 인근으로 접근성이 우수합니다. 주말·야간 유입도 기대됩니다."}}
          },
          "chapter_5_business_fit_analysis": {
            "선택_카테고리_평가": {"해석": {"값":"이 입지에 일식(64)은 무난하나, 카페·디저트(71)·주점(66)이 더 높게 나옵니다. 일식을 선택한다면 저녁 술·안주를 결합한 콘셉트가 점수 차를 메우는 전략이 됩니다."}},
            "best_3_카테고리": {"값":"카페·디저트(71) > 주점(66) > 일식(64)"}
          },
          "chapter_7_appendix": {
            "본_보고서의_한계": {"값":"가게 자체 특성(맛·서비스·운영 역량)은 분석에 반영되지 않습니다.\n매출·순이익은 동네 평균 추정치로, 개별 매물의 실제 매출과 다를 수 있습니다.\n본 보고서는 참고용이며, 최종 결정 전 현장 실사·전문가 상담을 권장합니다."}
          },
          "chapter_9_investment_payback": {
            "활성_여부": true,
            "회수_해석": {"값":"1순위 매물은 월 순이익이 적자로 추정됩니다. 손익분기 달성을 위해 점심 회전과 저녁 객단가, 포장의 3개 매출원을 합산 설계해야 합니다."},
            "bep_action": {"값":"손익분기를 넘기려면 피크 시간 기준 시간당 약 38그릇이 필요합니다."}
          },
          "chapter_8_review_insight": {
            "활성_여부": true,
            "데이터_범위": "추천 매물별 반경 50m 동일 업종 가게의 방문자 태그 기준",
            "매물별_리뷰": [
              {"순위":1,"코멘트":"동교로 194 주변은 신선도와 혼밥 편의가 가장 강한 수요입니다. 1인 좌석과 당일 입고 재료를 전면에 내세우면 인근 수요와 바로 맞물립니다."},
              {"순위":2,"코멘트":"동교로 195 일대는 분위기·데이트·가벼운 술 수요가 두드러집니다. 저녁 객단가를 높이는 사케·안주 구성과 좌석 동선이 유효합니다."},
              {"순위":3,"코멘트":"동교로25길은 맛과 신선도 만족이 핵심이고 웨이팅 부담이 적습니다. 빠른 회전과 대표 메뉴 품질로 재방문을 노릴 수 있습니다."}
            ],
            "리뷰_종합_해석": "세 매물 모두 신선도가 공통 강점이나, 194는 혼밥, 195는 분위기·술, 25길은 회전이 차별 포인트입니다. 매물별 수요에 맞춘 좌석·메뉴 설계가 승부처입니다."
          }
        }
        """.trimIndent()
    }
}
