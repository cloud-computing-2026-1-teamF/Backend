delete from vacancy_category_score_explanations
where source = 'mock_score_explanation';

with ranked_scores as (
  select
    property_id,
    category_id,
    생존점수,
    row_number() over (
      partition by category_id
      order by 생존점수 desc nulls last, property_id
    ) as score_rank
  from vacancy_category_scores
  where 생존점수 is not null
),
mock_base as (
  select
    ranked_scores.property_id,
    ranked_scores.category_id,
    ranked_scores.생존점수 as survival_score,
    vacancies.월세_만원 as monthly_rent,
    vacancies.권리금_만원 as premium,
    vacancy_common_features.저녁_비율 as evening_ratio,
    vacancy_common_features.주말_비율 as weekend_ratio,
    vacancy_common_features.가게당_평균매출 as average_sales_per_store,
    vacancy_category_spatial.업종성장률_500m as industry_growth_500m,
    vacancy_category_spatial.동종_식당수_500m as same_category_count_500m
  from ranked_scores
  join vacancies
    on vacancies.property_id = ranked_scores.property_id
  left join vacancy_common_features
    on vacancy_common_features.property_id = ranked_scores.property_id
  left join vacancy_category_spatial
    on vacancy_category_spatial.property_id = ranked_scores.property_id
   and vacancy_category_spatial.category_id = ranked_scores.category_id
  where ranked_scores.score_rank <= 80
)
insert into vacancy_category_score_explanations (
  property_id,
  category_id,
  contribution_direction,
  contribution_rank,
  feature_key,
  feature_label,
  feature_display_value,
  impact_value,
  impact_percent,
  source
)
select
  mock_base.property_id,
  mock_base.category_id,
  feature.contribution_direction,
  feature.contribution_rank,
  feature.feature_key,
  feature.feature_label,
  feature.feature_display_value,
  feature.impact_value,
  feature.impact_percent,
  'mock_score_explanation'
from mock_base
cross join lateral (
  values
    (
      'positive',
      1,
      'evening_foot_traffic',
      '저녁 유동인구',
      coalesce(to_char(round(mock_base.evening_ratio * 100, 1), 'FM999990.0') || '%', '데이터 준비 중'),
      round(0.102 + mock_base.survival_score * 0.068 + coalesce(mock_base.evening_ratio, 0) * 0.035, 6),
      34.00
    ),
    (
      'positive',
      2,
      'industry_growth_500m',
      '업종 성장률',
      coalesce(to_char(round(mock_base.industry_growth_500m, 1), 'FM999990.0') || '%', '데이터 준비 중'),
      round(0.074 + mock_base.survival_score * 0.046 + coalesce(mock_base.industry_growth_500m, 0) / 180, 6),
      25.00
    ),
    (
      'positive',
      3,
      'sales_per_store',
      '점포당 평균매출',
      coalesce(to_char(round(mock_base.average_sales_per_store / 10000, 0), 'FM999,999,990') || '만원', '데이터 준비 중'),
      round(0.052 + mock_base.survival_score * 0.033 + least(coalesce(mock_base.average_sales_per_store, 0) / 100000000, 0.055), 6),
      18.00
    ),
    (
      'negative',
      1,
      'monthly_rent',
      '월세',
      coalesce(to_char(mock_base.monthly_rent::numeric, 'FM999,999,990') || '만원', '데이터 준비 중'),
      -round(0.061 + least(coalesce(mock_base.monthly_rent, 0)::numeric / 20000, 0.085), 6),
      29.00
    ),
    (
      'negative',
      2,
      'same_category_competition_500m',
      '동종 경쟁점포',
      coalesce(to_char(mock_base.same_category_count_500m::numeric, 'FM999,999,990') || '곳', '데이터 준비 중'),
      -round(0.047 + least(coalesce(mock_base.same_category_count_500m, 0)::numeric / 850, 0.070), 6),
      22.00
    ),
    (
      'negative',
      3,
      'premium',
      '권리금',
      coalesce(to_char(mock_base.premium::numeric, 'FM999,999,990') || '만원', '데이터 준비 중'),
      -round(0.033 + least(coalesce(mock_base.premium, 0)::numeric / 150000, 0.055), 6),
      13.00
    )
) as feature(
  contribution_direction,
  contribution_rank,
  feature_key,
  feature_label,
  feature_display_value,
  impact_value,
  impact_percent
)
on conflict (
  property_id,
  category_id,
  contribution_direction,
  contribution_rank
) do update set
  feature_key = excluded.feature_key,
  feature_label = excluded.feature_label,
  feature_display_value = excluded.feature_display_value,
  impact_value = excluded.impact_value,
  impact_percent = excluded.impact_percent,
  source = excluded.source,
  created_at = now();
