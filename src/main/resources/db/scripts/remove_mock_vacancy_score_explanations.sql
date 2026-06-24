-- Remove temporary XGBoost feature-attribution seed rows before loading
-- production vacancy score explanations from the data team.
begin;

delete from vacancy_category_score_explanations
where source = 'mock_score_explanation';

commit;
