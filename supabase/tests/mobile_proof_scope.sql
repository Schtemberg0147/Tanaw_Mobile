BEGIN;
DO $$ DECLARE a uuid:=gen_random_uuid(); b uuid:=gen_random_uuid(); driver_user uuid:=gen_random_uuid(); office uuid:=gen_random_uuid();
  customer uuid; shipment uuid; driver uuid; truck uuid; delivery uuid; good_path text; wrong_folder text; wrong_owner text; BEGIN
  INSERT INTO auth.users(id,email,raw_app_meta_data,raw_user_meta_data) VALUES
    (a,'mobile-rollback-'||a||'@example.invalid','{}','{"full_name":"Proof customer A"}'),
    (b,'mobile-rollback-'||b||'@example.invalid','{}','{"full_name":"Proof customer B"}'),
    (driver_user,'mobile-rollback-'||driver_user||'@example.invalid','{"role":"driver"}','{"full_name":"Proof driver"}'),
    (office,'mobile-rollback-'||office||'@example.invalid','{"role":"admin"}','{"full_name":"Proof office"}');
  SELECT id INTO customer FROM public.customers WHERE user_id=a;
  INSERT INTO public.shipments(customer_id,status,pickup_address,dropoff_address) VALUES(customer,'pending','Rollback pickup','Rollback destination') RETURNING id INTO shipment;
  INSERT INTO public.drivers(user_id,full_name,license_number) VALUES(driver_user,'Rollback driver','ROLLBACK-'||driver_user) RETURNING id INTO driver;
  INSERT INTO public.trucks(plate_number) VALUES('RB-'||left(driver_user::text,8)) RETURNING id INTO truck;
  INSERT INTO public.deliveries(shipment_id,driver_id,truck_id) VALUES(shipment,driver,truck) RETURNING id INTO delivery;
  good_path:=delivery||'/pod-photo-rollback.png';wrong_folder:=gen_random_uuid()||'/pod-photo-rollback.png';wrong_owner:=delivery||'/pod-photo-wrong-owner.png';
  INSERT INTO public.delivery_documents(shipment_id,delivery_id,kind,storage_path,uploaded_by)
  VALUES(shipment,delivery,'proof_of_delivery',good_path,driver_user),
    (shipment,delivery,'proof_of_delivery',wrong_folder,driver_user),
    (shipment,delivery,'proof_of_delivery',wrong_owner,driver_user);
  INSERT INTO storage.objects(bucket_id,name,owner,metadata) VALUES
    ('delivery-documents',good_path,driver_user,'{"size":1,"mimetype":"image/png"}'),
    ('delivery-documents',wrong_folder,driver_user,'{"size":1,"mimetype":"image/png"}'),
    ('delivery-documents',wrong_owner,b,'{"size":1,"mimetype":"image/png"}');
  PERFORM set_config('test.proof_office',office::text,true);PERFORM set_config('test.proof_other',b::text,true);
  PERFORM set_config('test.proof_good',good_path,true);PERFORM set_config('test.proof_folder',wrong_folder,true);PERFORM set_config('test.proof_owner',wrong_owner,true);
  PERFORM set_config('request.jwt.claims',jsonb_build_object('sub',office,'role','authenticated')::text,true);
END $$;
SET LOCAL ROLE authenticated;
DO $$ BEGIN
  IF NOT EXISTS(SELECT 1 FROM storage.objects WHERE name=current_setting('test.proof_good')) THEN RAISE EXCEPTION 'Office recorded proof denied';END IF;
  IF EXISTS(SELECT 1 FROM storage.objects WHERE name IN(current_setting('test.proof_folder'),current_setting('test.proof_owner'))) THEN RAISE EXCEPTION 'Forged proof reference authorized';END IF;
  PERFORM set_config('request.jwt.claims',jsonb_build_object('sub',current_setting('test.proof_other'),'role','authenticated')::text,true);
  IF EXISTS(SELECT 1 FROM storage.objects WHERE name=current_setting('test.proof_good')) THEN RAISE EXCEPTION 'Other customer proof leak';END IF;
END $$;
RESET ROLE;
SELECT set_config('request.jwt.claims','{}',true);
UPDATE public.users SET is_active=false WHERE id=current_setting('test.proof_office')::uuid;
SELECT set_config('request.jwt.claims',jsonb_build_object('sub',current_setting('test.proof_office'),'role','authenticated')::text,true);
SET LOCAL ROLE authenticated;
DO $$ BEGIN IF EXISTS(SELECT 1 FROM storage.objects WHERE name=current_setting('test.proof_good')) THEN RAISE EXCEPTION 'Inactive office proof leak';END IF;END $$;
RESET ROLE;
ROLLBACK;
