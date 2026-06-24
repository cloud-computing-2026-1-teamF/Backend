delete from vacancy_category_score_explanations
where source = 'mock_score_top_features';

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
    ranked_scores.score_rank,
    vacancies."월세_만원",
    vacancies."권리금_만원",
    vacancies."관리비_만원",
    vacancies."전용면적_제곱미터",
    common_features."최종_유동인구_밀도_명_per_km2_2022_연간합계",
    common_features."저녁_비율",
    common_features."주말_비율",
    common_features."동네_폐업률",
    common_features."가게당_평균매출",
    spatial_features."동종_식당수_500m",
    spatial_features."업종성장률_500m"
  from ranked_scores
  join vacancies
    on vacancies.property_id = ranked_scores.property_id
  left join vacancy_common_features common_features
    on common_features.property_id = ranked_scores.property_id
  left join vacancy_category_spatial spatial_features
    on spatial_features.property_id = ranked_scores.property_id
   and spatial_features.category_id = ranked_scores.category_id
  where ranked_scores.score_rank <= 80
)
insert into vacancy_category_score_explanations (
  property_id,
  category_id,
  feature_rank,
  feature_key,
  source
)
select
  mock_base.property_id,
  mock_base.category_id,
  feature.feature_rank,
  feature.feature_key,
  'mock_score_top_features'
from mock_base
left join lateral (
  select candidate.feature_key
  from (
    values
      ('daily_floating_population', mock_base."최종_유동인구_밀도_명_per_km2_2022_연간합계" / 365),
      ('exclusive_area', mock_base."전용면적_제곱미터"),
      ('sales_per_store', mock_base."가게당_평균매출" / 10000),
      ('industry_growth_500m', mock_base."업종성장률_500m" * 100),
      ('evening_foot_traffic', mock_base."저녁_비율" * 100),
      ('weekend_population_ratio', mock_base."주말_비율" * 100),
      ('monthly_rent', mock_base."월세_만원"),
      ('premium', mock_base."권리금_만원"),
      ('maintenance_fee', mock_base."관리비_만원"),
      ('closure_rate', mock_base."동네_폐업률"),
      ('same_category_competition_500m', mock_base."동종_식당수_500m")
  ) as candidate(feature_key, current_value)
  join vacancy_score_feature_benchmarks benchmarks
    on benchmarks.feature_key = candidate.feature_key
  where candidate.current_value is not null
    and (
      (benchmarks.higher_is_positive and candidate.current_value > benchmarks.average_value)
      or (not benchmarks.higher_is_positive and candidate.current_value < benchmarks.average_value)
    )
  order by abs(candidate.current_value - benchmarks.average_value) / greatest(abs(benchmarks.average_value), 1) desc
  limit 1
) as favorable_feature on true
left join lateral (
  select candidate.feature_key
  from (
    values
      ('monthly_rent', mock_base."월세_만원"),
      ('premium', mock_base."권리금_만원"),
      ('maintenance_fee', mock_base."관리비_만원"),
      ('closure_rate', mock_base."동네_폐업률"),
      ('same_category_competition_500m', mock_base."동종_식당수_500m"),
      ('daily_floating_population', mock_base."최종_유동인구_밀도_명_per_km2_2022_연간합계" / 365),
      ('exclusive_area', mock_base."전용면적_제곱미터"),
      ('sales_per_store', mock_base."가게당_평균매출" / 10000),
      ('industry_growth_500m', mock_base."업종성장률_500m" * 100),
      ('evening_foot_traffic', mock_base."저녁_비율" * 100),
      ('weekend_population_ratio', mock_base."주말_비율" * 100)
  ) as candidate(feature_key, current_value)
  join vacancy_score_feature_benchmarks benchmarks
    on benchmarks.feature_key = candidate.feature_key
  where candidate.current_value is not null
    and (
      (benchmarks.higher_is_positive and candidate.current_value < benchmarks.average_value)
      or (not benchmarks.higher_is_positive and candidate.current_value > benchmarks.average_value)
    )
  order by abs(candidate.current_value - benchmarks.average_value) / greatest(abs(benchmarks.average_value), 1) desc
  limit 1
) as caution_feature on true
cross join lateral (
  values
    (1, coalesce(favorable_feature.feature_key, case when mock_base.score_rank % 3 = 0 then 'monthly_rent' else 'daily_floating_population' end)),
    (2, coalesce(caution_feature.feature_key, case when mock_base.score_rank % 4 = 0 then 'premium' else 'exclusive_area' end)),
    (3, case
      when favorable_feature.feature_key is distinct from 'industry_growth_500m'
        and caution_feature.feature_key is distinct from 'industry_growth_500m'
        then 'industry_growth_500m'
      when favorable_feature.feature_key is distinct from 'sales_per_store'
        and caution_feature.feature_key is distinct from 'sales_per_store'
        then 'sales_per_store'
      else 'daily_floating_population'
    end),
    (4, case when mock_base.score_rank % 2 = 0 then 'same_category_competition_500m' else 'evening_foot_traffic' end),
    (5, case when mock_base.score_rank % 7 = 0 then 'maintenance_fee' else 'weekend_population_ratio' end)
) as feature(feature_rank, feature_key)
where exists (
  select 1
  from vacancy_score_feature_benchmarks benchmarks
  where benchmarks.feature_key = feature.feature_key
)
on conflict (
  property_id,
  category_id,
  feature_rank
) do update set
  feature_key = excluded.feature_key,
  source = excluded.source,
  created_at = now();
