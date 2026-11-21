-- Exact-ID detail reads retain table RLS and require an active account.
CREATE INDEX IF NOT EXISTS delivery_documents_storage_path_idx ON public.delivery_documents(storage_path);
CREATE OR REPLACE FUNCTION public.mobile_shipment_details(p_shipment uuid)
RETURNS jsonb LANGUAGE plpgsql SECURITY INVOKER SET search_path = '' AS $$
BEGIN
  IF auth.uid() IS NULL OR NOT EXISTS(SELECT 1 FROM public.users WHERE id=auth.uid() AND is_active) THEN
    RAISE EXCEPTION 'ACTIVE_ACCOUNT_REQUIRED' USING ERRCODE='42501';
  END IF;
  IF NOT EXISTS(SELECT 1 FROM public.shipments WHERE id=p_shipment) THEN
    RAISE EXCEPTION 'SHIPMENT_NOT_FOUND' USING ERRCODE='42501';
  END IF;
  RETURN jsonb_build_object(
    'shipment_id',p_shipment,
    'events',coalesce((SELECT jsonb_agg(e ORDER BY e.occurred_at) FROM
      (SELECT id,event_type,occurred_at FROM public.shipment_events WHERE shipment_id=p_shipment ORDER BY occurred_at DESC LIMIT 200)e),'[]'::jsonb),
    'history',coalesce((SELECT jsonb_agg(h ORDER BY h.created_at) FROM
      (SELECT id,from_status,to_status,note,created_at FROM public.shipment_status_history WHERE shipment_id=p_shipment ORDER BY created_at DESC LIMIT 200)h),'[]'::jsonb),
    'documents',coalesce((SELECT jsonb_agg(d ORDER BY d.created_at) FROM
      (SELECT id,kind,storage_path,file_name,mime_type,recipient_name,created_at FROM public.delivery_documents
       WHERE shipment_id=p_shipment AND kind::text IN ('proof_of_delivery','signature') ORDER BY created_at DESC LIMIT 50)d),'[]'::jsonb),
    'position',(SELECT to_jsonb(p) FROM
      (SELECT l.latitude,l.longitude,l.accuracy_m,l.recorded_at FROM public.tracking_logs l
       JOIN public.deliveries d ON d.id=l.delivery_id WHERE d.shipment_id=p_shipment ORDER BY l.recorded_at DESC LIMIT 1)p)
  );
END $$;
REVOKE ALL ON FUNCTION public.mobile_shipment_details(uuid) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.mobile_shipment_details(uuid) TO authenticated;

-- The object must be an actual recorded proof visible under delivery_documents
-- RLS. No public URLs and no general office access to unrelated bucket objects.
CREATE POLICY "active accounts read recorded authorized proof"
ON storage.objects FOR SELECT TO authenticated
USING (
  bucket_id='delivery-documents'
  AND EXISTS(SELECT 1 FROM public.users u WHERE u.id=(SELECT auth.uid()) AND u.is_active)
  AND EXISTS(SELECT 1 FROM public.delivery_documents d WHERE d.storage_path=storage.objects.name
    AND d.kind::text IN ('proof_of_delivery','signature'))
);
