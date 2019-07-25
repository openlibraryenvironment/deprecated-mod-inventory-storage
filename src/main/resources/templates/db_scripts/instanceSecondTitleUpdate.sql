begin;

update ${myuniversity}_${mymodule}.instance
  set jsonb = jsonb_set(jsonb, '{"secondTitle"}', concat('"', jsonb->> 'title', ' Some Additional Data"')::jsonb );

commit;
