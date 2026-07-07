ALTER TABLE design_templates MODIFY COLUMN canvas_json LONGTEXT;
ALTER TABLE design_projects MODIFY COLUMN canvas_json LONGTEXT;
ALTER TABLE design_project_versions MODIFY COLUMN canvas_json LONGTEXT;

ALTER TABLE print_orders ADD COLUMN size_name VARCHAR(60);
ALTER TABLE print_orders ADD COLUMN paper_type VARCHAR(80);
ALTER TABLE print_orders ADD COLUMN craft_type VARCHAR(80);
