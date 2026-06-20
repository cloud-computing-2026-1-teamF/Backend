-- Remove temporary vacancy history seed rows before the data team loads
-- production history. Real data must use a source value other than mock_seed
-- or mock_projection.
begin;

delete from vacancy_occupancy_history
where source in ('mock_seed', 'mock_projection');

delete from vacancy_score_history
where source in ('mock_seed', 'mock_projection');

commit;
