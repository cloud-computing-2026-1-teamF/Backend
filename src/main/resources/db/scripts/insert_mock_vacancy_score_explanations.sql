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
    ranked_scores.score_rank
  from ranked_scores
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
cross join lateral (
  values
    (1, case when mock_base.score_rank % 3 = 0 then 'monthly_rent' else 'daily_floating_population' end),
    (2, case when mock_base.score_rank % 4 = 0 then 'premium' else 'exclusive_area' end),
    (3, case when mock_base.score_rank % 5 = 0 then 'sales_per_store' else 'industry_growth_500m' end),
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
