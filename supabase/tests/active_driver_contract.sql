-- Read-only catalog gate. No application rows, credentials or schema changes.
BEGIN READ ONLY;
DO $$
DECLARE signature text; proc oid; definition text;
BEGIN
 FOREACH signature IN ARRAY ARRAY[
  'public.mobile_workspace()', 'public.mobile_shipment_details(uuid)',
  'public.mobile_create_request(uuid,jsonb)', 'public.mobile_save_profile(text,text,text,text)',
  'public.mobile_driver_action(uuid,text)', 'public.driver_milestone(uuid,text,jsonb)',
  'public.driver_arrive_at_pickup(uuid,numeric,numeric)',
  'public.driver_record_position(uuid,uuid,jsonb)',
  'public.driver_report_incident_evidence(uuid,uuid,public.incident_category,text,integer,text[])',
  'public.driver_submit_pod(uuid,text,text,text)',
  'public.mobile_driver_history(text,integer,integer)',
  'public.mobile_driver_fuel_history(uuid)', 'public.mobile_driver_evidence(uuid)',
  'public.mobile_driver_log_fuel(uuid,uuid,numeric,numeric,numeric,text)',
  'public.mobile_delivery_evidence_file(uuid,text,uuid)',
  'public.delivery_conversation_unread()',
  'public.delivery_conversation(uuid,bigint)', 'public.send_delivery_message(uuid,uuid,text,uuid)',
  'public.read_delivery_messages(uuid,bigint[])'
 ] LOOP
  proc:=to_regprocedure(signature);
  IF proc IS NULL THEN RAISE EXCEPTION 'DRIVER_CONTRACT_MISSING: %',signature;END IF;
  IF NOT has_function_privilege('authenticated',proc,'EXECUTE') THEN
   RAISE EXCEPTION 'DRIVER_CONTRACT_EXECUTE_MISSING: %',signature;
  END IF;
 END LOOP;
 SELECT pg_get_functiondef('public.mobile_driver_action(uuid,text)'::regprocedure) INTO definition;
 IF definition !~ 'driver_milestone\(p_delivery,\s*p_action\)' THEN
  RAISE EXCEPTION 'DRIVER_CONTRACT_OBSOLETE: install canonical shared_delivery_workflow, not historical mobile wrapper';
 END IF;
 IF (SELECT proargnames FROM pg_proc WHERE oid='public.mobile_driver_action(uuid,text)'::regprocedure)
    IS DISTINCT FROM ARRAY['p_delivery','p_action']::text[] THEN RAISE EXCEPTION 'DRIVER_CONTRACT_ARGUMENT_NAMES';END IF;
 IF (SELECT proargnames FROM pg_proc WHERE oid='public.driver_arrive_at_pickup(uuid,numeric,numeric)'::regprocedure)
    IS DISTINCT FROM ARRAY['p_delivery_id','p_lat','p_lng']::text[] THEN RAISE EXCEPTION 'DRIVER_CONTRACT_ARRIVAL_ARGUMENT_NAMES';END IF;
 IF pg_get_function_result('public.mobile_driver_action(uuid,text)'::regprocedure)<>'void'
 OR pg_get_function_result('public.mobile_workspace()'::regprocedure)<>'jsonb'
 OR pg_get_function_result('public.mobile_shipment_details(uuid)'::regprocedure)<>'jsonb' THEN
  RAISE EXCEPTION 'DRIVER_CONTRACT_RETURN_SHAPE';END IF;
 SELECT pg_get_functiondef('public.driver_milestone(uuid,text,jsonb)'::regprocedure) INTO definition;
 IF position('showed' in definition)=0 OR position('no_show' in definition)=0 OR position('WAIT_PERIOD_NOT_FINISHED' in definition)=0 THEN
  RAISE EXCEPTION 'DRIVER_CONTRACT_MILESTONES_MISSING';END IF;
 IF NOT EXISTS(SELECT 1 FROM storage.buckets WHERE id='delivery-documents' AND NOT public) THEN
  RAISE EXCEPTION 'DRIVER_CONTRACT_PRIVATE_BUCKET_MISSING';END IF;
 FOREACH signature IN ARRAY ARRAY['deliveries','tracking_logs','delivery_expenses','delivery_incidents','incident_attachments'] LOOP
  IF NOT EXISTS(SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
   WHERE n.nspname='public' AND c.relname=signature AND c.relrowsecurity) THEN
   RAISE EXCEPTION 'DRIVER_CONTRACT_RLS_MISSING: %',signature;END IF;
 END LOOP;
 IF NOT EXISTS(SELECT 1 FROM pg_policies WHERE schemaname='public' AND tablename='delivery_expenses' AND cmd='INSERT')
 OR NOT EXISTS(SELECT 1 FROM pg_policies WHERE schemaname='storage' AND tablename='objects' AND cmd='SELECT' AND policyname='authorized recorded incident photo or receipt') THEN
  RAISE EXCEPTION 'DRIVER_CONTRACT_EVIDENCE_POLICY_MISSING';END IF;
END $$;
ROLLBACK;
SELECT 'PASS: active driver contract, canonical dispatcher, private evidence and RLS prerequisites' result;
