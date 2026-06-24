with feature_values as (
  select
    'monthly_rent' as feature_key,
    '월세' as feature_label,
    vacancies."월세_만원"::numeric as feature_value,
    '만원' as display_unit,
    false as higher_is_positive
  from vacancies
  where vacancies."월세_만원" is not null

  union all
  select 'deposit', '보증금', vacancies."보증금_만원"::numeric, '만원', false
  from vacancies
  where vacancies."보증금_만원" is not null

  union all
  select 'maintenance_fee', '관리비', vacancies."관리비_만원"::numeric, '만원', false
  from vacancies
  where vacancies."관리비_만원" is not null

  union all
  select 'premium', '권리금', vacancies."권리금_만원"::numeric, '만원', false
  from vacancies
  where vacancies."권리금_만원" is not null

  union all
  select 'sale_price', '매매가', vacancies."매매가_만원"::numeric, '만원', false
  from vacancies
  where vacancies."매매가_만원" is not null

  union all
  select 'exclusive_area', '전용면적', vacancies."전용면적_제곱미터"::numeric, 'm2', true
  from vacancies
  where vacancies."전용면적_제곱미터" is not null

  union all
  select 'supply_area', '공급면적', vacancies."공급면적_제곱미터"::numeric, 'm2', true
  from vacancies
  where vacancies."공급면적_제곱미터" is not null

  union all
  select 'facility_total_size', '시설총규모', vacancy_common_features."시설총규모"::numeric, 'm2', true
  from vacancy_common_features
  where vacancy_common_features."시설총규모" is not null

  union all
  select 'location_area', '소재지면적', vacancy_common_features."소재지면적"::numeric, 'm2', true
  from vacancy_common_features
  where vacancy_common_features."소재지면적" is not null

  union all
  select 'daily_floating_population', '일평균 유동인구', (vacancy_common_features."최종_유동인구_밀도_명_per_km2_2022_연간합계" / 365.0)::numeric, '명/일', true
  from vacancy_common_features
  where vacancy_common_features."최종_유동인구_밀도_명_per_km2_2022_연간합계" is not null

  union all
  select 'evening_foot_traffic', '저녁 유동인구', (vacancy_common_features."저녁_비율" * 100.0)::numeric, '%', true
  from vacancy_common_features
  where vacancy_common_features."저녁_비율" is not null

  union all
  select 'weekend_population_ratio', '주말 유동인구', (vacancy_common_features."주말_비율" * 100.0)::numeric, '%', true
  from vacancy_common_features
  where vacancy_common_features."주말_비율" is not null

  union all
  select 'age2030_population_ratio', '2030 유동인구', (vacancy_common_features."연령_2030_비율" * 100.0)::numeric, '%', true
  from vacancy_common_features
  where vacancy_common_features."연령_2030_비율" is not null

  union all
  select 'sales_per_store', '점포당 평균매출', (vacancy_common_features."가게당_평균매출" / 10000.0)::numeric, '만원', true
  from vacancy_common_features
  where vacancy_common_features."가게당_평균매출" is not null

  union all
  select 'closure_rate', '폐업률', vacancy_common_features."동네_폐업률"::numeric, '%', false
  from vacancy_common_features
  where vacancy_common_features."동네_폐업률" is not null

  union all
  select 'opening_rate', '개업률', vacancy_common_features."동네_개업율"::numeric, '%', true
  from vacancy_common_features
  where vacancy_common_features."동네_개업율" is not null

  union all
  select 'restaurant_count_500m', '식당 수', vacancy_common_features."식당수_500m"::numeric, '곳', false
  from vacancy_common_features
  where vacancy_common_features."식당수_500m" is not null

  union all
  select 'cafe_count_500m', '카페 수', vacancy_common_features."카페수_500m"::numeric, '곳', false
  from vacancy_common_features
  where vacancy_common_features."카페수_500m" is not null

  union all
  select 'same_category_competition_500m', '동종 경쟁점포', vacancy_category_spatial."동종_식당수_500m"::numeric, '곳', false
  from vacancy_category_spatial
  where vacancy_category_spatial."동종_식당수_500m" is not null

  union all
  select 'industry_growth_500m', '업종 성장률', (vacancy_category_spatial."업종성장률_500m" * 100.0)::numeric, '%', true
  from vacancy_category_spatial
  where vacancy_category_spatial."업종성장률_500m" is not null
),
feature_stats as (
  select
    feature_key,
    max(feature_label) as feature_label,
    round(avg(feature_value), 6) as average_value,
    max(display_unit) as display_unit,
    bool_or(higher_is_positive) as higher_is_positive
  from feature_values
  group by feature_key
)
insert into vacancy_score_feature_benchmarks (
  feature_key,
  feature_label,
  average_value,
  display_unit,
  higher_is_positive,
  source,
  calculated_at
)
select
  feature_key,
  feature_label,
  average_value,
  display_unit,
  higher_is_positive,
  'dataset_average',
  now()
from feature_stats
on conflict (feature_key) do update set
  feature_label = excluded.feature_label,
  average_value = excluded.average_value,
  display_unit = excluded.display_unit,
  higher_is_positive = excluded.higher_is_positive,
  source = excluded.source,
  calculated_at = excluded.calculated_at;
