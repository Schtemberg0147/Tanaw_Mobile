BEGIN;
DO $$ DECLARE a uuid:=gen_random_uuid(); b uuid:=gen_random_uuid(); s uuid; c uuid; doc uuid:=gen_random_uuid(); BEGIN
  PERFORM set_config('test.details_a',a::text,true);PERFORM set_config('test.details_b',b::text,true);
  INSERT INTO auth.users(id,email,raw_app_meta_data,raw_user_meta_data) VALUES
    (a,'mobile-rollback-'||a||'@example.invalid','{}','{"full_name":"Details rollback A"}'),
    (b,'mobile-rollback-'||b||'@example.invalid','{}','{"full_name":"Details rollback B"}');
  SELECT id INTO c FROM public.customers WHERE user_id=a;
  INSERT INTO public.shipments(customer_id,status,pickup_address,dropoff_address,cargo_description,weight_kg)
  VALUES(c,'pending','Rollback pickup','Rollback destination','Rollback cargo',1) RETURNING id INTO s;
  PERFORM set_config('test.details_shipment',s::text,true);
  PERFORM set_config('test.details_document',doc::text,true);
  INSERT INTO public.delivery_documents(id,shipment_id,kind,storage_path,mime_type,recipient_name,uploaded_by)
  VALUES(doc,s,'proof_of_delivery','rollback-only/'||doc||'.png','image/png','Rollback recipient',a);
  INSERT INTO storage.objects(bucket_id,name,owner,metadata)
  VALUES('delivery-documents','rollback-only/'||doc||'.png',a,'{"mimetype":"image/png","size":1}');
  PERFORM set_config('request.jwt.claims',jsonb_build_object('sub',a,'role','authenticated')::text,true);
END $$;
SET LOCAL ROLE authenticated;
DO $$ DECLARE data jsonb; BEGIN
  data:=public.mobile_shipment_details(current_setting('test.details_shipment')::uuid);
  IF jsonb_array_length(data->'documents')<>1 THEN RAISE EXCEPTION 'Own document missing'; END IF;
  IF jsonb_array_length(data->'history')=0 THEN RAISE EXCEPTION 'Status history missing'; END IF;
  IF (SELECT count(*) FROM storage.objects WHERE name='rollback-only/'||current_setting('test.details_document')||'.png')<>1 THEN RAISE EXCEPTION 'Authorized proof denied'; END IF;
  PERFORM set_config('request.jwt.claims',jsonb_build_object('sub',current_setting('test.details_b'),'role','authenticated')::text,true);
  BEGIN PERFORM public.mobile_shipment_details(current_setting('test.details_shipment')::uuid);RAISE EXCEPTION 'Other shipment visible';
    EXCEPTION WHEN insufficient_privilege THEN NULL; END;
  IF EXISTS(SELECT 1 FROM storage.objects WHERE name='rollback-only/'||current_setting('test.details_document')||'.png') THEN RAISE EXCEPTION 'Other proof visible'; END IF;
END $$;
RESET ROLE;
SELECT set_config('request.jwt.claims','{}',true);
UPDATE public.users SET is_active=false WHERE id=current_setting('test.details_a')::uuid;
SELECT set_config('request.jwt.claims',jsonb_build_object('sub',current_setting('test.details_a'),'role','authenticated')::text,true);
SET LOCAL ROLE authenticated;
DO $$ BEGIN
  BEGIN PERFORM public.mobile_shipment_details(current_setting('test.details_shipment')::uuid);RAISE EXCEPTION 'Inactive details allowed';
    EXCEPTION WHEN insufficient_privilege THEN NULL;END;
END $$;
RESET ROLE;
ROLLBACK;
