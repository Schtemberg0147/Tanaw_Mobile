-- All test identities, requests, audits and notifications roll back. No real login.
BEGIN;
DO $$ DECLARE a uuid:=gen_random_uuid(); b uuid:=gen_random_uuid(); BEGIN
  PERFORM set_config('test.mobile_a',a::text,true);
  PERFORM set_config('test.mobile_b',b::text,true);
  INSERT INTO auth.users(id,email,raw_app_meta_data,raw_user_meta_data) VALUES
    (a,'mobile-rollback-'||a||'@example.invalid','{}','{"role":"admin","full_name":"Mobile rollback A","phone":"+639123456789"}'),
    (b,'mobile-rollback-'||b||'@example.invalid','{}','{"full_name":"Mobile rollback B","phone":"+639123456780"}');
  IF (SELECT role FROM public.users WHERE id=a)<>'customer' THEN RAISE EXCEPTION 'Signup metadata escalated role'; END IF;
  IF (SELECT phone FROM public.customers WHERE user_id=a)<>'+639123456789' THEN RAISE EXCEPTION 'Signup lost phone'; END IF;
  PERFORM set_config('request.jwt.claims',jsonb_build_object('sub',a,'role','authenticated')::text,true);
END $$;
SET LOCAL ROLE authenticated;
DO $$ DECLARE data jsonb; payload jsonb; first_result jsonb; second_result jsonb; request uuid:=gen_random_uuid(); vehicle uuid; BEGIN
  data:=public.mobile_workspace();
  IF data#>>'{profile,id}'<>current_setting('test.mobile_a') THEN RAISE EXCEPTION 'Wrong profile'; END IF;
  IF jsonb_array_length(data->'shipments')<>0 THEN RAISE EXCEPTION 'Cross-account shipment leak'; END IF;
  PERFORM public.mobile_save_profile('Updated rollback A','+639123456789','Rollback address','Rollback city');
  IF (SELECT name FROM public.customers WHERE user_id=auth.uid())<>'Updated rollback A' THEN RAISE EXCEPTION 'Customer write lost'; END IF;
  SELECT id INTO vehicle FROM public.vehicle_types WHERE is_active AND default_capacity_kg>1 LIMIT 1;
  IF vehicle IS NULL THEN RAISE EXCEPTION 'Vehicle catalog required for test'; END IF;
  payload:=jsonb_build_object('pickup','Rollback pickup address','dropoff','Rollback destination address','cargo','Rollback test cargo','weight',1,'vehicle_id',vehicle,
    'contact','+639123456789','departure',now()+interval '2 days','notes','Rollback only');
  first_result:=public.mobile_create_request(request,payload);
  second_result:=public.mobile_create_request(request,payload);
  IF first_result<>second_result OR first_result->>'status'<>'pending' THEN RAISE EXCEPTION 'Request replay/state failed'; END IF;
  BEGIN PERFORM public.mobile_create_request(request,payload||'{"cargo":"Changed cargo"}'::jsonb); RAISE EXCEPTION 'Changed replay accepted';
    EXCEPTION WHEN OTHERS THEN IF SQLERRM<>'REQUEST_ALREADY_SAVED_REFRESH' THEN RAISE; END IF; END;
  IF (SELECT count(*) FROM public.shipments WHERE client_request_id=request)<>1 THEN RAISE EXCEPTION 'Duplicate request'; END IF;
  IF EXISTS(SELECT 1 FROM public.shipments WHERE client_request_id=request AND (quoted_amount IS NOT NULL OR confirmed_by IS NOT NULL)) THEN RAISE EXCEPTION 'Invented quotation/approval'; END IF;
  BEGIN PERFORM public.mobile_create_request(gen_random_uuid(),payload||'{"weight":0}'::jsonb); RAISE EXCEPTION 'Zero weight accepted';
    EXCEPTION WHEN OTHERS THEN IF SQLERRM<>'INVALID_WEIGHT' THEN RAISE; END IF; END;
  BEGIN PERFORM public.mobile_create_request(gen_random_uuid(),payload||'{"contact":"123"}'::jsonb); RAISE EXCEPTION 'Bad phone accepted';
    EXCEPTION WHEN OTHERS THEN IF SQLERRM<>'INVALID_CONTACT' THEN RAISE; END IF; END;
  BEGIN PERFORM public.mobile_driver_action(gen_random_uuid(),'start'); RAISE EXCEPTION 'Customer driver action allowed';
    EXCEPTION WHEN insufficient_privilege THEN NULL; END;
  PERFORM set_config('request.jwt.claims',jsonb_build_object('sub',current_setting('test.mobile_b'),'role','authenticated')::text,true);
  data:=public.mobile_workspace();
  IF jsonb_array_length(data->'shipments')<>0 THEN RAISE EXCEPTION 'Other customer sees request'; END IF;
  IF (SELECT full_name FROM public.users WHERE id=auth.uid())<>'Mobile rollback B' THEN RAISE EXCEPTION 'Other profile modified'; END IF;
END $$;
RESET ROLE;
SELECT set_config('request.jwt.claims','{}',true);
UPDATE public.users SET is_active=false WHERE id=current_setting('test.mobile_a')::uuid;
SELECT set_config('request.jwt.claims',jsonb_build_object('sub',current_setting('test.mobile_a'),'role','authenticated')::text,true);
SET LOCAL ROLE authenticated;
DO $$ BEGIN
  BEGIN PERFORM public.mobile_workspace(); RAISE EXCEPTION 'Inactive account allowed'; EXCEPTION WHEN insufficient_privilege THEN NULL; END;
  BEGIN PERFORM public.mobile_save_profile('Denied','+639123456789','',''); RAISE EXCEPTION 'Inactive write allowed'; EXCEPTION WHEN insufficient_privilege THEN NULL; END;
END $$;
RESET ROLE;
SET LOCAL ROLE anon;
DO $$ BEGIN
  BEGIN PERFORM public.mobile_workspace(); RAISE EXCEPTION 'Anonymous workspace allowed'; EXCEPTION WHEN insufficient_privilege THEN NULL; END;
END $$;
RESET ROLE;
ROLLBACK;
