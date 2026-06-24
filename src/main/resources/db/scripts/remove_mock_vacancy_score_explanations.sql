-- Remove temporary XGBoost top-feature seed rows before loading production
-- vacancy score explanations from the data team. The stored feature
-- benchmarks are calculated reference data and should remain in place.
begin;

delete from vacancy_category_score_explanations
where source = 'mock_score_top_features';

commit;
